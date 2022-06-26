package com.bsuir.objviewer.gui

import androidx.compose.desktop.ComposeWindow
import androidx.compose.desktop.Window
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.layout.onSizeChanged
import com.bsuir.objviewer.extensions.cross
import com.bsuir.objviewer.extensions.dot
import com.bsuir.objviewer.extensions.normalized
import com.bsuir.objviewer.model.Camera
import com.bsuir.objviewer.model.ObjEntry
import com.bsuir.objviewer.model.World
import com.bsuir.objviewer.model.WorldObject
import com.bsuir.objviewer.service.ObjParser
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.operations.minus
import org.jetbrains.kotlinx.multik.ndarray.operations.plus
import org.jetbrains.kotlinx.multik.ndarray.operations.times
import java.awt.FileDialog
import java.lang.Math.toRadians
import java.util.*
import java.util.UUID.randomUUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random.Default.nextInt
import java.nio.file.Path as FsPath

//val CUBE_OBJ_CONTENT = listOf(
//    "v 0 0 0", // 1
//    "v 0 0 1", // 2
//    "v 0 3 0", // 3
//    "v 0 1 1", // 4
//    "v 1 0 0", // 5
//    "v 1 0 1", // 6
//    "v 1 1 0", // 7
//    "v 1 1 1", // 8
//
//    "f 1 5 6 2",
//    "f 3 7 8 4",
//    "f 5 7 8 6",
//    "f 2 6 8 4",
//    "f 1 3 7 5",
//)
val CUBE_OBJ_CONTENT = listOf(
    "v 0 0 0", // 1
    "v 0 0 1", // 2
    "v 0 1 0", // 3
    "v 0 1 1", // 4
    "v 1 0 0", // 5
    "v 1 0 1", // 6
    "v 1 1 0", // 7
    "v 1 1 1", // 8

    "f 1 2 4 3",
    "f 3 4 8 7",
    "f 7 8 6 5",
    "f 5 6 2 1",
    "f 3 7 5 1",
    "f 2 6 8 4",
)

val PYRAMID_OBJ_CONTENT = listOf(
    "v 0 0 0", // 1
    "v 0 0 1", // 2
    "v 0 3 0", // 3
    "v 0 1 1", // 4
    "v 1 0 0", // 5
    "v 1 0 1", // 6
    "v 1 1 0", // 7
    "v 1 1 1", // 8

    "f 1 3 5",
    "f 1 3 2",
    "f 1 2 5",
)

//fun process(vertex: ObjEntry.Vertex, modelMatrix: D2Array<Double>, world: World): FPoint2d = with(world) {
//    val v = mk.ndarray(listOf(vertex.x, vertex.y, vertex.z, vertex.w))
//
//    return (viewportMatrix dot projectionMatrix dot viewMatrix dot modelMatrix dot v).let {
//        FPoint2d(
//            x = (windowSize.width - (it[0] / it[3])).toFloat(),
//            y = (windowSize.height - (it[1] / it[3])).toFloat(),
//        )
//    }
//}

