package com.luckyagent.android.ui.graph

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class GraphForces(
    val center: Float = .05f,
    val repulsion: Float = 1000f,
    val linkStrength: Float = .6f,
    val linkDistance: Float = 160f,
) {
    init {
        require(center in 0f..1f && repulsion in 0f..10000f)
        require(linkStrength in 0f..1f && linkDistance in 30f..500f)
    }
}

data class GraphPin(val id: String, val point: GraphPoint)

/**
 * Independently implemented force-directed layout: centering, degree-biased springs,
 * Barnes–Hut repulsion, collision separation, velocity damping and temperature cooling.
 * The caller owns this mutable simulation on one worker coroutine. Frames are immutable
 * snapshots; no Android, Compose, Obsidian code or WASM dependency is required.
 */
class ForceSimulation(
    graph: GraphTopology,
    initial: Map<String, GraphPoint> = emptyMap(),
    private val forces: GraphForces = GraphForces(),
) {
    private class Body(val id: String, var x: Float, var y: Float, val radius: Float) {
        var vx = 0f
        var vy = 0f
    }

    private val bodies = graph.nodeIds.mapIndexed { index, id ->
        val seed = initial[id]?.takeIf { it.isFinite }
        val radius = 28f * sqrt(index + .5f)
        Body(
            id,
            seed?.x ?: (cos(index * 2.399963).toFloat() * radius),
            seed?.y ?: (sin(index * 2.399963).toFloat() * radius),
            nodeRadius(graph.neighbors.getValue(id).size),
        )
    }
    private val indices = bodies.mapIndexed { index, body -> body.id to index }.toMap()
    private val links = graph.links.map { indices.getValue(it.source) to indices.getValue(it.target) }
    private val degrees = IntArray(bodies.size).also { counts ->
        links.forEach { (a, b) -> counts[a]++; counts[b]++ }
    }
    var temperature = 1f
        private set
    val active: Boolean get() = bodies.isNotEmpty() && temperature > .001f

    fun snapshot(): Map<String, GraphPoint> = bodies.associate { it.id to GraphPoint(it.x, it.y) }

    /** One 60 Hz physics tick. A dragged node is fixed; other nodes can respond to it. */
    fun tick(pin: GraphPin? = null) {
        if (bodies.isEmpty()) return
        val validPin = pin?.takeIf { it.point.isFinite && it.id in indices }
        // A cooled layout with no drag is done. Further frames must not rebuild the tree.
        if (validPin == null && temperature <= STOP_TEMPERATURE) return
        val pinned = validPin?.let { indices[it.id] }
        if (pinned != null) {
            bodies[pinned].apply { x = validPin.point.x; y = validPin.point.y; vx = 0f; vy = 0f }
            temperature = max(temperature, .15f)
        }
        temperature *= COOLING
        val tree = buildTree()
        bodies.forEachIndexed { index, body ->
            body.vx -= body.x * forces.center * temperature
            body.vy -= body.y * forces.center * temperature
            repel(tree, index)
        }
        links.forEach { (a, b) ->
            val from = bodies[a]
            val to = bodies[b]
            val dx = to.x + to.vx - from.x - from.vx
            val dy = to.y + to.vy - from.y - from.vy
            val distance = hypot(dx, dy).coerceAtLeast(.01f)
            val spring = (distance - forces.linkDistance) / distance * temperature *
                forces.linkStrength / min(degrees[a], degrees[b]).coerceAtLeast(1)
            val bias = degrees[a].toFloat() / (degrees[a] + degrees[b])
            from.vx += dx * spring * (1f - bias)
            from.vy += dy * spring * (1f - bias)
            to.vx -= dx * spring * bias
            to.vy -= dy * spring * bias
        }
        separateCollisions(pinned)
        bodies.forEachIndexed { index, body ->
            if (index == pinned) {
                body.x = validPin!!.point.x
                body.y = validPin.point.y
                body.vx = 0f
                body.vy = 0f
            } else {
                body.vx = (body.vx * .6f).coerceIn(-40f, 40f)
                body.vy = (body.vy * .6f).coerceIn(-40f, 40f)
                body.x += body.vx
                body.y += body.vy
            }
        }
    }

    private class Quad(var left: Float, var top: Float, var size: Float) {
        var count = 0
        var sumX = 0f
        var sumY = 0f
        val members = ArrayList<Int>(4)
        var children: Array<Quad?>? = null
        fun contains(body: Body) = body.x >= left && body.x <= left + size &&
            body.y >= top && body.y <= top + size

        fun reset(nextLeft: Float, nextTop: Float, nextSize: Float) {
            left = nextLeft
            top = nextTop
            size = nextSize
            count = 0
            sumX = 0f
            sumY = 0f
            members.clear()
            children?.forEach { it?.reset(0f, 0f, 0f) }
            children = null
        }
    }

    private val treeScratch = Quad(0f, 0f, 1f)

    private fun buildTree(): Quad {
        val left = bodies.minOf { it.x }
        val top = bodies.minOf { it.y }
        val size = max(bodies.maxOf { it.x } - left, bodies.maxOf { it.y } - top).coerceAtLeast(1f) + 1f
        treeScratch.reset(left, top, size)
        bodies.indices.forEach { insert(treeScratch, it, 0) }
        return treeScratch
    }

    private fun insert(quad: Quad, index: Int, depth: Int) {
        val body = bodies[index]
        quad.count++
        quad.sumX += body.x
        quad.sumY += body.y
        if (quad.children == null && (quad.members.isEmpty() || depth >= 20)) {
            quad.members.add(index)
            return
        }
        if (quad.children == null) {
            quad.children = arrayOfNulls(4)
            quad.members.forEach { insertChild(quad, it, depth) }
            quad.members.clear()
        }
        insertChild(quad, index, depth)
    }

    private fun insertChild(quad: Quad, index: Int, depth: Int) {
        val half = quad.size / 2f
        val body = bodies[index]
        val right = body.x >= quad.left + half
        val bottom = body.y >= quad.top + half
        val slot = (if (right) 1 else 0) + (if (bottom) 2 else 0)
        val child = quad.children!![slot] ?: Quad(
            quad.left + if (right) half else 0f,
            quad.top + if (bottom) half else 0f,
            half,
        ).also { quad.children!![slot] = it }
        insert(child, index, depth + 1)
    }

    private fun repel(quad: Quad, index: Int) {
        val body = bodies[index]
        val dx = body.x - quad.sumX / quad.count
        val dy = body.y - quad.sumY / quad.count
        val distanceSquared = dx * dx + dy * dy
        if (quad.children != null) {
            // Never approximate a cell containing this body: that would include self-charge.
            if (!quad.contains(body) && quad.size * quad.size < .81f * distanceSquared) {
                push(body, dx, dy, quad.count)
            } else quad.children!!.forEach { child -> if (child != null) repel(child, index) }
        } else {
            quad.members.forEach { other ->
                if (other != index) {
                    val direction = if (index < other) -1f else 1f
                    val x = body.x - bodies[other].x
                    val y = body.y - bodies[other].y
                    // Coincident seeds must separate deterministically, not remain at zero.
                    push(body, if (x == 0f && y == 0f) direction else x,
                        if (x == 0f && y == 0f) direction * .5f else y, 1)
                }
            }
        }
    }

    private fun push(body: Body, dx: Float, dy: Float, count: Int) {
        val strength = forces.repulsion * temperature * count / max(dx * dx + dy * dy, 900f)
        body.vx += dx * strength
        body.vy += dy * strength
    }

    /** Spatial buckets avoid another all-pairs loop just to prevent overlapping nodes. */
    private fun separateCollisions(pinned: Int?) {
        val cellSize = 2f * (bodies.maxOf { it.radius } + 12f)
        val buckets = mutableMapOf<Long, MutableList<Int>>()
        fun key(x: Int, y: Int) = (x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)
        val cells = bodies.map { body ->
            floor((body.x + body.vx) / cellSize).toInt() to floor((body.y + body.vy) / cellSize).toInt()
        }
        cells.forEachIndexed { index, (x, y) -> buckets.getOrPut(key(x, y)) { mutableListOf() }.add(index) }
        bodies.forEachIndexed { index, body ->
            val (cx, cy) = cells[index]
            for (x in cx - 1..cx + 1) for (y in cy - 1..cy + 1) {
                buckets[key(x, y)].orEmpty().forEach { other ->
                    if (other <= index) return@forEach
                    val neighbor = bodies[other]
                    var dx = body.x + body.vx - neighbor.x - neighbor.vx
                    var dy = body.y + body.vy - neighbor.y - neighbor.vy
                    if (dx == 0f && dy == 0f) { dx = -1f; dy = -.5f }
                    val distance = hypot(dx, dy)
                    val minimum = body.radius + neighbor.radius + 24f
                    if (distance < minimum) {
                        val strength = (minimum - distance) / distance * .5f
                        val share = if (pinned == index || pinned == other) 1f else .5f
                        if (index != pinned) { body.vx += dx * strength * share; body.vy += dy * strength * share }
                        if (other != pinned) { neighbor.vx -= dx * strength * share; neighbor.vy -= dy * strength * share }
                    }
                }
            }
        }
    }

    companion object {
        private val COOLING = .001f.pow(1f / 300f)
        /** Below this the layout is idle. [tick] returns without rebuilding the tree. */
        const val STOP_TEMPERATURE = .001f
        fun nodeRadius(degree: Int) = (6f + sqrt(degree.coerceAtLeast(0).toFloat()) * 3.4f).coerceAtMost(26f)
    }
}
