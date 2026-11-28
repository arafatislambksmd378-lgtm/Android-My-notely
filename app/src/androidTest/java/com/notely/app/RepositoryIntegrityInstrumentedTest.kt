package com.notely.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notely.app.data.db.*
import com.notely.app.data.repo.NoteRepository
import com.notely.app.doc.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room/SQLite tests. These are NOT a substitute for process-restart UI tests. */
@RunWith(AndroidJUnit4::class)
class RepositoryIntegrityInstrumentedTest {
    private fun database() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(), NotelyDatabase::class.java
    ).build()

    @Test fun mergeMovesNotesAndRejectsInvalidDestinationsWithoutMutation() = runBlocking {
        val db = database()
        try {
            val source = db.notebookDao().insert(NotebookEntity(name = "Source"))
            val target = db.notebookDao().insert(NotebookEntity(name = "Target"))
            val repo = NoteRepository(db.noteDao(), db.versionDao(), db.notebookDao(), db)
            val note = repo.create(source, "Keep")
            assertTrue(runCatching { repo.mergeNotebook(source, Long.MAX_VALUE) }.isFailure)
            assertEquals(source, repo.getById(note)!!.notebookId)
            assertFalse(db.notebookDao().getById(source)!!.archived)
            assertTrue(runCatching { repo.mergeNotebook(source, source) }.isFailure)
            repo.mergeNotebook(source, target)
            assertEquals(target, repo.getById(note)!!.notebookId)
            assertTrue(db.notebookDao().getById(source)!!.archived)
        } finally { db.close() }
    }

    @Test fun failedMergeRollsBackNoteMove() = runBlocking {
        val db = database()
        try {
            val source = db.notebookDao().insert(NotebookEntity(name = "Source"))
            val target = db.notebookDao().insert(NotebookEntity(name = "Target"))
            val repo = NoteRepository(db.noteDao(), db.versionDao(), db.notebookDao(), db)
            val note = repo.create(source)
            // Inject an actual SQLite failure in the second half of the operation.
            db.openHelper.writableDatabase.execSQL("""
                CREATE TRIGGER fail_archive BEFORE UPDATE OF archived ON notebooks
                BEGIN SELECT RAISE(ABORT, 'injected failure'); END
            """.trimIndent())
            assertTrue(runCatching { repo.mergeNotebook(source, target) }.isFailure)
            assertEquals(source, repo.getById(note)!!.notebookId)
            assertFalse(db.notebookDao().getById(source)!!.archived)
        } finally { db.close() }
    }

    @Test fun invalidVersionCannotOverwriteDocument() = runBlocking {
        val db = database()
        try {
            val notebook = db.notebookDao().insert(NotebookEntity(name = "Book"))
            val repo = NoteRepository(db.noteDao(), db.versionDao(), db.notebookDao(), db)
            val json = DocumentSerializer.serialize(listOf(DocumentBlock(runs = listOf(TextRun("Keep")))), PageSetup())
            val note = repo.create(notebook, "Title", json)
            assertTrue(runCatching { repo.restoreVersion(note, "{broken") }.isFailure)
            assertEquals(json, repo.getById(note)!!.documentJson)
        } finally { db.close() }
    }
}
