package com.notely.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Real bitmap operations used by image insert / rotate / crop / compress. */
object ImageOps {

    /** Decodes with downsampling so large camera photos never blow the heap on 3 GB devices. */
    fun decode(path: String?, maxDim: Int = 2000): Bitmap? {
        if (path == null || maxDim <= 0) return null
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

    private fun directory(context: Context, name: String): File {
        require(name in setOf("images", "drawings", "attachments")) { "Invalid storage directory" }
        return File(context.filesDir, name).apply { check(isDirectory || mkdirs()) }
    }

    fun save(context: Context, bmp: Bitmap, dirName: String = "images"): String {
        val file = File(directory(context, dirName), "img_${UUID.randomUUID()}.jpg")
        PrivateFiles.atomicWrite(file) { check(bmp.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
        return file.absolutePath
    }

    fun savePng(context: Context, bmp: Bitmap, dirName: String = "images"): String {
        val file = File(directory(context, dirName), "img_${UUID.randomUUID()}.png")
        PrivateFiles.atomicWrite(file) { check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        return file.absolutePath
    }

    /** Stage a bounded stream, read bounds first, then decode a sampled bitmap (never the full photo). */
    fun import(context: Context, uri: android.net.Uri): String? {
        var staging: File? = null
        var bitmap: Bitmap? = null
        return try {
            val mime = context.contentResolver.getType(uri)
            require(mime == null || mime.startsWith("image/")) { "Not an image" }
            val staged = context.contentResolver.openInputStream(uri)?.use { input ->
                PrivateFiles.copy(input, context.cacheDir, "image-import", 50L * 1024 * 1024)
            } ?: return null
            staging = staged
            val decoded = decode(staged.absolutePath) ?: return null
            bitmap = decoded
            save(context, decoded)
        } catch (_: Exception) {
            null
        } finally {
            bitmap?.recycle()
            staging?.delete()
        }
    }

    /** Unique names prevent a second attachment with the same display name destroying the first. */
    fun importBytes(context: Context, uri: android.net.Uri, dirName: String = "attachments"): String? = runCatching {
        val dir = directory(context, dirName)
        val name = queryName(context, uri) ?: "attachment"
        context.contentResolver.openInputStream(uri)?.use { input ->
            PrivateFiles.copy(input, dir, name).absolutePath
        }
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
