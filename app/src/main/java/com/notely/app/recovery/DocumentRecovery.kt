package com.notely.app.recovery

import androidx.room.withTransaction
import com.notely.app.data.db.*
import com.notely.app.doc.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

/** App-owned, per-note ordered writer. No lifecycle event is needed to stage an edit. */
class DocumentRecovery(private val db: NotelyDatabase, private val scope: CoroutineScope) {
    private data class Slot(val mutex: Mutex = Mutex(), var session: Long = 0, var ticket: Long = 0, var pending: Job? = null)
    private val slots = mutableMapOf<Long, Slot>()
    private fun slot(id: Long) = synchronized(slots) { slots.getOrPut(id) { Slot() } }

    fun beginSession(id: Long): Long = synchronized(slots) {
        val slot = slot(id)
        slot.pending?.cancel()
        ++slot.session
    }

    /** Recovery is used only when it matches the main-row generation and its own digest. */
    suspend fun load(id: Long): Pair<NoteEntity, Boolean> = withContext(Dispatchers.IO) {
        slot(id).mutex.withLock {
            db.withTransaction {
                val note = db.noteDao().getById(id) ?: error("Document no longer exists")
                val draft = db.recoveryDao().get(id)
                if (draft != null && draft.baseUpdatedAt == note.updatedAt &&
                    digest(draft.title, draft.documentJson) == draft.sha256 &&
                    runCatching { DocumentSerializer.deserializeForEditing(draft.documentJson) }.isSuccess) {
                    note.copy(title = draft.title, documentJson = draft.documentJson) to true
                } else note to false
            }
        }
    }

    /** Captured immutable model; serialization/SQL stay off-main. Superseded queued writes are cancelled. */
    fun write(id: Long, session: Long, title: String, blocks: List<DocumentBlock>, page: PageSetup,
              commit: Boolean, onResult: (Result<Unit>) -> Unit): Job {
        val slot = slot(id)
        return synchronized(slots) {
            val ticket = ++slot.ticket
            slot.pending?.cancel()
            scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                try {
                    slot.mutex.withLock {
                        if (synchronized(slots) { session != slot.session || ticket != slot.ticket }) throw CancellationException("Superseded snapshot")
                        val json = DocumentSerializer.serialize(blocks, page)
                        DocumentSerializer.deserializeForEditing(json)
                        db.withTransaction {
                            val note = db.noteDao().getById(id) ?: error("Document no longer exists")
                            val now = System.currentTimeMillis()
                            if (commit) db.noteDao().updateContent(id, title, json, now)
                            db.recoveryDao().put(RecoveryDraftEntity(id, title, json, digest(title, json),
                                if (commit) now else note.updatedAt, now))
                        }
                    }
                    withContext(Dispatchers.Main) { onResult(Result.success(Unit)) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    withContext(Dispatchers.Main) { onResult(Result.failure(error)) }
                }
            }.also { slot.pending = it; it.start() }
        }
    }

    companion object {
        fun digest(title: String, json: String): String = MessageDigest.getInstance("SHA-256")
            .digest((title + "\u0000" + json).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
