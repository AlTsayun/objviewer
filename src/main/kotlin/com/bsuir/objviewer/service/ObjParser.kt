package com.bsuir.objviewer.service

import androidx.compose.desktop.Window
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.bsuir.objviewer.model.ObjEntry.*
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.*
import org.jetbrains.kotlinx.multik.ndarray.operations.div
import org.jetbrains.kotlinx.multik.ndarray.operations.minus
import org.jetbrains.kotlinx.multik.ndarray.operations.plus
import org.jetbrains.kotlinx.multik.ndarray.operations.times
import java.lang.Math.toRadians
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.*

private data class ObjEntries(
    val vertexes: MutableList<Vertex> = ArrayList(),
    val textures: MutableList<Texture> = ArrayList(),
    val normals: MutableList<Normal> = ArrayList(),
    val faces: MutableList<Face> = ArrayList(),
)

class ObjParser {

    private val whitespaces = "\\s+".toPattern()

    fun parse(lines: List<String>): List<Face> {

        val entries = ObjEntries()
        lines
            .map { it.trim() }
            .forEach {
                when {
                    it.startsWith("v ") -> parseVertex(it).let(entries.vertexes::add)
                    it.startsWith("vt ") -> parseTexture(it).let(entries.textures::add)
                    it.startsWith("vn ") -> parseNormal(it).let(entries.normals::add)
                    it.startsWith("f ") -> parseFace(it, entries).let(entries.faces::add)
                }
            }
        return entries.faces
    }

    private fun parseVertex(line: String): Vertex {
        val parts = line.split(whitespaces)
        val (_, x, y, z) = parts
        val w = parts.getOrNull(4)
        return Vertex(
            x = x.toDouble(),
            y = y.toDouble(),
            z = z.toDouble(),
            w = w?.toDouble() ?: 1.0,
        )
    }

    private fun parseTexture(line: String): Texture {
        val parts = line.split(whitespaces)
        val u = parts[1]
        val v = parts.getOrNull(2)
        val w = parts.getOrNull(3)
        return Texture(
            u = u.toDouble(),
            v = v?.toDouble(),
            w = w?.toDouble(),
        )
    }

    private fun parseNormal(line: String): Normal {
        val (_, i, j, k) = line.split(whitespaces)
        return Normal(
            i = i.toDouble(),
            j = j.toDouble(),
            k = k.toDouble(),
        )
    }

    private fun parseFace(line: String, entries: ObjEntries): Face {
        fun String.toIndex(size: Int) = this.toInt().let {
            when {
                it > 0 -> it - 1
                it < 0 -> size + it
                else -> throw RuntimeException()
            }
        }

        return line.split(whitespaces)
            .asSequence()
            .drop(1)
            .map { group ->
                val parts = group.split("/")
                val vertexIndex = parts[0].toIndex(size = entries.vertexes.size)
                val textureIndex = parts.getOrNull(1)
                    ?.takeIf { it.isNotBlank() }
                    ?.toIndex(size = entries.textures.size)
                val normalIndex = parts.getOrNull(2)
                    ?.takeIf { it.isNotBlank() }
                    ?.toIndex(size = entries.normals.size)
                Face.Item(
                    vertex = entries.vertexes[vertexIndex],
                    texture = textureIndex?.let { entries.textures[it] },
                    normal = normalIndex?.let { entries.normals[it] },
                )
            }
            .toList()
            .let { items -> Face(items) }
    }
}

infix fun D1Array<Double>.dot(another: D1Array<Double>): Double =
    mk.linalg.dot(this, another)

infix fun D1Array<Double>.cross(another: D1Array<Double>): D1Array<Double> {
    require(this.size == 3)
    require(another.size == 3)
    return mk.ndarray(
        listOf(
            this[1] * another[2] - this[2] * another[1],
            this[2] * another[0] - this[0] * another[2],
            this[0] * another[1] - this[1] * another[0],
        )
    )
}

