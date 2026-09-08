package com.samco.trackandgraph.widgets

import kotlin.math.sqrt

/** Bound IPC bitmap size independently of the phone's density or launcher-supplied dimensions. */
internal fun graphWidgetBitmapSize(widthDp: Int, heightDp: Int, density: Float): Pair<Int, Int> {
    val scale = if (density.isFinite()) density.coerceIn(1f, 2f) else 1f
    val width = widthDp.coerceIn(120, 800) * scale
    val height = (heightDp.coerceIn(116, 656) - 56) * scale
    val reduction = sqrt((160_000f / (width * height)).coerceAtMost(1f))
    return (width * reduction).toInt().coerceAtLeast(1) to
        (height * reduction).toInt().coerceAtLeast(1)
}
