package com.notely.app.data.repo

import com.notely.app.data.db.*
import com.notely.app.doc.DocumentSerializer
import kotlinx.coroutines.flow.Flow

class NotebookRepository(private val dao: NotebookDao) {
    fun observeActive() = dao.observeActive()
    fun observeAll() = dao.observeAll()
    fun observeTrashed() = dao.observeTrashed()
    fun countActive() = dao.countActive()
    suspend fun getById(id: Long) = dao.getById(id)
    suspend fun create(name: String, icon: String = "book", color: Int = 0): Long =
        dao.insert(NotebookEntity(name = name, icon = icon, colorIndex = color))
    suspend fun rename(id: Long, name: String, icon: String, color: Int) =
        dao.updateFields(id, name, icon, color, System.currentTimeMillis())
    suspend fun setTrashed(id: Long, trashed: Boolean) = dao.setTrashed(id, trashed, System.currentTimeMillis())
    suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived, System.currentTimeMillis())
    suspend fun setViewMode(id: Long, mode: String) = dao.setViewMode(id, mode)

    /** Moving every note of [from] into [to] is the safe "merge notebooks" primitive. */
    suspend fun mergeNotebook(from: Long, to: Long) {
        dao.reparentNotes(from, to)
        dao.setTrashed(from, true, System.currentTimeMillis())
    }
    suspend fun deleteForever(id: Long) = dao.deleteById(id)
}

