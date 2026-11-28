package com.notely.app.util

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

/** Bounded, atomic private-file writes; failed imports never expose partial final files. */
object PrivateFiles {
    const val MAX_ATTACHMENT_BYTES = 100L * 1024 * 1024

    fun copy(input: InputStream, directory: File, displayName: String, maxBytes: Long = MAX_ATTACHMENT_BYTES): File {
        require(maxBytes > 0)
        check(directory.isDirectory || directory.mkdirs()) { "Storage is unavailable" }
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(100).ifBlank { "attachment" }
        val target = File(directory, "${UUID.randomUUID()}_$safeName")
        return atomicWrite(target) { output ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= maxBytes) { "File exceeds the size limit" }
                output.write(buffer, 0, count)
            }
            require(total > 0) { "File is empty" }
        }
    }

    fun atomicWrite(target: File, write: (FileOutputStream) -> Unit): File {
        require(!target.exists()) { "Refusing to overwrite an existing file" }
        val partial = File(target.parentFile, ".${target.name}.${UUID.randomUUID()}.part")
        try {
            FileOutputStream(partial).use { output ->
                write(output)
                output.flush()
                output.fd.sync()
            }
            check(partial.renameTo(target)) { "Could not finalize file" }
            return target
        } finally { partial.delete() }
    }
}
