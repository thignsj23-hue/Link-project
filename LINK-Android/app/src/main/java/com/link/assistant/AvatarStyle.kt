package com.link.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/** Creates a small posterized, outlined portrait so a user photo is presented as 2D art. */
object AvatarStyle {
    fun stylizeAndSave(context: Context, uri: Uri): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "Não foi possível abrir a imagem." }
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Formato de imagem não reconhecido." }
        var sample = 1
        while (bounds.outWidth / sample > 900 || bounds.outHeight / sample > 900) sample *= 2
        val input = context.contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "Não foi possível abrir a imagem." }
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Formato de imagem não reconhecido.")
        val side = 420
        val scale = minOf(side.toFloat() / input.width, side.toFloat() / input.height)
        val w = (input.width * scale).toInt().coerceAtLeast(1)
        val h = (input.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(input, w, h, true)
        if (scaled !== input) input.recycle()
        val source = IntArray(w * h)
        scaled.getPixels(source, 0, w, 0, 0, w, h)
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val output = IntArray(source.size)
        val step = 48
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val c = source[i]
            val right = source[y * w + minOf(x + 1, w - 1)]
            val below = source[minOf(y + 1, h - 1) * w + x]
            val edge = colorDistance(c, right) + colorDistance(c, below) > 150
            fun poster(v: Int) = ((v / step) * step + step / 2).coerceIn(0, 255)
            output[i] = if (edge) Color.rgb(20, 24, 40) else Color.rgb(poster(Color.red(c)), poster(Color.green(c)), poster(Color.blue(c)))
        }
        result.setPixels(output, 0, w, 0, 0, w, h)
        scaled.recycle()
        val file = File(context.filesDir, "link-avatar-2d.png")
        FileOutputStream(file).use { result.compress(Bitmap.CompressFormat.PNG, 100, it) }
        result.recycle()
        return file
    }

    private fun colorDistance(a: Int, b: Int): Int =
        kotlin.math.abs(Color.red(a) - Color.red(b)) +
            kotlin.math.abs(Color.green(a) - Color.green(b)) +
            kotlin.math.abs(Color.blue(a) - Color.blue(b))
}
