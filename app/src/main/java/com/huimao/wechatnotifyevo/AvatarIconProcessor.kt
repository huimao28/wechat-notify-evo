package com.huimao.wechatnotifyevo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import kotlin.math.ceil
import kotlin.math.floor

/** Safely converts a captured WeChat large icon into a bounded transparent circle. */
internal object AvatarIconProcessor {
    private const val TAG = "WNE.Avatar"
    private const val MAX_SOURCE_DIMENSION = 8192
    private const val MAX_SOURCE_PIXELS = 16_777_216L

    /** Returns null on any unsupported or unsafe source so callers can retain the original Icon. */
    fun circularize(context: Context, source: Icon): Icon? {
        // Icon type/data inspection only became public in API 28. On older systems
        // falling back avoids accidentally dereferencing an opaque URI icon.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return try {
            val drawable = when (source.type) {
                Icon.TYPE_BITMAP, Icon.TYPE_ADAPTIVE_BITMAP, Icon.TYPE_RESOURCE ->
                    source.loadDrawable(context)
                // Opaque DATA cannot be bounds-probed through public Icon APIs before
                // decoding, so do not risk a compressed image expanding without limit.
                Icon.TYPE_DATA -> null
                // Never open Icon URIs here: they may require foreign grants or trigger
                // provider/network work outside notification processing's authority.
                Icon.TYPE_URI, Icon.TYPE_URI_ADAPTIVE_BITMAP -> null
                else -> null
            } ?: return null
            renderCircular(drawable)?.let(Icon::createWithBitmap)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Avatar conversion failed; keeping original Icon", error)
            null
        }
    }

    private fun safeDimensions(width: Int, height: Int): Boolean =
        width in 1..MAX_SOURCE_DIMENSION && height in 1..MAX_SOURCE_DIMENSION &&
            width.toLong() * height <= MAX_SOURCE_PIXELS

    private fun renderCircular(drawable: Drawable): Bitmap? {
        val sourceWidth = drawableSourceWidth(drawable)
        val sourceHeight = drawableSourceHeight(drawable)
        if (sourceWidth > 0 && sourceHeight > 0 && !safeDimensions(sourceWidth, sourceHeight)) {
            return null
        }
        val target = circularAvatarOutputSize(sourceWidth, sourceHeight)
        val effectiveWidth = sourceWidth.takeIf { it > 0 } ?: target
        val effectiveHeight = sourceHeight.takeIf { it > 0 } ?: target
        val crop = centerCropGeometry(effectiveWidth, effectiveHeight, target)

        // Both temporary allocations are fixed at <= 256x256 ARGB_8888 (~256 KiB each).
        val square = Bitmap.createBitmap(target, target, Bitmap.Config.ARGB_8888)
        square.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(square)
        val oldBounds = Rect(drawable.bounds)
        try {
            drawable.mutate().bounds = Rect(
                floor(crop.left).toInt(),
                floor(crop.top).toInt(),
                ceil(crop.left + crop.width).toInt(),
                ceil(crop.top + crop.height).toInt(),
            )
            drawable.draw(canvas)
        } finally {
            drawable.bounds = oldBounds
        }

        val output = Bitmap.createBitmap(target, target, Bitmap.Config.ARGB_8888)
        output.eraseColor(Color.TRANSPARENT)
        val shader = BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.shader = shader
        }
        Canvas(output).drawCircle(target / 2f, target / 2f, target / 2f, paint)
        square.recycle()
        return output
    }

    private fun drawableSourceWidth(drawable: Drawable): Int = when (drawable) {
        is BitmapDrawable -> drawable.bitmap?.width ?: drawable.intrinsicWidth
        else -> drawable.intrinsicWidth
    }

    private fun drawableSourceHeight(drawable: Drawable): Int = when (drawable) {
        is BitmapDrawable -> drawable.bitmap?.height ?: drawable.intrinsicHeight
        else -> drawable.intrinsicHeight
    }
}