fun process(obj: WorldObject, world: World): ProcessedWorldObject = with(world) {
    val faces = ArrayList<ProcessedFace>()
    for (face in obj.faces) {
        var isFaceOutOfProjection = false
        var isFacePointsOutwards = false
        val points = ArrayList<VertexProjections>()
        for (vertex in face.items.map { it.vertex }) {
            val v = mk.ndarray(listOf(vertex.x, vertex.y, vertex.z, vertex.w))
            val modelV = obj.modelMatrix dot v
            val viewV = viewMatrix dot modelV
            val projectionV = projectionMatrix dot viewV
            val viewportV = viewportMatrix dot projectionV

            if (projectionV[2] < world.projectionNear || projectionV[2] > world.projectionFar) {
                isFaceOutOfProjection = true
                break
            }

            val point = FPoint2d(
                x = (windowSize.width - (viewportV[0] / viewportV[3])).toFloat(),
                y = (windowSize.height - (viewportV[1] / viewportV[3])).toFloat(),
            )

            if (point.x < 0 || point.x > windowSize.width ||
                point.y < 0 || point.y > windowSize.height) {
                isFaceOutOfProjection = true
                break
            }

            points.add(VertexProjections(modelV, viewV, projectionV, viewportV, point))

        }

//        if (!isFaceOutOfProjection) {
//            val v0 = points[0].model
//            val v1 = points[1].model
//            val v2 = points[2].model
//            val line1 = mk.ndarray(listOf(v1[0].toDouble() - v0[0], v1[1].toDouble() - v0[1], v1[2].toDouble() - v0[2]))
//            val line2 = mk.ndarray(listOf(v2[0].toDouble() - v1[0], v2[1].toDouble() - v1[1], v2[2].toDouble() - v1[2]))
//            val faceNormal = line1 cross line2
//            if (faceNormal dot (world.cam.target - world.cam.position) > 0) {
//                isFacePointsOutwards = true
//            }
//        }

        if (!isFaceOutOfProjection && !isFacePointsOutwards) {
            faces.add(ProcessedFace(points.map { ProcessedFace.Item(DepthPoint2d(it.point.x.toInt(), it.point.y.toInt(), it.projection[2].toUInt())) }))
        }

    }
    return ProcessedWorldObject(obj.id, faces)
}

data class FPoint2d(val x: Float, val y: Float)
data class Point2d(val x: Int, val y: Int) {
    constructor(point: FPoint2d) : this(point.x.toInt(), point.y.toInt())
}
data class DepthPoint2d(val x: Int, val y: Int, val depth: UInt)

class ZBuffer(width: Int, height: Int) {
    private val items: Array<Array<UInt>>
    private val _chunkedItems: MutableMap<Color, MutableList<Pair<Int, Int>>>
    val chunkedItems: Map<Color, List<Pair<Int, Int>>>
            get() = _chunkedItems

    init {
        items = Array(height) {
            Array(width) {UInt.MAX_VALUE
            }
        }
        _chunkedItems = HashMap()
    }

    operator fun get(i: Int): Array<UInt>{
        return items[i]
    }

    fun addPoint(x: Int, y: Int, depth: UInt, color: Color){

        if (this[y][x] > depth){
            if (_chunkedItems[color] == null) {
                _chunkedItems[color] = ArrayList()
            }
            _chunkedItems[color]?.add(x to y)
            this[y][x] = depth
        }
    }


    fun transferTo(scope: DrawScope){
        chunkedItems.forEach{ entry ->
            val points = entry.value
                .map { Offset(it.first.toFloat(), it.second.toFloat()) }
            scope.drawPoints(points = points, pointMode = PointMode.Points, strokeWidth = 1f, color = entry.key)
        }
    }
}