fun D1Array<Double>.normalized(): D1Array<Double> {
    require(this.size == 3)
    return this / sqrt(this[0].pow(2) + this[1].pow(2) + this[2].pow(2))
}

infix fun <T : Number, D : Dim2> MultiArray<T, D2>.dot(another: MultiArray<T, D>): NDArray<T, D> =
    mk.linalg.dot(this, another)

fun process(vertex: Vertex, camPosition: D1Array<Double>, camFront: D1Array<Double>, windowSize: IntSize): Vertex {
    val v = mk.ndarray(listOf(vertex.x, vertex.y, vertex.z, vertex.w))

    val modelMatrix = mk.ndarray(
        listOf(
            listOf(1.0, 0.0, 0.0, 0.0),
            listOf(0.0, 1.0, 0.0, 0.0),
            listOf(0.0, 0.0, 1.0, 0.0),
            listOf(0.0, 0.0, 0.0, 1.0),
        )
    )

    val camTarget = camPosition + camFront
    val up = mk.ndarray(listOf(0.0, 1.0, 0.0))

    val zAxis = (camPosition - camTarget).normalized()
    val xAxis = (up cross zAxis).normalized()
    val yAxis = (zAxis cross xAxis).normalized()

    val viewMatrix = mk.ndarray(
        listOf(
            listOf(xAxis[0], xAxis[1], xAxis[2], -(xAxis dot camPosition)),
            listOf(yAxis[0], yAxis[1], yAxis[2], -(yAxis dot camPosition)),
            listOf(zAxis[0], zAxis[1], zAxis[2], -(zAxis dot camPosition)),
            listOf(0.0, 0.0, 0.0, 1.0),
        )
    )

    val zNear = 1.0
//    val zFar = 10.0
    val zFar = 500.0
    val aspect = windowSize.width / windowSize.height
    val fov = 5

    val projectionMatrix = mk.ndarray(
        listOf(
            listOf(1 / (aspect * tan(fov / 2.0)), 0.0, 0.0, 0.0),
            listOf(0.0, 1 / tan(fov / 2.0), 0.0, 0.0),
            listOf(0.0, 0.0, zFar / (zNear - zFar), zNear * zFar / (zNear - zFar)),
            listOf(0.0, 0.0, -1.0, 0.0),
        )
    )

    val windowWidth = windowSize.width.toDouble()
    val windowHeight = windowSize.height.toDouble()
    val windowXMin = 0
    val windowYMin = 0

    val viewportMatrix: D2Array<Double> = mk.ndarray(
        listOf(
            listOf(windowWidth / 2, 0.0, 0.0, windowXMin + windowWidth / 2),
            listOf(0.0, -windowHeight / 2, 0.0, windowYMin + windowHeight / 2),
            listOf(0.0, 0.0, 1.0, 0.0),
            listOf(0.0, 0.0, 0.0, 1.0),
        )
    )

    val finalMatrix = viewportMatrix dot projectionMatrix dot viewMatrix dot modelMatrix

    return (finalMatrix dot v)
        .let { Vertex(
            x = windowSize.width - (it[0] / it[3]),
            y = windowSize.height - (it[1] / it[3]),
            z = it[2] / it[3],
            w = it[3]
        ) }
}

data class DPoint(val x: Double, val y: Double)

