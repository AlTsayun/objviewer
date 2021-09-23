package com.bsuir.objviewer.extensions

import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.*
import org.jetbrains.kotlinx.multik.ndarray.operations.div
import kotlin.math.pow
import kotlin.math.sqrt

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