class NoteRepository(
    private val dao: NoteDao,
    private val versionDao: VersionDao
) {
    fun observeActive() = dao.observeActive()
    fun observeInNotebook(nb: Long) = dao.observeInNotebook(nb)
    fun observeTrashed() = dao.observeTrashed()
    fun observeArchived() = dao.observeArchived()
    fun observeRecent(limit: Int = 20) = dao.observeRecent(limit)
    fun observeById(id: Long) = dao.observeById(id)
    fun observeFavorites() = dao.observeFavorites()
    fun observePinned() = dao.observePinned()
    fun countActive() = dao.countActive()
    fun countInNotebook(nb: Long) = dao.countInNotebook(nb)
    fun countLiveInNotebook(nb: Long) = dao.countLiveInNotebook(nb)
    fun countArchived() = dao.countArchived()
    fun countPinned() = dao.countPinned()
    fun countFavorite() = dao.countFavorite()
    fun countTrashed() = dao.countTrashed()
    fun totalContentBytes() = dao.totalContentBytes()
    fun observeTrashList() = dao.observeTrashList()
    fun searchAll(q: String) = dao.searchAll(q)
    suspend fun getById(id: Long) = dao.getById(id)

    suspend fun create(notebookId: Long, title: String = "Untitled", documentJson: String = ""): Long =
        dao.insert(NoteEntity(notebookId = notebookId, title = title, documentJson = documentJson))

    suspend fun createFromTemplate(notebookId: Long, title: String, documentJson: String): Long =
        dao.insert(NoteEntity(notebookId = notebookId, title = title, documentJson = documentJson))

    suspend fun updateContent(id: Long, title: String, json: String) =
        dao.updateContent(id, title, json, System.currentTimeMillis())

    suspend fun snapshot(id: Long) {
        val n = dao.getById(id) ?: return
        if (n.documentJson.isBlank()) return
        versionDao.insert(VersionEntity(noteId = id, documentJson = n.documentJson))
        versionDao.prune(id)
    }

    suspend fun restoreVersion(noteId: Long, json: String) =
        dao.updateContent(noteId, dao.getById(noteId)?.title ?: "Untitled", json, System.currentTimeMillis())

    suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)
    suspend fun setFavorite(id: Long, fav: Boolean) = dao.setFavorite(id, fav)
    suspend fun setColor(id: Long, color: Int) = dao.setColor(id, color)
    suspend fun move(id: Long, notebookId: Long) = dao.moveToNotebook(id, notebookId, System.currentTimeMillis())
    suspend fun setTrashed(id: Long, trashed: Boolean) = dao.setTrashed(id, trashed, System.currentTimeMillis())
    suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived, System.currentTimeMillis())
    suspend fun setViewMode(id: Long, mode: String) = dao.setViewMode(id, mode)

    /** Moving every note of [from] into [to] is the safe "merge notebooks" primitive. */
    suspend fun mergeNotebook(from: Long, to: Long) {
        dao.reparentNotes(from, to)
        dao.setTrashed(from, true, System.currentTimeMillis())
    }
    suspend fun setReminder(id: Long, at: Long?) = dao.setReminder(id, at)
    suspend fun deleteForever(id: Long) = dao.deleteById(id)
    suspend fun emptyTrash() = dao.emptyTrash()

    suspend fun duplicate(id: Long): Long {
        val src = dao.getById(id) ?: return -1
        return dao.insert(
            src.copy(
                id = 0, title = src.title + " copy", pinned = false, reminderAt = null,
                trashed = false, archived = false,
                createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun search(query: String): Flow<List<NoteEntity>> {
        val q = query.trim()
        if (q.isBlank()) return dao.observeActive()
        // Indexed LIKE search over title + serialized document (offline, no full-content load).
        return dao.search(q)
    }
}

class TagRepository(private val dao: TagDao) {
    fun observeAll() = dao.observeAll()
    fun tagsForNote(noteId: Long) = dao.tagsForNote(noteId)
    fun notesForTag(tagId: Long) = dao.notesForTag(tagId)

    /** Copies every tag assignment from [fromNote] to [toNote]. */
    suspend fun copyTags(fromNote: Long, toNote: Long, tags: List<TagEntity>) {
        tags.forEach { dao.link(NoteTagCrossRef(toNote, it.id)) }
    }

    suspend fun ensureTag(name: String): Long {
        val clean = name.trim().removePrefix("#").trim()
        require(clean.isNotEmpty()) { "Tag name is empty" }
        return dao.getByName(clean)?.id ?: dao.insert(TagEntity(name = clean))
    }

    suspend fun assign(noteId: Long, tagName: String) {
        val id = ensureTag(tagName)
        dao.link(NoteTagCrossRef(noteId, id))
    }

    suspend fun unassign(noteId: Long, tagId: Long) = dao.unlink(noteId, tagId)
    suspend fun deleteTag(id: Long) = dao.deleteById(id)
}

class CommentRepository(private val dao: CommentDao) {
    fun observeForNote(noteId: Long) = dao.observeForNote(noteId)
    suspend fun add(noteId: Long, text: String): Long = dao.insert(CommentEntity(noteId = noteId, text = text))
    suspend fun edit(id: Long, text: String) = dao.updateText(id, text)
    suspend fun setResolved(id: Long, resolved: Boolean) = dao.setResolved(id, resolved)
    suspend fun delete(id: Long) = dao.deleteById(id)
}

class BookmarkRepository(private val dao: BookmarkDao) {
    fun observeForNote(noteId: Long) = dao.observeForNote(noteId)
    suspend fun add(noteId: Long, name: String, blockId: String = ""): Long =
        dao.insert(BookmarkEntity(noteId = noteId, name = name, blockId = blockId))
    suspend fun delete(id: Long) = dao.deleteById(id)
}

class FootnoteRepository(private val dao: FootnoteDao) {
    fun observeForNote(noteId: Long) = dao.observeForNote(noteId)
    suspend fun add(noteId: Long, text: String): Long {
        val ref = dao.maxRef(noteId) + 1
        return dao.insert(FootnoteEntity(noteId = noteId, refNumber = ref, text = text))
    }
    suspend fun edit(id: Long, text: String) = dao.updateText(id, text)
    suspend fun delete(id: Long) = dao.deleteById(id)
}

class VersionRepository(private val dao: VersionDao) {
    fun observeForNote(noteId: Long) = dao.observeForNote(noteId)
    suspend fun delete(id: Long) = dao.deleteById(id)
}

class TemplateRepository(private val dao: TemplateDao) {
    fun observeAll() = dao.observeAll()
    suspend fun save(name: String, documentJson: String, builtin: Boolean = false): Long =
        dao.insert(TemplateEntity(name = name, documentJson = documentJson, builtin = builtin))
    suspend fun delete(id: Long) = dao.deleteById(id)
}

class ReminderRepository(private val dao: ReminderDao) {
    fun observeForNote(noteId: Long) = dao.observeForNote(noteId)
    suspend fun add(noteId: Long, atMillis: Long, label: String): Long =
        dao.insert(ReminderEntity(noteId = noteId, atMillis = atMillis, label = label))
    suspend fun delete(id: Long) = dao.deleteById(id)
    suspend fun clearForNote(noteId: Long) = dao.deleteForNote(noteId)
    suspend fun upcoming(now: Long) = dao.upcoming(now)
    suspend fun all() = dao.all()
}

/** Built-in templates: each creates a real editable document. */
object BuiltinTemplates {
    fun all(): List<Pair<String, String>> {
        fun doc(title: String, vararg lines: String): String {
            val blocks = mutableListOf<com.notely.app.doc.DocumentBlock>()
            blocks.add(com.notely.app.doc.DocumentBlock(type = com.notely.app.doc.BlockType.HEADING, headingLevel = 1,
                runs = listOf(com.notely.app.doc.TextRun(title, bold = true, fontSizeSp = 28f))))
            lines.forEach { blocks.add(com.notely.app.doc.DocumentBlock(runs = listOf(com.notely.app.doc.TextRun(it)))) }
            return DocumentSerializer.serialize(blocks, com.notely.app.doc.PageSetup())
        }
        return listOf(
            "Blank Document" to doc("Untitled", ""),
            "Meeting Notes" to doc("Meeting Notes", "Date: ", "Attendees: ", "Agenda:", "• ", "Decisions:", "• ", "Action items:", "• "),
            "Daily Journal" to doc("Daily Journal", "Today I…", "Grateful for:", "Tomorrow:"),
            "Weekly Planner" to doc("Weekly Planner", "Monday:", "Tuesday:", "Wednesday:", "Thursday:", "Friday:", "Weekend:"),
            "Study Notes" to doc("Study Notes", "Topic:", "Key ideas:", "• ", "Questions:", "• "),
            "Business Report" to doc("Business Report", "Executive summary:", "Findings:", "• ", "Recommendations:", "• "),
            "Project Plan" to doc("Project Plan", "Goal:", "Scope:", "Milestones:", "• ", "Risks:", "• "),
            "CV" to doc("CV", "Name:", "Contact:", "Experience:", "• ", "Education:", "• ", "Skills:", "• "),
            "Resume" to doc("Resume", "Name:", "Summary:", "Experience:", "• ", "Skills:", "• "),
            "Invoice" to doc("Invoice", "Invoice #: ", "Date: ", "Bill to:", "Items:", "• ", "Total:"),
            "Expense Tracker" to doc("Expense Tracker", "Date | Item | Amount", "— | — | —"),
            "Research Notes" to doc("Research Notes", "Question:", "Sources:", "• ", "Findings:", "• ", "Conclusion:"),
            "Letter" to doc("Letter", "Date:", "", "Dear ", "", "Sincerely,"),
            "Task List" to doc("Task List", "☐ ", "☐ ", "☐ "),
            "Shopping List" to doc("Shopping List", "☐ ", "☐ ", "☐ "),
            "Personal Diary" to doc("Personal Diary", "Dear Diary,")
        )
    }
}
