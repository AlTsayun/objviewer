package com.bsuir.objviewer.gui

import androidx.compose.desktop.AppManager
import androidx.compose.desktop.ComposeWindow
import androidx.compose.desktop.Window
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
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
import com.bsuir.objviewer.extensions.cross
import com.bsuir.objviewer.extensions.dot
import com.bsuir.objviewer.extensions.normalized
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
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID.randomUUID
import kotlin.math.cos
import kotlin.math.sin
import java.nio.file.Path as FsPath

val CUBE_OBJ_CONTENT = listOf(
    "v 0 0 0", // 1
    "v 0 0 1", // 2
    "v 0 3 0", // 3
    "v 0 1 1", // 4
    "v 1 0 0", // 5
    "v 1 0 1", // 6
    "v 1 1 0", // 7
    "v 1 1 1", // 8

    "f 1 5 6 2",
    "f 3 7 8 4",
    "f 5 7 8 6",
    "f 2 6 8 4",
    "f 1 3 7 5",
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

fun process(vertex: ObjEntry.Vertex, modelMatrix: D2Array<Double>, world: World): F2dPoint = with(world) {
    val v = mk.ndarray(listOf(vertex.x, vertex.y, vertex.z, vertex.w))

    return (worldMatrix dot modelMatrix dot v).let {
        F2dPoint(
            x = (windowSize.width - (it[0] / it[3])).toFloat(),
            y = (windowSize.height - (it[1] / it[3])).toFloat(),
        )
    }
}

data class F2dPoint(val x: Float, val y: Float)

fun main() {
    val objEntries = ObjParser().parse(Files.readAllLines(Paths.get("C:/Users/olegg/bsuir/sem7/objviewer/cat.obj")))

    return Window {
        MaterialTheme {
            var world by remember {
                mutableStateOf(
                    World(
                        camSpeed = 13.0,
                        camPosition = mk.ndarray(listOf(0.0, 0.5, 0.0)),
                        objects = listOf(
                            WorldObject(
                                randomUUID(),
                                objEntries.vertexes,
                                objEntries.textures,
                                objEntries.normals,
                                objEntries.faces,
                            )
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
                                camFront = mk.ndarray(
                                    listOf(
                                        cos(toRadians(yaw)) * cos(toRadians(pitch)),
                                        sin(toRadians(pitch)),
                                        sin(toRadians(yaw)) * cos(toRadians(pitch)),
                                    )
                                ).normalized()
                            )
                            return@pointerMoveFilter false
                        })
                    .onKeyEvent {
                        if (it.type == KeyEventType.KeyDown) {
                            with(world) {
                                when (it.key) {
                                    Key.W -> world = copy(camPosition = camPosition + camFront * camSpeed)
                                    Key.S -> world = copy(camPosition = camPosition - camFront * camSpeed)
                                    Key.A -> world = copy(camPosition = camPosition - (camFront cross camUp).normalized() * camSpeed)
                                    Key.D -> world = copy(camPosition = camPosition + (camFront cross camUp).normalized() * camSpeed)
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
                    world.objects.forEach { worldObject ->
                        val processedVertexes = worldObject.vertexes.map { process(it, worldObject.modelMatrix, world) }
                        objEntries.faces.forEach { face ->
                            val first = processedVertexes[face.items[0].vertexIx]
                            val path = Path()
                            path.moveTo(first.x, first.y)

                            face.items
                                .drop(1)
                                .map { processedVertexes[it.vertexIx] }
                                .forEach { path.lineTo(it.x, it.y) }

                            path.lineTo(first.x, first.y)
                            drawPath(path, Color.Black, style = Stroke(width = 1f))
                        }
                    }
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

fun openFileDialog(window: ComposeWindow, title: String, allowedExtensions: List<String>, allowMultiSelection: Boolean = false): List<FsPath> {
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
