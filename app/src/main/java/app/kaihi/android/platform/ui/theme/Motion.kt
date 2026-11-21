package app.kaihi.android.platform.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring

// Material 3's standard motion scheme, spelled out because material3 1.4 keeps MotionScheme internal:

private const val SPATIAL_DAMPING_RATIO = 0.9f
private const val SPATIAL_STIFFNESS = 700f
private const val EFFECTS_DAMPING_RATIO = 1f
private const val EFFECTS_STIFFNESS = 1600f

internal fun <T> kaihiSpatialSpec(): FiniteAnimationSpec<T> =
    spring(dampingRatio = SPATIAL_DAMPING_RATIO, stiffness = SPATIAL_STIFFNESS)

internal fun <T> kaihiEffectsSpec(): FiniteAnimationSpec<T> =
    spring(dampingRatio = EFFECTS_DAMPING_RATIO, stiffness = EFFECTS_STIFFNESS)
