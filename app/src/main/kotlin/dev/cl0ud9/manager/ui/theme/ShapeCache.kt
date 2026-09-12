package dev.cl0ud9.manager.ui.theme

import androidx.compose.ui.unit.dp
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

// squircle corners for the expressive look, cached to avoid recomputing the path per composition -
// scale matches PixelPlayer's own ShapeCache (same library, same 60% smoothness), extended with
// smoothPill for the floating nav bar and pill-shaped buttons
object ShapeCache {
    val smooth8 = AbsoluteSmoothCornerShape(cornerRadius = 8.dp, smoothnessAsPercent = 60)
    val smooth10 = AbsoluteSmoothCornerShape(cornerRadius = 10.dp, smoothnessAsPercent = 60)
    val smooth12 = AbsoluteSmoothCornerShape(cornerRadius = 12.dp, smoothnessAsPercent = 60)
    val smooth14 = AbsoluteSmoothCornerShape(cornerRadius = 14.dp, smoothnessAsPercent = 60)
    val smooth16 = AbsoluteSmoothCornerShape(cornerRadius = 16.dp, smoothnessAsPercent = 60)
    val smooth20 = AbsoluteSmoothCornerShape(cornerRadius = 20.dp, smoothnessAsPercent = 60)
    val smooth24 = AbsoluteSmoothCornerShape(cornerRadius = 24.dp, smoothnessAsPercent = 60)
    val smooth28 = AbsoluteSmoothCornerShape(cornerRadius = 28.dp, smoothnessAsPercent = 60)
    val smooth32 = AbsoluteSmoothCornerShape(cornerRadius = 32.dp, smoothnessAsPercent = 60)
    val smoothPill = AbsoluteSmoothCornerShape(cornerRadius = 50.dp, smoothnessAsPercent = 60)
}