fun main() {
    val objParser = ObjParser()
//    val parsed = objParser.parse(Files.readAllLines(Paths.get("./cat.obj")))
    val parsed = objParser.parse(CUBE_OBJ_CONTENT)

    return Window {
        MaterialTheme {
            var world by remember {
                mutableStateOf(
                    World(
                        cam = Camera(
                            speed = 5.0,
                            position = mk.ndarray(listOf(-5.0, 0.5, 0.5)),
                        ),
                        objects = listOf(
                            parsed.let {
                                WorldObject(
                                    randomUUID(),
                                    it.vertexes,
                                    it.textures,
                                    it.normals,
                                    it.faces,
                                )
                            }
                        )
                    )
                )
            }
            val initPointerPosition = Offset(-1f, -1f)
            var lastPointerPosition by remember { mutableStateOf(initPointerPosition) }

            var yaw by remember { mutableStateOf(-90.0) }
            var pitch by remember { mutableStateOf(0.0) }

            val focusRequester = FocusRequester()

            Box(
                Modifier
                    .pointerMoveFilter(
                        onEnter = {
                            lastPointerPosition = initPointerPosition
                            return@pointerMoveFilter false
                        },
                        onMove = {
                            if (lastPointerPosition == initPointerPosition) {
                                lastPointerPosition = it
                            }

                            var offset = Offset(
                                it.x - lastPointerPosition.x,
                                lastPointerPosition.y - it.y,
                            )
                            lastPointerPosition = it

                            val sensitivity = 0.5f
                            offset *= sensitivity
                            yaw += offset.x
                            pitch += offset.y

                            // make sure that when pitch is out of bounds, screen doesn't get flipped
                            if (pitch > 89.0) pitch = 89.0
                            if (pitch < -89.0) pitch = -89.0

                            world = world.copy(
                                cam = world.cam.copy(
                                    front = mk.ndarray(
                                        listOf(
                                            cos(toRadians(yaw)) * cos(toRadians(pitch)),
                                            sin(toRadians(pitch)),
                                            sin(toRadians(yaw)) * cos(toRadians(pitch)),
                                        )
                                    ).normalized()
                                )
                            )
                            return@pointerMoveFilter false
                        })
                    .onKeyEvent {
                        if (it.type == KeyEventType.KeyDown) {
                            with(world) {
                                when (it.key) {
                                    Key.W -> world =
                                        world.copy(cam = cam.copy(position = cam.position + cam.front * cam.speed))
                                    Key.S -> world =
                                        world.copy(cam = cam.copy(position = cam.position - cam.front * cam.speed))
                                    Key.A -> world = world.copy(
                                        cam =
                                        cam.copy(position = cam.position - (cam.front cross cam.up).normalized() * cam.speed)
                                    )
                                    Key.D -> world = world.copy(
                                        cam =
                                        cam.copy(position = cam.position + (cam.front cross cam.up).normalized() * cam.speed)
                                    )
                                }
                            }
                        }
                        return@onKeyEvent false
                    }
                    .focusRequester(focusRequester)
                    .focusable()
                    .clickable { focusRequester.requestFocus() }
            ) {
                Canvas(Modifier
                    .fillMaxSize()
                    .onSizeChanged { world = world.copy(windowSize = it) }
                ) {
                    val random = Random()
                    val zBuffer = ZBuffer(world.windowSize.width + 1, world.windowSize.height + 1)
                    val colors = arrayOf(Color.Black, Color.Black, Color.Magenta, Color.Red, Color.Green, Color.Cyan)
                    world.objects
                        .map { process(it, world) }
                        .forEach { obj ->
                            obj.faces.forEach { face ->
                                drawFillFace(face, zBuffer, Color.Gray)
//                                drawPathFace(face, Color.Blue)
//                                drawStrokeFace(face, zBuffer, colors[random.nextInt(colors.size)])
                            }
                        }
                    zBuffer.transferTo(this)
                }
            }

//            Row {
//                Column {
//                    world.objects.forEachIndexed { ix, worldObject ->
//                        Row {
//                            Text("${ix - 1}: ${worldObject.id}")
//                        }
//                    }
//                    Row {
//                        Button(onClick = {
//                            openFileDialog(
//                                AppManager.focusedWindow!!.window,
//                                title = "Title",
//                                allowedExtensions = listOf("obj"),
//                                allowMultiSelection = false
//                            )
//                        }) {
//                            Text("Load")
//                        }
//                    }
//                }
//            }

//            Column(horizontalAlignment = Alignment.End) {
//                Text("Cam: ${world.camPosition[0]} x ${world.camPosition[2]}")
//                Text("Target: ${world.camTarget[0]} x ${world.camTarget[2]}")
//            }

            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        }
    }
}

