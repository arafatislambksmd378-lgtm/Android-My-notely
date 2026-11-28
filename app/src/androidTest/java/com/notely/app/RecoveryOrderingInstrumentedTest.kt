package com.notely.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notely.app.data.db.*
import com.notely.app.doc.*
import com.notely.app.recovery.DocumentRecovery
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecoveryOrderingInstrumentedTest {
    @Test fun rapidWritesAndOldEditorSessionsCannotReplaceLatestAcknowledgedState() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NotelyDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val nb = db.notebookDao().insert(NotebookEntity(name = "Isolated"))
            val id = db.noteDao().insert(NoteEntity(notebookId = nb))
            val writer = DocumentRecovery(db, scope)
            val old = writer.beginSession(id)
            val jobs = (1..100).map { n -> writer.write(id, old, "title$n", listOf(DocumentBlock(id = "stable", runs = listOf(TextRun("edit$n")))), PageSetup(), false) {} }
            jobs.forEach { it.join() }
            assertEquals("title100", writer.load(id).first.title)
            val current = writer.beginSession(id)
            val ack = CompletableDeferred<Result<Unit>>()
            writer.write(id, current, "current", listOf(DocumentBlock(id = "stable", runs = listOf(TextRun("current")))), PageSetup(), false) { ack.complete(it) }
            ack.await().getOrThrow()
            writer.write(id, old, "stale", emptyList(), PageSetup(), true) {}.join()
            assertEquals("current", writer.load(id).first.title)
            val draft = db.recoveryDao().get(id)!!
            db.recoveryDao().put(draft.copy(sha256 = "corrupt"))
            assertFalse(writer.load(id).second)
            assertEquals("Untitled", writer.load(id).first.title)
            // Inconsistent generation must not be replayed over a more recent direct update.
            db.recoveryDao().put(draft.copy(baseUpdatedAt = -1))
            assertFalse(writer.load(id).second)
        } finally { scope.cancel(); db.close() }
    }
}
