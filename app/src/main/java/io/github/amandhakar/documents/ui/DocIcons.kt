package io.github.amandhakar.documents.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The few icons the app needs, drawn from path data so it needs no icon library (shapes follow the Apache-2.0 Material icon set). */
object DocIcons {
    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes(path), fill = SolidColor(Color.Black))
            .build()

    val Public by lazy {
        icon(
            "Public",
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM11,19.93c-3.95,-0.49 -7,-3.85 -7,-7.93 0,-0.62 0.08,-1.21 0.21,-1.79L9,15v1c0,1.1 0.9,2 2,2v1.93zM17.9,17.39c-0.26,-0.81 -1,-1.39 -1.9,-1.39h-1v-3c0,-0.55 -0.45,-1 -1,-1H8v-2h2c0.55,0 1,-0.45 1,-1V7h2c1.1,0 2,-0.9 2,-2v-0.41c2.93,1.19 5,4.06 5,7.41 0,2.08 -0.8,3.97 -2.1,5.39z",
        )
    }
    val ArrowBack by lazy { icon("ArrowBack", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z") }
    val Edit by lazy {
        icon(
            "Edit",
            "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.82z",
        )
    }
    val Save by lazy {
        icon(
            "Save",
            "M17,3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V7l-4,-4zM12,19c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3 3,1.34 3,3 -1.34,3 -3,3zM15,9H5V5h10v4z",
        )
    }
    val Check by lazy { icon("Check", "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z") }
    val Close by lazy {
        icon(
            "Close",
            "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z",
        )
    }
    val Share by lazy {
        icon(
            "Share",
            "M18,16.08c-0.76,0 -1.44,0.3 -1.96,0.77L8.91,12.7c0.05,-0.23 0.09,-0.46 0.09,-0.7s-0.04,-0.47 -0.09,-0.7l7.05,-4.11c0.54,0.5 1.25,0.81 2.04,0.81 1.66,0 3,-1.34 3,-3s-1.34,-3 -3,-3 -3,1.34 -3,3c0,0.24 0.04,0.47 0.09,0.7L8.04,9.81C7.5,9.31 6.79,9 6,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3c0.79,0 1.5,-0.31 2.04,-0.81l7.12,4.16c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.61 1.31,2.92 2.92,2.92 1.61,0 2.92,-1.31 2.92,-2.92s-1.31,-2.92 -2.92,-2.92z",
        )
    }
    val MoreVert by lazy {
        icon(
            "MoreVert",
            "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z",
        )
    }
    val Search by lazy {
        icon(
            "Search",
            "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z",
        )
    }
    val FolderOpen by lazy {
        icon(
            "FolderOpen",
            "M20,6h-8l-2,-2H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2zM20,18H4V8h16v10z",
        )
    }
    val TextFields by lazy {
        icon("TextFields", "M2.5,4v3h5v12h3V7h5V4h-13zM21.5,9h-9v3h3v7h3v-7h3V9z")
    }
    val EditText by lazy {
        icon("EditText", "M4,6h12v2H4zM4,11h8v2H4zM4,16h6v2H4zM19.3,10.5l1.4,1.4 -6.2,6.2 -2.1,0.7 0.7,-2.1z")
    }
    val Highlight by lazy {
        icon(
            "Highlight",
            "M6,14l3,3v5h6v-5l3,-3V9H6zM11,2h2v3h-2zM3.5,5.88l1.41,-1.41 2.12,2.12 -1.41,1.41zM16.96,6.59l2.12,-2.12 1.41,1.41 -2.12,2.12z",
        )
    }
    val Brush by lazy {
        icon(
            "Brush",
            "M7,14c-1.66,0 -3,1.34 -3,3 0,1.31 -1.16,2 -2,2 0.92,1.22 2.49,2 4,2 2.21,0 4,-1.79 4,-4 0,-1.66 -1.34,-3 -3,-3zM20.71,4.63l-1.34,-1.34c-0.39,-0.39 -1.02,-0.39 -1.41,0L9,12.25 11.75,15l8.96,-8.96c0.39,-0.39 0.39,-1.02 0,-1.41z",
        )
    }
}