private fun DrawScope.drawStrokeFace(face: ProcessedFace, zBuffer: ZBuffer, color: Color) {
    face.items
        .pairedInCycle()
        .forEach { pair ->
            drawLine(pair.first.point, pair.second.point, zBuffer, color)
        }
}
private fun DrawScope.drawPathFace(face: ProcessedFace, color: Color) {
    val first = face.items[0].point
    val path = Path()
    path.moveTo(first.x.toFloat(), first.y.toFloat())
    face.items
        .map { it.point }
        .forEach { path.lineTo(it.x.toFloat(), it.y.toFloat()) }

    path.lineTo(first.x.toFloat(), first.y.toFloat())
    drawPath(path, color, style = Stroke(width = 1f))
}
private fun DrawScope.drawFillFace(face: ProcessedFace, zBuffer: ZBuffer, color: Color) {
    val allEdges = face.items
        .pairedInCycle()
        .map { pair ->
            val i1 = pair.first
            val i2 = pair.second
            val sorted = if (i1.point.y < i2.point.y) {
                i1.point to i2.point
            } else {
                i2.point to i1.point
            }
            Edge(
                sorted.first.x,
                sorted.first.y,
                sorted.first.depth,
                sorted.second.y,
                (sorted.second.depth - sorted.first.depth).toFloat() / (sorted.second.y - sorted.first.y).toFloat(),
                (sorted.second.x - sorted.first.x) / (sorted.second.y - sorted.first.y).toFloat()
            )
        }

    val yComparator = compareBy<Edge> { it.minY }
    val xComparator = compareBy<Edge> { it.minX }
    //todo: fix comparing float to 0
    val globalEdges = allEdges
//        .filter { it.invSlope != 0f }
        .sortedWith(yComparator.then(xComparator))

    if (globalEdges.isNotEmpty()) {
        for (scanLine: Int in globalEdges.first().minY..globalEdges.last().maxY){
            val activeEdges = globalEdges
                .filter { (it.minY <= scanLine) && (it.maxY >= scanLine) }
                .chunked(2)
                .filter { it.size == 2 }

            activeEdges.forEach {
                val fromX = getCurrentX(it[0], scanLine)
                val toX = getCurrentX(it[1], scanLine)
                val fromDepth = getCurrentDepth(it[0], scanLine)
                val toDepth = getCurrentDepth(it[1], scanLine)
                drawLine(
                    DepthPoint2d(fromX, scanLine, fromDepth),
                    DepthPoint2d(toX, scanLine, toDepth),
                    zBuffer,
                    color
                )
            }
        }
    }

}


fun getCurrentX(edge: Edge, y: Int): Int{
    return edge.minX + ((y - edge.minY) * edge.invSlope).toInt()
}

fun getCurrentDepth(edge: Edge, y: Int): UInt{
    return edge.minDepth + ((y - edge.minY) * edge.invDepthSlope).toUInt()
}

fun <T> paired(list: Collection<T>): List<Pair<T, T>> {
    val pairs: MutableList<Pair<T, T>> = ArrayList(list.size)
    list.stream().reduce { a: T, b: T ->
        pairs.add(a to b)
        return@reduce b
    }
    return pairs
}

fun <T> List<T>.pairedInCycle(): List<Pair<T, T>> {
    val pairs: MutableList<Pair<T, T>> = ArrayList(this.size)
    this.stream().reduce { a: T, b: T ->
        pairs.add(Pair(a, b))
        return@reduce b
    }
    pairs.add(Pair(this.last(), this.first()))
    return pairs
}

fun isFaceTooClose(cam: Camera, face: ObjEntry.Face): Boolean {
    val delta = 5f.pow(2)
    var res = false
    for (item in face.items) {
        res =
            res || ((cam.position[0] - item.vertex.x).pow(2) + (cam.position[1] - item.vertex.y).pow(2) + (cam.position[1] - item.vertex.z).pow(
                2
            ) < delta)
    }
    return res
}

fun openFileDialog(
    window: ComposeWindow,
    title: String,
    allowedExtensions: List<String>,
    allowMultiSelection: Boolean = false
): List<FsPath> {
    return FileDialog(window, title, FileDialog.LOAD).apply {
        // Windows
        file = allowedExtensions.joinToString(";") { "*$it" }

        // Linux
        setFilenameFilter { _, name ->
            allowedExtensions.any {
                name.endsWith(it)
            }
        }

        isMultipleMode = allowMultiSelection
        isVisible = true

    }.files.map { it.toPath() }
}

