package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.data.EffectType
import com.abdulkus.glyphlab.data.MatrixConfig
import com.abdulkus.glyphlab.data.SolidType
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class MatrixEngine(seed: Long = System.nanoTime()) {
    private val random = Random(seed)
    private val fireHeat = FloatArray(PIXEL_COUNT)
    private val sand = mutableListOf<Cell>()
    private var sandStepAccumulator = 0f
    private val stars = MutableList(34) { newStar() }
    private var lastNanos = 0L

    fun render(
        config: MatrixConfig,
        timeNanos: Long,
        tiltX: Float = 0f,
        tiltY: Float = 0f
    ): IntArray {
        val time = timeNanos / 1_000_000_000.0
        val dt = if (lastNanos == 0L) 1f / config.frameRate else {
            ((timeNanos - lastNanos) / 1_000_000_000f).coerceIn(1f / 120f, 0.1f)
        }
        lastNanos = timeNanos

        // The LEDs are observed from the back of the phone, so their horizontal
        // direction is mirrored relative to Android's screen coordinate system.
        val sensorX = if (config.accelerometer) -tiltX * config.sensorStrength else 0f
        val sensorY = if (config.accelerometer) tiltY * config.sensorStrength else 0f

        val raw = when (config.effect) {
            EffectType.WIREFRAME -> renderWireframe(config, time, sensorX, sensorY)
            // Fire is perceived from the rear glass and its wind direction needs
            // the opposite horizontal sign from scene/camera movement.
            EffectType.FIRE -> renderFire(config, -sensorX)
            EffectType.GRAVITY -> renderGravity(config, dt, sensorX, sensorY)
            EffectType.PLASMA -> renderPlasma(config, time, sensorX, sensorY)
            EffectType.STARFIELD -> renderStarfield(config, dt, sensorX, sensorY)
        }

        val brightness = config.brightness.coerceIn(0f, 1f)
        for (i in raw.indices) {
            raw[i] = if (isInsideMatrix(i % SIZE, i / SIZE)) {
                (raw[i] * brightness).roundToInt().coerceIn(0, 255)
            } else {
                0
            }
        }
        return raw
    }

    private fun renderWireframe(
        config: MatrixConfig,
        time: Double,
        tiltX: Float,
        tiltY: Float
    ): IntArray {
        val frame = IntArray(PIXEL_COUNT)
        val solid = solid(config.solid)
        val auto = time * config.speed * 1.75
        val cameraX = -0.48 - tiltY * 0.82 + if (config.autoRotateX) auto * 0.73 else 0.0
        val cameraY = 0.62 + tiltX * 0.82 + if (config.autoRotateY) auto else 0.0
        val cameraZ = if (config.autoRotateZ) auto * 0.61 else 0.0

        val points = solid.vertices.map { vertex ->
            // Inverse camera transform: the shape stays in world space while the
            // selected camera axes move around it. Full edges retain the bold,
            // expressive wireframe of the earlier version.
            val viewed = cameraView(vertex, cameraX, cameraY, cameraZ)
            val perspective = (4.2 / (4.8 - viewed.z)).coerceIn(0.72, 1.34)
            Point2(
                x = CENTER + viewed.x * perspective * 2.85,
                y = CENTER + viewed.y * perspective * 2.85,
                depth = viewed.z
            )
        }

        solid.edges
            .sortedBy { (a, b) -> (points[a].depth + points[b].depth) / 2.0 }
            .forEach { (a, b) ->
                val depth = (points[a].depth + points[b].depth) / 2.0
                val near = ((depth + 1.8) / 3.6).coerceIn(0.0, 1.0)
                val level = (48 + near * 196).roundToInt()
                drawLine(frame, points[a], points[b], level)
            }

        if (config.showVertices) {
            points.forEach { point ->
                put(frame, point.x.roundToInt(), point.y.roundToInt(), 255)
            }
        }
        return frame
    }

    private fun cameraView(v: V3, ax: Double, ay: Double, az: Double): V3 {
        val x1 = v.x * cos(-az) - v.y * sin(-az)
        val y1 = v.x * sin(-az) + v.y * cos(-az)
        val z1 = v.z
        val x2 = x1 * cos(-ay) + z1 * sin(-ay)
        val y2 = y1
        val z2 = -x1 * sin(-ay) + z1 * cos(-ay)
        return V3(
            x2,
            y2 * cos(-ax) - z2 * sin(-ax),
            y2 * sin(-ax) + z2 * cos(-ax)
        )
    }

    private fun renderFire(config: MatrixConfig, tiltX: Float): IntArray {
        val fuel = (0.48f + config.intensity * 0.5f).coerceAtMost(0.98f)
        for (x in 1 until SIZE - 1) {
            val edgeFade = 1f - abs(x - CENTER.toFloat()) / 7f
            fireHeat[index(x, SIZE - 1)] = if (random.nextFloat() < fuel * edgeFade) {
                190f + random.nextFloat() * 65f
            } else {
                random.nextFloat() * 65f
            }
        }

        val next = fireHeat.copyOf()
        // A small per-row drift bends the flame without pushing the whole body
        // against the circular edge. Vertical lift never depends on phone tilt.
        val wind = (tiltX * 0.68f).coerceIn(-0.68f, 0.68f)
        for (y in 0 until SIZE - 1) {
            for (x in 0 until SIZE) {
                val sourceX = (x - wind + (random.nextFloat() - 0.5f) * 0.8f)
                    .roundToInt().coerceIn(0, SIZE - 1)
                val below = fireHeat[index(sourceX, y + 1)]
                val left = fireHeat[index((sourceX - 1).coerceAtLeast(0), y + 1)]
                val right = fireHeat[index((sourceX + 1).coerceAtMost(SIZE - 1), y + 1)]
                val twoBelow = fireHeat[index(sourceX, (y + 2).coerceAtMost(SIZE - 1))]
                val cooling = 8f + (1f - config.intensity) * 22f + random.nextFloat() * 16f
                val lift = 0.55f + config.speed * 0.35f
                next[index(x, y)] =
                    ((below * lift + left * 0.14f + right * 0.14f + twoBelow * 0.12f) - cooling)
                        .coerceIn(0f, 255f)
            }
        }
        next.copyInto(fireHeat)

        return IntArray(PIXEL_COUNT) { i ->
            val heat = fireHeat[i]
            when {
                heat < 28f -> 0
                heat < 95f -> ((heat - 28f) * 1.45f).roundToInt()
                else -> (98f + (heat - 95f) * 0.93f).roundToInt().coerceAtMost(255)
            }
        }
    }

    private fun renderGravity(
        config: MatrixConfig,
        dt: Float,
        tiltX: Float,
        tiltY: Float
    ): IntArray {
        resizeSand(config.particleCount.coerceIn(8, 56))

        val gx = if (config.accelerometer) tiltX else sin(lastNanos / 2.8e9).toFloat() * 0.24f
        // Verified on the rear-facing Phone (4a) Pro matrix: the physical
        // vertical direction is the opposite of the normalized sensor Y.
        val gy = if (config.accelerometer) -tiltY else 1f
        val magnitude = sqrt(gx * gx + gy * gy)
        if (magnitude > 0.08f) {
            sandStepAccumulator += dt * (4f + config.speed * 15f) * magnitude.coerceAtLeast(0.35f)
            repeat(sandStepAccumulator.toInt().coerceAtMost(3)) {
                stepSand(gx, gy, config.trail)
                sandStepAccumulator -= 1f
            }
        } else {
            sandStepAccumulator = 0f
        }

        return IntArray(PIXEL_COUNT).also { frame ->
            sand.forEach { grain -> frame[index(grain.x, grain.y)] = 255 }
        }
    }

    private fun resizeSand(targetCount: Int) {
        while (sand.size > targetCount) sand.removeLast()
        val occupied = sand.mapTo(mutableSetOf()) { index(it.x, it.y) }
        var attempts = 0
        while (sand.size < targetCount && attempts++ < 800) {
            val x = (CENTER + (random.nextFloat() - 0.5f) * 8f).roundToInt()
            val y = (CENTER + (random.nextFloat() - 0.5f) * 8f).roundToInt()
            if (x in 0 until SIZE && y in 0 until SIZE && isInsideMatrix(x, y)) {
                val cellIndex = index(x, y)
                if (occupied.add(cellIndex)) sand += Cell(x, y)
            }
        }
    }

    private fun stepSand(gravityX: Float, gravityY: Float, looseness: Float) {
        val horizontalIsDominant = abs(gravityX) > abs(gravityY)
        val primaryX = if (horizontalIsDominant) gravityX.signInt() else 0
        val primaryY = if (horizontalIsDominant) 0 else gravityY.signInt()
        if (primaryX == 0 && primaryY == 0) return

        val secondarySign = if (horizontalIsDominant) gravityY.signInt() else gravityX.signInt()
        val alternateSign = if (secondarySign == 0 && random.nextBoolean()) 1 else -1
        val sideA = if (secondarySign == 0) alternateSign else secondarySign
        val sideB = -sideA
        val candidates = if (horizontalIsDominant) {
            listOf(primaryX to 0, primaryX to sideA, primaryX to sideB)
        } else {
            listOf(0 to primaryY, sideA to primaryY, sideB to primaryY)
        }

        val occupied = BooleanArray(PIXEL_COUNT)
        sand.forEach { occupied[index(it.x, it.y)] = true }
        val order = sand.indices.sortedByDescending { i ->
            sand[i].x * gravityX + sand[i].y * gravityY
        }

        order.forEach { grainIndex ->
            val grain = sand[grainIndex]
            occupied[index(grain.x, grain.y)] = false
            val diagonalAllowed = random.nextFloat() < (0.35f + looseness * 0.65f)
            val move = candidates.firstOrNull { (dx, dy) ->
                if ((dx != primaryX || dy != primaryY) && !diagonalAllowed) return@firstOrNull false
                val x = grain.x + dx
                val y = grain.y + dy
                x in 0 until SIZE && y in 0 until SIZE &&
                    isInsideMatrix(x, y) && !occupied[index(x, y)]
            }
            if (move != null) {
                grain.x += move.first
                grain.y += move.second
            }
            occupied[index(grain.x, grain.y)] = true
        }
    }

    private fun Float.signInt(): Int = when {
        this > 0.04f -> 1
        this < -0.04f -> -1
        else -> 0
    }

    private fun renderPlasma(
        config: MatrixConfig,
        time: Double,
        tiltX: Float,
        tiltY: Float
    ): IntArray {
        val frame = IntArray(PIXEL_COUNT)
        val phase = time * (0.6 + config.speed * 3.7)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val px = x - CENTER + tiltX * 3.4
                val py = y - CENTER - tiltY * 3.4
                val radial = sqrt(px * px + py * py)
                val wave = sin(px * 0.86 + phase) +
                    sin(py * 0.72 - phase * 1.17) +
                    sin(radial * 1.28 - phase * 1.55)
                val normalized = ((wave + 3.0) / 6.0).toFloat()
                val threshold = 0.28f + (1f - config.intensity) * 0.36f
                frame[index(x, y)] = if (normalized > threshold) {
                    (45 + normalized * 210).roundToInt().coerceAtMost(255)
                } else 0
            }
        }
        return frame
    }

    private fun renderStarfield(
        config: MatrixConfig,
        dt: Float,
        tiltX: Float,
        tiltY: Float
    ): IntArray {
        val frame = IntArray(PIXEL_COUNT)
        val centerX = CENTER + tiltX * 2.6f
        val centerY = CENTER - tiltY * 2.6f
        val velocity = 0.45f + config.speed * 2.8f

        stars.forEachIndexed { i, star ->
            star.z -= dt * velocity
            if (star.z < 0.16f) stars[i] = newStar().also { it.z = 1f }
            val active = stars[i]
            val scale = 1f / active.z
            val sx = centerX + active.x * scale * 3.7f
            val sy = centerY + active.y * scale * 3.7f
            val brightness = ((1f - active.z) * 220f + 35f).roundToInt()
            put(frame, sx.roundToInt(), sy.roundToInt(), brightness)

            if (config.trail > 0.35f && active.z < 0.6f) {
                val previousScale = 1f / (active.z + 0.12f)
                drawLine(
                    frame,
                    Point2(
                        (centerX + active.x * previousScale * 3.7f).toDouble(),
                        (centerY + active.y * previousScale * 3.7f).toDouble(),
                        0.0
                    ),
                    Point2(sx.toDouble(), sy.toDouble(), 0.0),
                    (brightness * config.trail).roundToInt()
                )
            }
        }
        return frame
    }

    private fun solid(type: SolidType): Solid = when (type) {
        SolidType.CUBE -> Solid(
            vertices = listOf(
                V3(-1.0, -1.0, -1.0), V3(1.0, -1.0, -1.0),
                V3(1.0, 1.0, -1.0), V3(-1.0, 1.0, -1.0),
                V3(-1.0, -1.0, 1.0), V3(1.0, -1.0, 1.0),
                V3(1.0, 1.0, 1.0), V3(-1.0, 1.0, 1.0)
            ),
            edges = listOf(
                0 to 1, 1 to 2, 2 to 3, 3 to 0,
                4 to 5, 5 to 6, 6 to 7, 7 to 4,
                0 to 4, 1 to 5, 2 to 6, 3 to 7
            )
        )

        SolidType.TETRAHEDRON -> Solid(
            vertices = listOf(
                V3(1.0, 1.0, 1.0), V3(-1.0, -1.0, 1.0),
                V3(-1.0, 1.0, -1.0), V3(1.0, -1.0, -1.0)
            ),
            edges = listOf(0 to 1, 0 to 2, 0 to 3, 1 to 2, 1 to 3, 2 to 3)
        )

        SolidType.OCTAHEDRON -> Solid(
            vertices = listOf(
                V3(1.25, 0.0, 0.0), V3(-1.25, 0.0, 0.0),
                V3(0.0, 1.25, 0.0), V3(0.0, -1.25, 0.0),
                V3(0.0, 0.0, 1.25), V3(0.0, 0.0, -1.25)
            ),
            edges = listOf(
                0 to 2, 0 to 3, 0 to 4, 0 to 5,
                1 to 2, 1 to 3, 1 to 4, 1 to 5,
                2 to 4, 2 to 5, 3 to 4, 3 to 5
            )
        )

        SolidType.PYRAMID -> Solid(
            vertices = listOf(
                V3(-1.1, 0.9, -1.1), V3(1.1, 0.9, -1.1),
                V3(1.1, 0.9, 1.1), V3(-1.1, 0.9, 1.1),
                V3(0.0, -1.35, 0.0)
            ),
            edges = listOf(
                0 to 1, 1 to 2, 2 to 3, 3 to 0,
                0 to 4, 1 to 4, 2 to 4, 3 to 4
            )
        )
    }

    private fun drawLine(frame: IntArray, from: Point2, to: Point2, level: Int) {
        var x0 = from.x.roundToInt()
        var y0 = from.y.roundToInt()
        val x1 = to.x.roundToInt()
        val y1 = to.y.roundToInt()
        val dx = abs(x1 - x0)
        val sx = if (x0 < x1) 1 else -1
        val dy = -abs(y1 - y0)
        val sy = if (y0 < y1) 1 else -1
        var error = dx + dy
        while (true) {
            put(frame, x0, y0, level)
            if (x0 == x1 && y0 == y1) break
            val twice = 2 * error
            if (twice >= dy) {
                error += dy
                x0 += sx
            }
            if (twice <= dx) {
                error += dx
                y0 += sy
            }
        }
    }

    private fun put(frame: IntArray, x: Int, y: Int, level: Int) {
        if (x in 0 until SIZE && y in 0 until SIZE && isInsideMatrix(x, y)) {
            frame[index(x, y)] = max(frame[index(x, y)], level.coerceIn(0, 255))
        }
    }

    private fun newStar() = Star(
        x = (random.nextFloat() - 0.5f) * 2.7f,
        y = (random.nextFloat() - 0.5f) * 2.7f,
        z = 0.25f + random.nextFloat() * 0.75f
    )

    private data class V3(val x: Double, val y: Double, val z: Double) {
        operator fun minus(other: V3) = V3(x - other.x, y - other.y, z - other.z)

        fun dot(other: V3): Double = x * other.x + y * other.y + z * other.z

        fun cross(other: V3) = V3(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        )

        fun normalized(): V3 {
            val length = sqrt(x * x + y * y + z * z).coerceAtLeast(0.0001)
            return V3(x / length, y / length, z / length)
        }

        companion object {
            val ZERO = V3(0.0, 0.0, 0.0)
            val DOWN = V3(0.0, 1.0, 0.0)
        }
    }
    private data class Point2(val x: Double, val y: Double, val depth: Double)
    private data class Solid(val vertices: List<V3>, val edges: List<Pair<Int, Int>>)
    private data class Cell(var x: Int, var y: Int)
    private data class Star(var x: Float, var y: Float, var z: Float)

    companion object {
        const val SIZE = 13
        const val PIXEL_COUNT = SIZE * SIZE
        const val CENTER = 6.0
        const val MATRIX_RADIUS = 6.35f

        fun isInsideMatrix(x: Int, y: Int): Boolean {
            val dx = x - CENTER
            val dy = y - CENTER
            return dx * dx + dy * dy <= MATRIX_RADIUS * MATRIX_RADIUS
        }

        private fun index(x: Int, y: Int): Int = y * SIZE + x
    }
}
