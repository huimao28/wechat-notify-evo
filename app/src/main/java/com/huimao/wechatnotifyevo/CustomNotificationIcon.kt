package com.huimao.wechatnotifyevo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.net.Uri
import android.util.AtomicFile
import java.io.ByteArrayOutputStream

/** Copies picker content into a bounded, private, monochrome notification icon. */
object CustomNotificationIcon {
    private const val MAX_BYTES = 10 * 1024 * 1024
    private const val MAX_DIMENSION = 8192
    private const val SIZE = 192
    private fun file(context: Context) = AtomicFile(context.getFileStreamPath("notification_icon.png"))

    fun import(context: Context, uri: Uri): Boolean {
        if (uri.scheme != "content") return false
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16384)
                var total = 0
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) return false
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            } ?: return false
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION ||
                width.toLong() * height > 32_000_000L) return false
            var sample = 1
            while (maxOf(width, height) / sample > SIZE * 2) sample *= 2
            val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
            val scaled = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val scale = minOf(SIZE.toFloat() / source.width, SIZE.toFloat() / source.height)
            val w = source.width * scale
            val h = source.height * scale
            Canvas(scaled).drawBitmap(source, null,
                RectF((SIZE - w) / 2, (SIZE - h) / 2, (SIZE + w) / 2, (SIZE + h) / 2),
                Paint(Paint.FILTER_BITMAP_FLAG))
            source.recycle()
            val pixels = IntArray(SIZE * SIZE)
            scaled.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
            // For transparent artwork preserve alpha; for opaque artwork use luminance
            // as a mask (white = foreground, black = transparent).
            val left = ((SIZE - w) / 2).toInt().coerceAtLeast(0)
            val top = ((SIZE - h) / 2).toInt().coerceAtLeast(0)
            val right = ((SIZE + w) / 2).toInt().coerceAtMost(SIZE)
            val bottom = ((SIZE + h) / 2).toInt().coerceAtMost(SIZE)
            var hasTransparency = false
            for (y in top until bottom) for (x in left until right) {
                if (Color.alpha(pixels[y * SIZE + x]) < 250) hasTransparency = true
            }
            var visible = false
            for (i in pixels.indices) {
                val p = pixels[i]
                val alpha = if (hasTransparency) Color.alpha(p) else
                    (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
                if (alpha > 0) visible = true
                pixels[i] = Color.argb(alpha, 255, 255, 255)
            }
            if (!visible) { scaled.recycle(); return false }
            scaled.setPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
            val atomic = file(context)
            val output = atomic.startWrite()
            try {
                if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, output)) error("PNG encode failed")
                atomic.finishWrite(output)
            } catch (e: Exception) {
                atomic.failWrite(output)
                throw e
            } finally { scaled.recycle() }
            true
        } catch (_: Exception) { false } catch (_: OutOfMemoryError) { false }
    }

    fun load(context: Context): Icon? = try {
        file(context).openRead().use { input ->
            BitmapFactory.decodeStream(input)?.let { Icon.createWithBitmap(it) }
        }
    } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }
}
