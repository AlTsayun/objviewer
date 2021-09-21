package com.bsuir.objviewer.model

sealed interface ObjEntry {

    data class Vertex(val x: Double, val y: Double, val z: Double, val w: Double = 1.0) : ObjEntry
    data class Texture(val u: Double, val v: Double?, val w: Double?) : ObjEntry
    data class Normal(val i: Double, val j: Double, val k: Double) : ObjEntry
    data class Face(val items: List<Item>) : ObjEntry {
        data class Item(val vertex: Vertex, val texture: Texture?, val normal: Normal?)
    }
}
