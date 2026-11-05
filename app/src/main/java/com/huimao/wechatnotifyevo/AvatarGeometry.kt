package com.huimao.wechatnotifyevo

import kotlin.math.max
import kotlin.math.min

/** Pure sizing/cropping policy shared by avatar rendering and unit tests. */
internal object CircularAvatarPolicy {
    const val DEFAULT_ENABLED = true
    const val MAX_OUTPUT_SIZE_PX = 256

    fun useProcessed(enabled: Boolean, processedAvailable: Boolean): Boolean =
        enabled && processedAvailable
}

internal data class CenterCropGeometry(
    val scale: Float,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * Keeps output allocations bounded and never enlarges a source with known dimensions.
 * Using the shorter edge also makes the center-crop behavior explicit for rectangles.
 */
internal fun circularAvatarOutputSize(
    sourceWidth: Int,
    sourceHeight: Int,
    maximum: Int = CircularAvatarPolicy.MAX_OUTPUT_SIZE_PX,
): Int {
    require(maximum > 0)
    if (sourceWidth <= 0 || sourceHeight <= 0) return maximum
    return min(maximum, min(sourceWidth, sourceHeight)).coerceAtLeast(1)
}

/** Scale-to-fill geometry; overflow is cropped equally from opposite edges. */
internal fun centerCropGeometry(
    sourceWidth: Int,
    sourceHeight: Int,
    targetSize: Int,
): CenterCropGeometry {
    require(sourceWidth > 0 && sourceHeight > 0 && targetSize > 0)
    val scale = max(targetSize.toFloat() / sourceWidth, targetSize.toFloat() / sourceHeight)
    val width = sourceWidth * scale
    val height = sourceHeight * scale
    return CenterCropGeometry(
        scale = scale,
        left = (targetSize - width) / 2f,
        top = (targetSize - height) / 2f,
        width = width,
        height = height,
    )
}
