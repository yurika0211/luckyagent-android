package com.luckyagent.android.data.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object AppearanceStore {
    const val BACKGROUND_FILE = "chat-background.jpg"
    const val AVATAR_FILE = "avatar.jpg"

    fun file(context: Context, name: String): File = File(context.filesDir, name)

    fun importImage(context: Context, source: Uri, name: String, maxEdge: Int): Boolean {
        val bitmap = decode(context, source, maxEdge) ?: return false
        val scaled = scaleDown(bitmap, maxEdge)
        if (scaled !== bitmap) bitmap.recycle()
        val target = file(context, name)
        val temp = File(target.parentFile, "$name.tmp")
        return try {
            FileOutputStream(temp).use { out ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)) return false
            }
            if (target.exists() && !target.delete()) return false
            temp.renameTo(target)
        } finally {
            scaled.recycle()
            if (temp.exists()) temp.delete()
        }
    }

    private fun decode(context: Context, source: Uri, maxEdge: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            val decoded = runCatching {
                val decoderSource = ImageDecoder.createSource(context.contentResolver, source)
                ImageDecoder.decodeBitmap(decoderSource) { decoder, info, _ ->
                    val edge = max(info.size.width, info.size.height)
                    if (edge > maxEdge) {
                        val scale = maxEdge.toFloat() / edge
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1),
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }.getOrNull()
            if (decoded != null) return decoded
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        return context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    fun clear(context: Context, name: String) {
        file(context, name).delete()
    }

    private fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        while (max(width, height) / sample > maxEdge * 2) sample *= 2
        return sample
    }

    private fun scaleDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val edge = max(bitmap.width, bitmap.height)
        if (edge <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / edge
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }
}
