package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.data.MatrixConfig
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal class LiquidRenderer {
    private val fluid = LiquidSimulation()
    private var downX = 0f
    private var downY = 1f
    private var pendingSeconds = 0f
    private val pixels = (0 until MatrixEngine.PIXEL_COUNT).filter {
        MatrixEngine.isInsideMatrix(it % MatrixEngine.SIZE, it / MatrixEngine.SIZE)
    }

    fun render(config: MatrixConfig, dt: Float, gx: Float, gy: Float): IntArray {
        val followsTilt = config.accelerometer && config.sensorStrength > 0f
        val gravity = hypot(gx, gy)
        if (!followsTilt) {
            downX = 0f
            downY = 1f
        } else if (gravity > 0.055f * config.sensorStrength) {
            downX = gx / gravity
            downY = gy / gravity
        }

        val fill = 0.15f + config.liquidFill.coerceIn(0f, 1f) * 0.70f
        fluid.setFill(fill, downX, downY)
        val viscosity = config.liquidViscosity.coerceIn(0f, 1f)
        val accelerationX = if (followsTilt) gx * 38f else 0f
        val accelerationY = if (followsTilt) gy * 38f else 38f
        pendingSeconds += dt
        while (pendingSeconds >= STEP_SECONDS) {
            fluid.advance(STEP_SECONDS, accelerationX, accelerationY, viscosity)
            pendingSeconds -= STEP_SECONDS
        }

        val center = (MatrixEngine.SIZE - 1) / 2f
        return IntArray(MatrixEngine.PIXEL_COUNT).also { frame ->
            for (pixel in pixels) {
                val x = pixel % MatrixEngine.SIZE - center
                val y = pixel / MatrixEngine.SIZE - center
                val density = fluid.sample(x, y)
                val coverage = smoothStep(((density - 0.04f) / 0.62f).coerceIn(0f, 1f))
                if (coverage == 0f) continue
                val atWall = x * x + y * y >= 5.8f * 5.8f
                // Sample toward the air so the meniscus stays legible on the sparse LED grid.
                val surface = if (atWall) 0f else {
                    val above = fluid.sample(x - downX * 0.68f, y - downY * 0.68f)
                    ((density - above) * 1.9f).coerceIn(0f, 1f)
                }
                frame[pixel] = (coverage * (143f + 107f * surface)).roundToInt()
            }
        }
    }

    private fun smoothStep(value: Float) = value * value * (3f - 2f * value)

    private companion object {
        const val STEP_SECONDS = 1f / 120f
    }
}

/** Position-based density constraints keep the particles nearly incompressible. */
private class LiquidSimulation {
    private data class Point(val x: Float, val y: Float)

    private val vessel = buildList {
        for (row in -12..12) {
            for (column in -12..12) {
                val x = (column + if (row % 2 == 0) 0f else 0.5f) * SPACING
                val y = row * SPACING * 0.8660254f
                if (x * x + y * y < RADIUS_SQUARED) add(Point(x, y))
            }
        }
    }
    private val x = FloatArray(vessel.size)
    private val y = FloatArray(vessel.size)
    private val nextX = FloatArray(vessel.size)
    private val nextY = FloatArray(vessel.size)
    private val velocityX = FloatArray(vessel.size)
    private val velocityY = FloatArray(vessel.size)
    private val deltaX = FloatArray(vessel.size)
    private val deltaY = FloatArray(vessel.size)
    private val pressure = FloatArray(vessel.size)
    private var count = 0

    private val cells = IntArray(GRID_SIZE * GRID_SIZE)
    private val nextInCell = IntArray(vessel.size)
    private val neighbors = IntArray(vessel.size * MAX_NEIGHBORS)
    private val neighborCounts = IntArray(vessel.size)

    fun setFill(fill: Float, downX: Float, downY: Float) {
        val target = (vessel.size * fill).roundToInt()
        if (target == count) return

        // Fill changes reset the volume at the current low side of the vessel.
        val startingPoints = vessel.sortedByDescending { it.x * downX + it.y * downY }
        count = target
        for (i in 0 until count) {
            val point = startingPoints[i]
            x[i] = point.x
            y[i] = point.y
            nextX[i] = point.x
            nextY[i] = point.y
            velocityX[i] = 0f
            velocityY[i] = 0f
        }
        indexParticles()
    }

    fun advance(dt: Float, ax: Float, ay: Float, viscosity: Float) {
        for (i in 0 until count) {
            nextX[i] = x[i] + (velocityX[i] + ax * dt) * dt
            nextY[i] = y[i] + (velocityY[i] + ay * dt) * dt
            confine(i)
        }
        indexParticles()
        collectNeighbors()
        repeat(3) {
            solveDensity()
            solveDisplacements()
            for (i in 0 until count) {
                nextX[i] += deltaX[i]
                nextY[i] += deltaY[i]
                confine(i)
            }
        }

        for (i in 0 until count) {
            velocityX[i] = (nextX[i] - x[i]) / dt
            velocityY[i] = (nextY[i] - y[i]) / dt
            x[i] = nextX[i]
            y[i] = nextY[i]
        }
        applyViscosity(viscosity)
        indexParticles()
    }

    fun sample(x: Float, y: Float): Float {
        val cellX = cell(x)
        val cellY = cell(y)
        var density = 0f
        for (row in cellY - 1..cellY + 1) {
            for (column in cellX - 1..cellX + 1) {
                var i = cells[row * GRID_SIZE + column]
                while (i >= 0) {
                    val dx = x - nextX[i]
                    val dy = y - nextY[i]
                    density += kernel(dx * dx + dy * dy)
                    i = nextInCell[i]
                }
            }
        }
        return density / REST_DENSITY
    }

