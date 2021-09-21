package com.bsuir.objviewer.gui

import androidx.compose.desktop.Window
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

fun main() = Window {
    Canvas(Modifier.fillMaxSize()) {
        drawLine(
            color = Color.Black,
            start = Offset(0f, 0f),
            end = Offset(5f, 5f)
        )
    }
}