fun DrawScope.drawLine(p0: DepthPoint2d, p1: DepthPoint2d,  zBuffer: ZBuffer, color: Color) {
    val deltaX = abs(p1.x - p0.x)
    val deltaY = abs(p1.y - p0.y)
    val deltaDepth = abs(p1.depth.toInt() - p0.depth.toInt())
    val dirX = sign(p1.x - p0.x)
    val dirY = sign(p1.y - p0.y)
    val dirDepth = sign(p1.depth.toInt() - p0.depth.toInt())

    var x = p0.x
    var y = p0.y
    var depth = p0.depth

    zBuffer.addPoint(p0.x, p0.y, p0.depth, color)

    if (deltaX >= deltaY && deltaX >= deltaDepth) {
        //iteration by x
        var errorY = 2 * deltaY - deltaX
        var errorDepth = 2 * deltaDepth - deltaX
        for (i: Int in 1..deltaX) {
            x += dirX
            if (errorY >= 0) {
                y += dirY
                errorY -= 2 * deltaX
            }
            if (errorDepth >= 0) {
                depth = (depth.toInt() - dirDepth).toUInt()
                errorDepth -= 2 * deltaX
            }
            errorY += 2 * deltaY
            errorDepth += 2 * deltaDepth
            zBuffer.addPoint(x, y, depth, color)
        }
    } else if (deltaY >= deltaX && deltaY >= deltaDepth) {
        //iteration by y
        var errorX = 2 * deltaX - deltaY
        var errorDepth = 2 * deltaDepth - deltaY
        for (i: Int in 1..deltaY) {
            y += dirY
            if (errorX >= 0) {
                x += dirX
                errorX -= 2 * deltaY
            }
            if (errorDepth >= 0) {
                depth = (depth.toInt() - dirDepth).toUInt()
                errorDepth -= 2 * deltaY
            }
            errorX += 2 * deltaX
            errorDepth += 2 * deltaDepth
            zBuffer.addPoint(x, y, depth, color)
        }
    } else {
        //iteration by depth
        var errorX = 2 * deltaX - deltaDepth
        var errorY = 2 * deltaY - deltaDepth
        for (i: Int in 1..deltaDepth) {
            depth = (depth.toInt() - dirDepth).toUInt()
            if (errorX >= 0) {
                x += dirX
                errorX -= 2 * deltaDepth
            }
            if (errorY >= 0) {
                y += dirY
                errorY -= 2 * deltaDepth
            }
            errorX += 2 * deltaX
            errorY += 2 * deltaY
            zBuffer.addPoint(x, y, depth, color)
        }
    }
//    drawPoints(applyZBuf(points.filter { it.x > -1 && it.y > -1 && it.x < 400 && it.y < 400 }, zBuffer).map { Offset(it.x.toFloat(), it.y.toFloat())}, PointMode.Points, color = color, strokeWidth = 1f)

//    val incX: Int
//    val incY: Int
//    val incDepth: Int
//    val iterated: Int
//    val es: Int
//    if (deltaX > deltaY && deltaX > deltaDepth) {
//        //iteration by x
//        incX = dirX
//        incY = 0
//        incDepth = 0
//        es = deltaY
//        iterated = deltaX
//    } else if (deltaY > deltaX && deltaY > deltaDepth) {
//        //iteration by y
//        incX = 0
//        incY = dirY
//        incDepth = 0
//        es = deltaX
//        iterated = deltaY
//    } else {
//        //iteration by depth
//        incX = 0
//        incY = 0
//        incDepth = dirDepth
//        es = deltaX
//        iterated = deltaY
//    }
//
//    val points = ArrayList<Offset>(iterated)
//
//    var x = p0.x
//    var y = p0.y
//    var depth = p0.depth
//    var error = iterated / 2
//    points.add(Offset(x.toFloat(), y.toFloat()))
//    for (t in 0..iterated){
//        error -= es
//        if (error < 0){
//            error += iterated
//            x += dirX
//            y += dirY
//        } else {
//            x += incX
//            y += incY
//        }
//        points.add(Offset(x.toFloat(), y.toFloat()))
//    }
//    drawPoints(points, PointMode.Points, color = color, strokeWidth = 1f)

}

fun sign(a: Int) = when {
    a > 0 -> 1
    a < 0 -> -1
    else -> 0
}

data class Edge(val minX: Int, val minY: Int, val minDepth: UInt, val maxY: Int, val invDepthSlope: Float, val invSlope: Float)