    private fun confine(i: Int) {
        val distanceSquared = nextX[i] * nextX[i] + nextY[i] * nextY[i]
        if (distanceSquared <= RADIUS_SQUARED) return
        val scale = RADIUS / sqrt(distanceSquared)
        nextX[i] *= scale
        nextY[i] *= scale
    }

    private fun indexParticles() {
        cells.fill(-1)
        for (i in 0 until count) {
            val slot = cell(nextY[i]) * GRID_SIZE + cell(nextX[i])
            nextInCell[i] = cells[slot]
            cells[slot] = i
        }
    }

    private fun collectNeighbors() {
        for (i in 0 until count) {
            val cx = cell(nextX[i])
            val cy = cell(nextY[i])
            val offset = i * MAX_NEIGHBORS
            var size = 0
            for (row in cy - 1..cy + 1) {
                for (column in cx - 1..cx + 1) {
                    var j = cells[row * GRID_SIZE + column]
                    while (j >= 0) {
                        val dx = nextX[i] - nextX[j]
                        val dy = nextY[i] - nextY[j]
                        if (i != j && size < MAX_NEIGHBORS && dx * dx + dy * dy < NEIGHBOR_RADIUS_SQUARED) {
                            neighbors[offset + size] = j
                            size++
                        }
                        j = nextInCell[j]
                    }
                }
            }
            neighborCounts[i] = size
        }
    }

    private fun solveDensity() {
        for (i in 0 until count) {
            var density = 1f
            var sumSquared = 0f
            var gradientX = 0f
            var gradientY = 0f
            val offset = i * MAX_NEIGHBORS
            for (n in 0 until neighborCounts[i]) {
                val j = neighbors[offset + n]
                val dx = nextX[i] - nextX[j]
                val dy = nextY[i] - nextY[j]
                val q = kernelBase(dx * dx + dy * dy)
                density += q * q * q
                val gradient = GRADIENT_SCALE * q * q
                val px = dx * gradient
                val py = dy * gradient
                gradientX += px
                gradientY += py
                sumSquared += px * px + py * py
            }
            val compression = (density / REST_DENSITY - 1f).coerceAtLeast(0f)
            pressure[i] = -compression / (sumSquared + gradientX * gradientX + gradientY * gradientY + 0.16f)
        }
    }

    private fun solveDisplacements() {
        for (i in 0 until count) {
            var px = 0f
            var py = 0f
            val offset = i * MAX_NEIGHBORS
            for (n in 0 until neighborCounts[i]) {
                val j = neighbors[offset + n]
                val dx = nextX[i] - nextX[j]
                val dy = nextY[i] - nextY[j]
                val q = kernelBase(dx * dx + dy * dy)
                val weight = q * q * q
                // Cohesion joins nearby droplets, while the short-range term prevents clumps.
                val cohesion = 0.021f * weight - 0.085f * weight * weight * weight
                val gradient = (pressure[i] + pressure[j] + cohesion) * GRADIENT_SCALE * q * q
                px += dx * gradient
                py += dy * gradient
            }
            deltaX[i] = px.coerceIn(-0.22f, 0.22f)
            deltaY[i] = py.coerceIn(-0.22f, 0.22f)
        }
    }

    private fun applyViscosity(viscosity: Float) {
        val mix = 0.035f + 0.58f * viscosity * viscosity
        val damping = 0.998f - 0.009f * viscosity
        for (i in 0 until count) {
            var vx = 0f
            var vy = 0f
            var total = 1f
            val offset = i * MAX_NEIGHBORS
            for (n in 0 until neighborCounts[i]) {
                val j = neighbors[offset + n]
                val dx = x[i] - x[j]
                val dy = y[i] - y[j]
                val weight = kernel(dx * dx + dy * dy)
                vx += weight * (velocityX[j] - velocityX[i])
                vy += weight * (velocityY[j] - velocityY[i])
                total += weight
            }
            deltaX[i] = vx * mix / total
            deltaY[i] = vy * mix / total
        }
        for (i in 0 until count) {
            velocityX[i] = ((velocityX[i] + deltaX[i]) * damping).coerceIn(-28f, 28f)
            velocityY[i] = ((velocityY[i] + deltaY[i]) * damping).coerceIn(-28f, 28f)
        }
    }

    private fun kernel(distanceSquared: Float): Float {
        val q = kernelBase(distanceSquared)
        return q * q * q
    }

    private fun kernelBase(distanceSquared: Float) =
        (1f - distanceSquared / KERNEL_RADIUS_SQUARED).coerceAtLeast(0f)

    private fun cell(value: Float) = ((value + RADIUS) / CELL_SIZE).toInt() + 1

    private companion object {
        const val RADIUS = 6.25f
        const val RADIUS_SQUARED = RADIUS * RADIUS
        const val SPACING = 0.65f
        const val KERNEL_RADIUS = 1.22f
        const val KERNEL_RADIUS_SQUARED = KERNEL_RADIUS * KERNEL_RADIUS
        const val NEIGHBOR_RADIUS = 1.48f
        const val NEIGHBOR_RADIUS_SQUARED = NEIGHBOR_RADIUS * NEIGHBOR_RADIUS
        const val CELL_SIZE = NEIGHBOR_RADIUS
        const val GRID_SIZE = 11
        const val MAX_NEIGHBORS = 32
        // The kernel sum for triangular packing, with headroom for the fluid under load.
        const val REST_DENSITY = 3.32f
        const val GRADIENT_SCALE = -6f / (KERNEL_RADIUS_SQUARED * REST_DENSITY)
    }
}