fun main() {
    val parse = ObjParser().parse(
        Files.readAllLines(Paths.get("C:\\Users\\olegg\\bsuir\\sem7\\objviewer\\cat.obj"))
//        listOf(
//            "v 0 0 0", // 1
//            "v 0 0 1", // 2
//            "v 0 3 0", // 3
//            "v 0 1 1", // 4
//            "v 1 0 0", // 5
//            "v 1 0 1", // 6
//            "v 1 1 0", // 7
//            "v 1 1 1", // 8
//
//            "f 1 5 6 2",
//            "f 3 7 8 4",
//            "f 5 7 8 6",
//            "f 2 6 8 4",
//            "f 1 3 7 5",

//            "f 1 3 5",
//            "f 1 3 2",
//            "f 1 2 5",
//        )
    )

    return Window {
        MaterialTheme {
            val focusRequester = FocusRequester()

            val initPointerPosition = Offset(-1f, -1f)
            var lastPointerPosition by remember { mutableStateOf(initPointerPosition) }
            var yaw by remember { mutableStateOf(-90f) }
            var pitch by remember { mutableStateOf(0f) }
            val camSpeed = 20.0
//            val camSpeed = 0.05
            var camPosition by remember { mutableStateOf(mk.ndarray(listOf(0.0, 0.5, 0.0))) }
            var camFront by remember { mutableStateOf(mk.ndarray(listOf(0.0, 0.0, -1.0))) }
            val camUp = mk.ndarray(listOf(0.0, 1.0, 0.0))
            var windowSize by remember { mutableStateOf(IntSize(1, 1)) }


            Box(
                Modifier
                    .pointerMoveFilter(
                        onEnter = {
                            lastPointerPosition = initPointerPosition
                            false
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
                            if (pitch > 89.0f) pitch = 89.0f;
                            if (pitch < -89.0f) pitch = -89.0f;

                            camFront = mk.ndarray(
                                listOf(
                                    cos(toRadians(yaw.toDouble())) * cos(toRadians(pitch.toDouble())),
                                    sin(toRadians(pitch.toDouble())),
//                                    0.0,
                                    sin(toRadians(yaw.toDouble())) * cos(toRadians(pitch.toDouble())),
                                )
                            ).normalized()
                            return@pointerMoveFilter false
                        })
                    .onKeyEvent {

                        if (it.type == KeyEventType.KeyDown) {
                            when (it.key) {
                                Key.W -> camPosition += camFront * camSpeed
                                Key.S -> camPosition -= camFront * camSpeed
                                Key.A -> camPosition -= (camFront cross camUp).normalized() * camSpeed
                                Key.D -> camPosition += (camFront cross camUp).normalized() * camSpeed
                            }
                        }
                        return@onKeyEvent false
                    }
                    .focusRequester(focusRequester)
                    .focusable()
                    .onSizeChanged { windowSize = it }
                    .clickable { focusRequester.requestFocus() }
            ) {

//                Column {
//                    Button(onClick = { camPosition = camPosition.copy(y = camPosition.y - delta) }) {
//                        Text("Top")
//                    }
//
//                    Row {
//                        Button(onClick = { camPosition = camPosition.copy(x = camPosition.x - delta) }) {
//                            Text("Left")
//                        }
//
//                        Button(onClick = { camPosition = camPosition.copy(x = camPosition.x + delta) }) {
//                            Text("Right")
//                        }
//                    }
//
//                    Button(onClick = { camPosition = camPosition.copy(y = camPosition.y + delta) }) {
//                        Text("Bottom")
//                    }
//                }

                Column {
                    Text("Cam: ${camPosition[0]} x ${camPosition[2]}")
                    val target = camPosition + camFront
                    Text("Target: ${target[0]} x ${target[2]}")
                }

                Canvas(Modifier.fillMaxSize()) {
                    parse.forEach { face ->
                        val first = process(face.items[0].vertex, camPosition, camFront, windowSize)
                        val path = Path()
                        path.moveTo(
                            first.x.toFloat(),
                            first.y.toFloat(),
                        )

                        face.items
                            .drop(1)
                            .map { process(it.vertex, camPosition, camFront, windowSize) }
                            .forEach {
                                println(it)
                                path.lineTo(
                                    it.x.toFloat(),
                                    it.y.toFloat(),
                                )
                            }

                        path.lineTo(
                            first.x.toFloat(),
                            first.y.toFloat(),
                        )


                        drawPath(path, Color.Black, style = Stroke(width = 1f))
                    }
                }
            }

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }
    }
}
