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
    private val gravityTrail = FloatArray(PIXEL_COUNT)
    private val particles = mutableListOf<Particle>()
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
            EffectType.FIRE -> renderFire(config, sensorX)
            EffectType.GRAVITY -> renderGravity(config, dt, sensorX, sensorY)
            EffectType.PLASMA -> renderPlasma(config, time, sensorX, sensorY)
            EffectType.STARFIELD -> renderStarfield(config, dt, sensorX, sensorY)
        }

        val brightness = config.brightness.coerceIn(0.05f, 1f)
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
        val orbit = time * config.speed * 1.8

        // The object stays fixed behind the glass. Tilt and automatic motion orbit
        // the camera around it, producing view-dependent perspective/parallax
        // instead of rotations in the object's local coordinate system.
        val cameraYaw = 0.55 + orbit + tiltX * 0.82
        val cameraPitch = (-0.32 + sin(orbit * 0.63) * 0.16 - tiltY * 0.68)
            .coerceIn(-1.05, 1.05)
        val cameraDistance = 5.1
        val camera = V3(
            x = sin(cameraYaw) * cos(cameraPitch) * cameraDistance,
            y = sin(cameraPitch) * cameraDistance,
            z = cos(cameraYaw) * cos(cameraPitch) * cameraDistance
        )
        val forward = (V3.ZERO - camera).normalized()
        val right = forward.cross(V3.DOWN).normalized()
        val down = right.cross(forward).normalized()

        val points = solid.vertices.map { vertex ->
            val fromCamera = vertex - camera
            val viewX = fromCamera.dot(right)
            val viewY = fromCamera.dot(down)
            val viewDepth = fromCamera.dot(forward).coerceAtLeast(2.4)
            val perspective = 4.25 / viewDepth
            Point2(
                x = CENTER + viewX * perspective * 3.15,
                y = CENTER + viewY * perspective * 3.15,
                depth = viewDepth
            )
        }

        solid.edges.sortedByDescending { (a, b) -> (points[a].depth + points[b].depth) / 2.0 }
            .forEach { (a, b) ->
                val depth = (points[a].depth + points[b].depth) / 2.0
                val level = (276 - depth * 12).roundToInt().coerceIn(205, 255)
                drawLine(frame, points[a], points[b], level)
            }

        if (config.showVertices) {
            points.forEach { point ->
                put(frame, point.x.roundToInt(), point.y.roundToInt(), 255)
            }
        }
        return frame
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
        val targetCount = config.particleCount.coerceIn(8, 56)
        val center = CENTER.toFloat()
        while (particles.size < targetCount) {
            particles += Particle(
                x = center + (random.nextFloat() - 0.5f) * 5f,
                y = center + (random.nextFloat() - 0.5f) * 5f,
                vx = (random.nextFloat() - 0.5f) * 2f,
                vy = (random.nextFloat() - 0.5f) * 2f
            )
        }
        while (particles.size > targetCount) particles.removeLast()

        val fade = (0.43f + config.trail * 0.48f).coerceIn(0.4f, 0.94f)
        gravityTrail.indices.forEach { gravityTrail[it] *= fade }

        val gx = if (config.accelerometer) tiltX * 14f else sin(lastNanos / 2.8e9).toFloat() * 1.8f
        // Matrix rows grow downwards; Android's positive Y points toward the
        // physical top of a portrait phone and must therefore accelerate down.
        val gy = if (config.accelerometer) tiltY * 14f else 7.5f
        val bounce = 0.66f + config.intensity * 0.22f
        val drag = 0.986f - config.speed * 0.018f

        particles.forEach { p ->
            p.vx = (p.vx + gx * dt) * drag
            p.vy = (p.vy + gy * dt) * drag
            p.x += p.vx * dt * (0.6f + config.speed * 1.6f)
            p.y += p.vy * dt * (0.6f + config.speed * 1.6f)

            val dx = p.x - center
            val dy = p.y - center
            val distance = sqrt(dx * dx + dy * dy)
            if (distance > MATRIX_RADIUS - 0.35f) {
                val nx = dx / distance.coerceAtLeast(0.001f)
                val ny = dy / distance.coerceAtLeast(0.001f)
                p.x = center + nx * (MATRIX_RADIUS - 0.4f)
                p.y = center + ny * (MATRIX_RADIUS - 0.4f)
                val normalVelocity = p.vx * nx + p.vy * ny
                if (normalVelocity > 0f) {
                    p.vx -= (1f + bounce) * normalVelocity * nx
                    p.vy -= (1f + bounce) * normalVelocity * ny
                }
            }

            val px = p.x.roundToInt().coerceIn(0, SIZE - 1)
            val py = p.y.roundToInt().coerceIn(0, SIZE - 1)
            gravityTrail[index(px, py)] = 255f
        }
        return IntArray(PIXEL_COUNT) { gravityTrail[it].roundToInt().coerceIn(0, 255) }
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
    private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float)
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
