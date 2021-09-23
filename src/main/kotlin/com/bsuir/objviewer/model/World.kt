package com.bsuir.objviewer.model

import androidx.compose.ui.unit.IntSize
import com.bsuir.objviewer.extensions.cross
import com.bsuir.objviewer.extensions.dot
import com.bsuir.objviewer.extensions.normalized
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.operations.minus
import org.jetbrains.kotlinx.multik.ndarray.operations.plus
import java.util.*
import kotlin.math.tan

data class World(
    val objects: List<WorldObject>,
    val camSpeed: Double,
    val camPosition: D1Array<Double>,
    val camFront: D1Array<Double> = mk.ndarray(listOf(0.0, 0.0, -1.0)),
    val windowSize: IntSize = IntSize(1, 1),
    val projectionNear: Double = 1.0,
    val projectionFar: Double = 500.0,
    val fieldOfView: Double = 5.0,
) {
    val camTarget: D1Array<Double> = camPosition + camFront
    val camUp: D1Array<Double> = mk.ndarray(listOf(0.0, 1.0, 0.0))

    private val viewMatrix = run {
        val zAxis = (camPosition - camTarget).normalized()
        val xAxis = (camUp cross zAxis).normalized()
        val yAxis = (zAxis cross xAxis).normalized()
        mk.ndarray(
            listOf(
                listOf(xAxis[0], xAxis[1], xAxis[2], -(xAxis dot camPosition)),
                listOf(yAxis[0], yAxis[1], yAxis[2], -(yAxis dot camPosition)),
                listOf(zAxis[0], zAxis[1], zAxis[2], -(zAxis dot camPosition)),
                listOf(0.0, 0.0, 0.0, 1.0),
            )
        )
    }

    private val viewportMatrix = run {
        val windowWidth = windowSize.width.toDouble()
        val windowHeight = windowSize.height.toDouble()
        val windowXMin = 0
        val windowYMin = 0
        mk.ndarray(
            listOf(
                listOf(windowWidth / 2, 0.0, 0.0, windowXMin + windowWidth / 2),
                listOf(0.0, -windowHeight / 2, 0.0, windowYMin + windowHeight / 2),
                listOf(0.0, 0.0, 1.0, 0.0),
                listOf(0.0, 0.0, 0.0, 1.0),
            )
        )
    }

    private val projectionMatrix = run {
        val aspect = windowSize.width / windowSize.height
        mk.ndarray(
            listOf(
                listOf(1 / (aspect * tan(fieldOfView / 2.0)), 0.0, 0.0, 0.0),
                listOf(0.0, 1 / tan(fieldOfView / 2.0), 0.0, 0.0),
                listOf(0.0, 0.0, projectionFar / (projectionNear - projectionFar), projectionNear * projectionFar / (projectionNear - projectionFar)),
                listOf(0.0, 0.0, -1.0, 0.0),
            )
        )
    }

    val worldMatrix = viewportMatrix dot projectionMatrix dot viewMatrix
}

data class WorldObject(
    val id: UUID,
    val vertexes: List<ObjEntry.Vertex>,
    val textures: List<ObjEntry.Texture>,
    val normals: List<ObjEntry.Normal>,
    val faces: List<ObjEntry.Face>,
//    val position: D1Array<Double>,
//    val scale,
//    val rotation,
) {
    val modelMatrix: D2Array<Double> = mk.ndarray(
        listOf(
            listOf(1.0, 0.0, 0.0, 0.0),
            listOf(0.0, 1.0, 0.0, 0.0),
            listOf(0.0, 0.0, 1.0, 0.0),
            listOf(0.0, 0.0, 0.0, 1.0),
        )
    )
}
