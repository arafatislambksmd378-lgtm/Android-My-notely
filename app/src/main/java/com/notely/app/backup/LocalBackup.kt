package com.notely.app.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.room.withTransaction
import com.notely.app.data.db.NotelyDatabase
import com.notely.app.doc.DocumentSerializer
import com.notely.app.recovery.DocumentRecovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.*

/** Versioned, bounded logical backup. Restore is always copy-only, never replacement. No secrets/settings/models. */
class LocalBackup(private val context: Context, private val db: NotelyDatabase) {
    companion object {
        val TABLES = listOf("notebooks", "notes", "tags", "note_tags", "comments", "bookmarks", "footnotes", "versions", "templates", "reminders", "recovery_drafts")
        private const val LIMIT = 512L * 1024 * 1024
        private const val JSON_LIMIT = 32L * 1024 * 1024
        private fun hash(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val b = ByteArray(65536)
                while (true) { val n = input.read(b); if (n < 0) break; digest.update(b, 0, n) }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    private fun rows(table: String): JSONArray {
        require(table in TABLES)
        val out = JSONArray()
        db.openHelper.writableDatabase.query("SELECT * FROM `$table`").use { cursor ->
            while (cursor.moveToNext()) {
                val row = JSONObject()
                cursor.columnNames.forEachIndexed { i, name ->
                    row.put(name, when (cursor.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                        Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                        else -> error("Unsupported binary database column")
                    })
                }
                out.put(row)
            }
        }
        return out
    }

    private fun documents(data: JSONObject, visit: (JSONObject) -> Unit) {
        listOf("notes", "versions", "templates", "recovery_drafts").forEach { table ->
            val array = data.getJSONArray(table)
            for (i in 0 until array.length()) visit(array.getJSONObject(i))
        }
    }

    /** Mutate only media-path fields, never replace arbitrary prose that resembles a path. */
    private fun media(json: String, transform: (String) -> String): String {
        if (json.isBlank()) return json
        DocumentSerializer.deserializeForEditing(json)
        val root = JSONObject(json)
        val blocks = root.getJSONArray("blocks")
        for (i in 0 until blocks.length()) {
            val block = blocks.getJSONObject(i)
            listOf("img", "draw").forEach { key ->
                if (!block.isNull(key) && block.optString(key).isNotBlank()) block.put(key, transform(block.getString(key)))
            }
            if (block.getString("type") in listOf("ATTACHMENT", "AUDIO", "VIDEO") && block.optString("ex").isNotBlank())
                block.put("ex", transform(block.getString("ex")))
        }
        return root.toString()
    }

    suspend fun export(output: OutputStream, progress: suspend (String) -> Unit = {}) = withContext(Dispatchers.IO) {
        val work = File(context.cacheDir, "backup_${UUID.randomUUID()}").apply { check(mkdirs()) }
        try {
            progress("Reading a consistent database snapshot…")
            val data = db.withTransaction { JSONObject().also { root -> TABLES.forEach { root.put(it, rows(it)) } } }
            val entries = JSONArray()
            val assets = JSONObject()
            val sources = linkedMapOf<String, File>()
            documents(data) { row ->
                media(row.getString("documentJson")) { path ->
                    val file = File(path).canonicalFile
                    require(listOf("images", "drawings", "attachments").any { name ->
                        file.path.startsWith(File(context.filesDir, name).canonicalPath + File.separator)
                    }) { "Document references a file outside Notely private media" }
                    require(file.isFile) { "A referenced attachment is missing; backup was not completed" }
                    val sum = hash(file)
                    val extension = file.extension.lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,10}")) } ?: "bin"
                    val name = "media/$sum.$extension"
                    assets.put(path, name)
                    sources[name] = file
                    path
                }
            }
            val database = File(work, "database.json").apply { writeText(data.toString(), Charsets.UTF_8) }
            require(database.length() <= JSON_LIMIT) { "Database exceeds the 32 MiB backup metadata limit" }
            sources["database.json"] = database
            require(sources.values.sumOf { it.length() } <= LIMIT) { "Backup exceeds the 512 MiB safety limit" }
            val archive = File(work, "backup.notely.zip")
            ZipOutputStream(BufferedOutputStream(archive.outputStream())).use { zip ->
                sources.forEach { (name, file) ->
                    progress("Backing up ${entries.length() + 1}/${sources.size} files…")
                    entries.put(JSONObject().put("path", name).put("size", file.length()).put("sha256", hash(file)))
                    zip.putNextEntry(ZipEntry(name)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
                val manifest = JSONObject().put("format", "notely-local-backup").put("version", 1)
                    .put("databaseVersion", 3).put("createdAt", System.currentTimeMillis()).put("entries", entries).put("assets", assets)
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toString().toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            progress("Writing backup to the selected location…")
            archive.inputStream().use { it.copyTo(output) }; output.flush()
        } finally { work.deleteRecursively() }
    }

    private fun bounded(input: InputStream, output: OutputStream, max: Long): Long {
        var count = 0L
        val b = ByteArray(65536)
        while (true) {
            val n = input.read(b); if (n < 0) break
            count += n; require(count <= max) { "Archive exceeds the size limit" }; output.write(b, 0, n)
        }
        return count
    }

    /** Stage, validate completely, then insert remapped records in one transaction. */
    suspend fun restore(input: InputStream, progress: suspend (String) -> Unit = {}): Int = withContext(Dispatchers.IO) {
        val work = File(context.cacheDir, "restore_${UUID.randomUUID()}").apply { check(mkdirs()) }
        val destination = File(context.filesDir, "attachments/restored_${UUID.randomUUID()}")
        var committed = false
        try {
            progress("Reading backup (maximum 512 MiB)…")
            val archive = File(work, "input.zip")
            archive.outputStream().use { bounded(input, it, LIMIT) }
            val data: JSONObject
            val manifest: JSONObject
            val staged = mutableMapOf<String, File>()
            ZipFile(archive).use { zip ->
                val names = mutableSetOf<String>()
                val enumeration = zip.entries()
                while (enumeration.hasMoreElements()) {
                    val e = enumeration.nextElement()
                    require(names.size < 10000 && names.add(e.name)) { "Duplicate or excessive archive entries" }
                    require(!e.isDirectory && (e.name == "manifest.json" || e.name == "database.json" ||
                        e.name.matches(Regex("media/[a-f0-9]{64}\\.[a-z0-9]{1,10}")))) { "Unsafe archive path" }
                }
                fun text(name: String, max: Long): String {
                    val e = zip.getEntry(name) ?: error("Required backup entry missing")
                    val bytes = ByteArrayOutputStream()
                    zip.getInputStream(e).use { bounded(it, bytes, max) }
                    return bytes.toString("UTF-8")
                }
                manifest = JSONObject(text("manifest.json", 2L * 1024 * 1024))
                require(manifest.getString("format") == "notely-local-backup" && manifest.getInt("version") == 1 &&
                    manifest.getInt("databaseVersion") == 3) { "Incompatible backup version" }
                val entries = manifest.getJSONArray("entries")
                val expected = mutableSetOf("manifest.json")
                var total = 0L
                for (i in 0 until entries.length()) {
                    val record = entries.getJSONObject(i)
                    val name = record.getString("path")
                    require(name in names && name != "manifest.json" && expected.add(name)) { "Invalid manifest entries" }
                    val size = record.getLong("size")
                    require(size in 0..LIMIT) { "Invalid entry size" }
                    total += size; require(total <= LIMIT) { "Expanded backup exceeds safety limit" }
                    require(context.cacheDir.usableSpace > size + 16L * 1024 * 1024) { "Insufficient free storage" }
                    val file = File(work, "verified_$i")
                    zip.getInputStream(zip.getEntry(name)).use { source -> file.outputStream().use { bounded(source, it, minOf(size, if (name == "database.json") JSON_LIMIT else LIMIT)) } }
                    require(file.length() == size && hash(file) == record.getString("sha256")) { "Backup integrity check failed" }
                    staged[name] = file
                    progress("Verified ${i + 1}/${entries.length()} files…")
                }
                require(names == expected) { "Unlisted files in backup" }
                data = JSONObject((staged["database.json"] ?: error("Database missing")).readText(Charsets.UTF_8))
            }
            require(data.keys().asSequence().toSet() == TABLES.toSet()) { "Backup database tables are incompatible" }
            val assetMap = manifest.getJSONObject("assets")
            val rewritten = mutableMapOf<String, String>()
            require(destination.mkdirs()) { "Cannot create restore storage" }
            assetMap.keys().forEach { original ->
                val name = assetMap.getString(original)
                require(name.startsWith("media/")) { "Invalid asset mapping" }
                val source = staged[name] ?: error("Attachment missing from backup")
                val target = File(destination, name.substringAfter('/'))
                if (!target.exists()) {
                    require(context.filesDir.usableSpace > source.length() + 16L * 1024 * 1024) { "Insufficient restore storage" }
                    source.inputStream().use { src -> FileOutputStream(target).use { out -> src.copyTo(out); out.fd.sync() } }
                }
                rewritten[original] = target.absolutePath
            }
            documents(data) { row -> row.put("documentJson", media(row.getString("documentJson")) { path ->
                rewritten[path] ?: error("Document asset is not listed in manifest")
            }) }
            progress("Restoring as new notebooks and documents…")
            val count = withContext(NonCancellable) {
                db.withTransaction { insertCopies(data) }.also { committed = true }
            }
            progress("Restored $count documents. Existing data was not overwritten.")
            count
        } finally {
            work.deleteRecursively()
            if (!committed) destination.deleteRecursively()
        }
    }

    private fun insertCopies(data: JSONObject): Int {
        val sql = db.openHelper.writableDatabase
        val maps = mutableMapOf<String, MutableMap<Long, Long>>()
        fun mapped(table: String, id: Long) = maps[table]?.get(id) ?: error("Broken backup relationship: $table")
        TABLES.forEach { table ->
            val columns = sql.query("PRAGMA table_info(`$table`)").use { c -> buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) } }
            val array = data.getJSONArray(table)
            val ids = mutableMapOf<Long, Long>()
            val crossRefs = mutableSetOf<Pair<Long, Long>>()
            maps[table] = ids
            for (i in 0 until array.length()) {
                val row = array.getJSONObject(i)
                require(row.keys().asSequence().toSet() == columns) { "Incompatible backup columns: $table" }
                val original = if (table == "note_tags") 0L else row.getLong(if (table == "recovery_drafts") "noteId" else "id")
                if (table != "note_tags") require(original > 0 && !ids.containsKey(original)) { "Duplicate identifier in backup" }
                if (table == "tags") {
                    sql.query("SELECT id FROM tags WHERE name = ?", arrayOf(row.getString("name"))).use { c ->
                        if (c.moveToFirst()) ids[original] = c.getLong(0)
                    }
                    if (ids.containsKey(original)) continue
                }
                val values = ContentValues()
                columns.forEach { column ->
                    if (column != "id") {
                        val value = row.get(column)
                        when (value) {
                            JSONObject.NULL -> values.putNull(column)
                            is Number -> values.put(column, value.toLong())
                            is String -> values.put(column, value)
                            else -> error("Invalid backup value")
                        }
                    }
                }
                if (table == "notes") values.put("notebookId", mapped("notebooks", row.getLong("notebookId")))
                if ("noteId" in columns) values.put("noteId", mapped("notes", row.getLong("noteId")))
                if (table == "note_tags") {
                    values.put("tagId", mapped("tags", row.getLong("tagId")))
                    require(crossRefs.add(row.getLong("noteId") to row.getLong("tagId"))) { "Duplicate tag assignment" }
                }
                if (table == "recovery_drafts") values.put("sha256", DocumentRecovery.digest(row.getString("title"), row.getString("documentJson")))
                // Reminder metadata is preserved; the existing scheduler may rearm future alarms on next launch.
                val id = sql.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, values)
                require(id > 0) { "Could not restore database record" }
                ids[original] = id
            }
        }
        return data.getJSONArray("notes").length()
    }
}
