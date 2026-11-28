package com.notely.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notely.app.backup.LocalBackup
import com.notely.app.data.db.*
import com.notely.app.doc.*
import com.notely.app.recovery.DocumentRecovery
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.*

@RunWith(AndroidJUnit4::class)
class BackupRestoreInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun database() = Room.inMemoryDatabaseBuilder(context, NotelyDatabase::class.java).build()
    private fun count(db: NotelyDatabase, table: String): Int = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    @Test fun fullBackupDeleteRestoreAndConflictCopiesPreserveRecordsAndBinaries() = runBlocking {
        val db = database()
        val created = mutableSetOf<File>()
        try {
            val fixtures = (1..2).map { index ->
                val nb = db.notebookDao().insert(NotebookEntity(name = "Notebook $index"))
                val (blocks, page) = ComplexFixture.create(context)
                created += File(blocks.first { it.imagePath != null }.imagePath!!).parentFile!!
                val json = DocumentSerializer.serialize(blocks, page)
                val id = db.noteDao().insert(NoteEntity(notebookId = nb, title = "Doc $index", documentJson = json, pinned = true, cardColorIndex = index))
                db.commentDao().insert(CommentEntity(noteId = id, text = "Comment", resolved = true))
                db.bookmarkDao().insert(BookmarkEntity(noteId = id, name = "Body", blockId = "body"))
                db.footnoteDao().insert(FootnoteEntity(noteId = id, refNumber = 1, text = "Footnote"))
                db.versionDao().insert(VersionEntity(noteId = id, documentJson = json))
                db.templateDao().insert(TemplateEntity(name = "Template $index", documentJson = json))
                val tag = db.tagDao().insert(TagEntity(name = "tag$index")); db.tagDao().link(NoteTagCrossRef(id, tag))
                db.reminderDao().insert(ReminderEntity(noteId = id, atMillis = 1, label = "Old reminder"))
                db.recoveryDao().put(RecoveryDraftEntity(id, "Doc $index", json, DocumentRecovery.digest("Doc $index", json), db.noteDao().getById(id)!!.updatedAt, 1))
                blocks to page
            }
            val service = LocalBackup(context, db)
            val backup = ByteArrayOutputStream().also { service.export(it) }.toByteArray()
            // Only isolated in-memory test database and fixture files are deleted, never app.db.
            LocalBackup.TABLES.reversed().forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM `$it`") }
            fixtures.forEach { (blocks, _) -> File(blocks.first { it.imagePath != null }.imagePath!!).parentFile!!.deleteRecursively() }
            assertEquals(2, service.restore(backup.inputStream()))
            val restored = db.noteDao().observeActive().first().sortedBy { it.title }
            assertEquals(2, restored.size)
            restored.forEachIndexed { index, note ->
                val (blocks, page) = DocumentSerializer.deserializeForEditing(note.documentJson)
                val (expected, expectedPage) = fixtures[index]
                val normalized = blocks.mapIndexed { i, block ->
                    val old = expected[i]
                    listOfNotNull(block.imagePath, block.drawingPath,
                        block.extra.takeIf { block.type == BlockType.ATTACHMENT }).forEach { path ->
                        assertTrue(File(path).isFile); created += File(path).parentFile!!
                    }
                    if (block.type == BlockType.ATTACHMENT) assertEquals("private test attachment বাংলা", File(block.extra).readText())
                    block.copy(imagePath = old.imagePath, drawingPath = old.drawingPath, extra = old.extra)
                }
                assertEquals(expected, normalized); assertEquals(expectedPage, page)
                assertTrue(note.pinned); assertEquals(index + 1, note.cardColorIndex)
                assertEquals(1, db.commentDao().observeForNote(note.id).first().size)
                assertTrue(db.commentDao().observeForNote(note.id).first().single().resolved)
                assertEquals("body", db.bookmarkDao().observeForNote(note.id).first().single().blockId)
                assertEquals(1, db.footnoteDao().observeForNote(note.id).first().size)
                assertEquals(1, db.versionDao().observeForNote(note.id).first().size)
                assertEquals(1, db.tagDao().tagsForNote(note.id).first().size)
                assertEquals(1, db.reminderDao().observeForNote(note.id).first().size)
                val draft = db.recoveryDao().get(note.id)!!
                assertEquals(DocumentRecovery.digest(draft.title, draft.documentJson), draft.sha256)
            }
            assertEquals(2, count(db, "templates"))
            // Existing conflicting original IDs never overwrite: restore allocates fresh copies.
            assertEquals(2, service.restore(backup.inputStream()))
            assertEquals(4, count(db, "notes")); assertEquals(2, count(db, "tags"))
            db.noteDao().observeActive().first().forEach { note ->
                DocumentSerializer.deserializeForEditing(note.documentJson).first.forEach { b ->
                    (b.imagePath ?: b.drawingPath)?.let { created += File(it).parentFile!! }
                }
            }
        } finally { db.close(); created.forEach { it.deleteRecursively() } }
    }

    @Test fun rejectsCorruptionTraversalCompatibilityAndDuplicateIdsWithoutMutation() = runBlocking {
        val db = database()
        try {
            val nb = db.notebookDao().insert(NotebookEntity(name = "Must survive"))
            db.noteDao().insert(NoteEntity(notebookId = nb, title = "Keep", documentJson = DocumentSerializer.serialize(listOf(DocumentBlock()), PageSetup())))
            val service = LocalBackup(context, db)
            val valid = ByteArrayOutputStream().also { service.export(it) }.toByteArray()
            val base = unzip(valid)
            val incompatible = base.toMutableMap().apply {
                this["manifest.json"] = JSONObject(String(getValue("manifest.json"))).put("version", 999).toString().toByteArray()
            }
            val unsafe = base.toMutableMap().apply { this["../escape"] = byteArrayOf(1) }
            val corrupt = base.toMutableMap().apply { this["database.json"] = "tampered".toByteArray() }
            val duplicate = base.toMutableMap().apply {
                val data = JSONObject(String(getValue("database.json")))
                data.getJSONArray("notes").put(data.getJSONArray("notes").getJSONObject(0))
                val bytes = data.toString().toByteArray(); this["database.json"] = bytes
                val manifest = JSONObject(String(getValue("manifest.json")))
                val entries = manifest.getJSONArray("entries")
                for (i in 0 until entries.length()) if (entries.getJSONObject(i).getString("path") == "database.json") {
                    entries.getJSONObject(i).put("size", bytes.size).put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
                }
                this["manifest.json"] = manifest.toString().toByteArray()
            }
            listOf(incompatible, unsafe, corrupt, duplicate).forEach { archive ->
                assertTrue(runCatching { service.restore(zip(archive).inputStream()) }.isFailure)
                assertEquals(1, count(db, "notes")); assertEquals(1, count(db, "notebooks"))
                assertEquals("Keep", db.noteDao().observeActive().first().single().title)
            }
        } finally { db.close() }
    }

    @Test fun migrationTwoToThreePreservesExistingData() = runBlocking {
        val name = "migration-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, NotelyDatabase::class.java, name).addMigrations(NotelyDatabase.MIGRATION_2_3).build()
        var db = open()
        try {
            val nb = db.notebookDao().insert(NotebookEntity(name = "Existing"))
            val id = db.noteDao().insert(NoteEntity(notebookId = nb, title = "Preserved"))
            db.close()
            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use {
                it.execSQL("DROP TABLE recovery_drafts"); it.version = 2
            }
            db = open()
            assertEquals("Preserved", db.noteDao().getById(id)!!.title)
            assertNull(db.recoveryDao().get(id))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(bytes.inputStream()).use { z -> while (true) { val e = z.nextEntry ?: break; put(e.name, z.readBytes()) } }
    }
    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z -> entries.forEach { (name, bytes) -> z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() } }
    }.toByteArray()
}
