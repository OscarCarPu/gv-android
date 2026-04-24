package com.gv.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val GvShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
)
