package com.notely.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.File
import java.io.FileOutputStream

/** Real bitmap operations used by image insert / rotate / crop / compress. */
object ImageOps {

    /** Decodes with downsampling so large camera photos never blow the heap on 3 GB devices. */
    fun decode(path: String?, maxDim: Int = 2000): Bitmap? {
        if (path == null) return null
        val file = File(path)
        if (!file.exists()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(path, opts)
        }.getOrNull()
    }

    fun save(context: Context, bmp: Bitmap, dirName: String = "images"): String {
        val dir = File(context.filesDir, dirName).apply { mkdirs() }
        val file = File(dir, "img_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        return file.absolutePath
    }

    fun savePng(context: Context, bmp: Bitmap, dirName: String = "images"): String {
        val dir = File(context.filesDir, dirName).apply { mkdirs() }
        val file = File(dir, "img_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return file.absolutePath
    }

    /** Copies an external image URI into app-private storage (no persisted URI permission needed). */
    /**
     * Copies an external image URI into app-private storage, always transcoding
     * to JPEG. Transcoding matters for two reasons: a picked PNG/HEIC/WebP would
     * otherwise be stored under a `.jpg` name (so DOCX/ODT/PDF readers see the
     * wrong container type), and huge camera photos are downscaled here.
     */
    fun import(context: Context, uri: android.net.Uri): String? {
        val dest = File(File(context.filesDir, "images").apply { mkdirs() }, "img_${System.currentTimeMillis()}.jpg")
        return runCatching {
            val bmp = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return@runCatching null
            val maxDim = 2000
            val scale = minOf(1f, maxDim.toFloat() / maxOf(bmp.width, bmp.height))
            val outBmp = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * scale).toInt().coerceAtLeast(1),
                    (bmp.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else bmp
            FileOutputStream(dest).use { outBmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            if (outBmp !== bmp) outBmp.recycle()
            bmp.recycle()
            dest.absolutePath
        }.getOrNull()
    }

    fun importBytes(context: Context, uri: android.net.Uri, dirName: String = "attachments"): String? = runCatching {
        val dir = File(context.filesDir, dirName).apply { mkdirs() }
        val name = queryName(context, uri) ?: "att_${System.currentTimeMillis()}"
        val safe = name.replace(Regex("[^\\w.\\-]"), "_")
        val dest = File(dir, safe)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest.absolutePath
    }.getOrNull()

    fun queryName(context: Context, uri: android.net.Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()

    fun rotate(context: Context, path: String, degrees: Int): String? {
        val bmp = decode(path) ?: return null
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        val out = save(context, rotated)
        if (rotated !== bmp) rotated.recycle()
        bmp.recycle()
        return out
    }

    fun crop(context: Context, path: String, left: Int, top: Int, width: Int, height: Int): String? {
        val bmp = decode(path) ?: return null
        val l = left.coerceIn(0, bmp.width - 1)
        val t = top.coerceIn(0, bmp.height - 1)
        val w = width.coerceIn(1, bmp.width - l)
        val h = height.coerceIn(1, bmp.height - t)
        val cropped = Bitmap.createBitmap(bmp, l, t, w, h)
        val out = save(context, cropped)
        if (cropped !== bmp) cropped.recycle()
        bmp.recycle()
        return out
    }

    fun mimeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "txt" -> "text/plain"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }
}
