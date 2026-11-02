package com.tasknext.app.ui

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** Position and velocity use page units, increasing towards History. */
internal fun hubSwipeTarget(
    position: Float,
    startPage: Int,
    velocity: Float,
    minimumFlingVelocity: Float
): Int {
    val target = when {
        abs(velocity) >= minimumFlingVelocity ->
            if (velocity > 0f) ceil(position).toInt() else floor(position).toInt()
        position - startPage >= 0.24f -> ceil(position).toInt()
        position - startPage <= -0.24f -> floor(position).toInt()
        else -> startPage
    }
    return target.coerceIn((startPage - 1).coerceAtLeast(0), (startPage + 1).coerceAtMost(2))
}
