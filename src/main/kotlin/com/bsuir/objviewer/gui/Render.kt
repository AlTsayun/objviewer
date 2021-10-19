package com.bsuir.objviewer.gui

import com.bsuir.objviewer.model.World
import org.jetbrains.kotlinx.multik.ndarray.data.D1
import org.jetbrains.kotlinx.multik.ndarray.data.NDArray
import java.util.*

class Render(private val world: World) {
}

data class ProcessedWorldObject(
    val id: UUID,
    val faces: List<Face>
)

data class Face(val items: List<Item>) {
    data class Item(val vertex: FPoint2d)
}

data class VertexProjections(
    val model: NDArray<Double, D1>,
    val view: NDArray<Double, D1>,
    val projection: NDArray<Double, D1>,
    val viewport: NDArray<Double, D1>,
    val point: FPoint2d
)