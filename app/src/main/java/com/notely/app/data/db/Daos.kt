package com.notely.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebooks WHERE trashed = 0 AND archived = 0 ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE trashed = 0 ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE trashed = 1 ORDER BY updatedAt DESC")
    fun observeTrashed(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NotebookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(n: NotebookEntity): Long

    @Query("UPDATE notebooks SET name = :name, icon = :icon, colorIndex = :color, updatedAt = :now WHERE id = :id")
    suspend fun updateFields(id: Long, name: String, icon: String, color: Int, now: Long)

    @Query("UPDATE notebooks SET trashed = :trashed, updatedAt = :now WHERE id = :id")
    suspend fun setTrashed(id: Long, trashed: Boolean, now: Long)

    @Query("UPDATE notebooks SET archived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long)

    @Query("DELETE FROM notebooks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM notebooks WHERE trashed = 0")
    fun countActive(): Flow<Int>
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE trashed = 0 AND archived = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun observeActive(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE notebookId = :nb AND trashed = 0 AND archived = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun observeInNotebook(nb: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE trashed = 1 ORDER BY updatedAt DESC")
    fun observeTrashed(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE archived = 1 AND trashed = 0 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE trashed = 0 ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(n: NoteEntity): Long

    @Query("UPDATE notes SET title = :title, documentJson = :json, updatedAt = :now WHERE id = :id")
    suspend fun updateContent(id: Long, title: String, json: String, now: Long)

    @Query("UPDATE notes SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE notes SET favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("UPDATE notes SET cardColorIndex = :color WHERE id = :id")
    suspend fun setColor(id: Long, color: Int)

    @Query("UPDATE notes SET viewMode = :mode WHERE id = :id")
    suspend fun setViewMode(id: Long, mode: String)

    @Query("UPDATE notes SET notebookId = :nb, updatedAt = :now WHERE id = :id")
    suspend fun moveToNotebook(id: Long, nb: Long, now: Long)

    @Query("UPDATE notes SET trashed = :trashed, updatedAt = :now WHERE id = :id")
    suspend fun setTrashed(id: Long, trashed: Boolean, now: Long)

    @Query("UPDATE notes SET archived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long)

    @Query("UPDATE notes SET reminderAt = :at WHERE id = :id")
    suspend fun setReminder(id: Long, at: Long?)

    @Query("UPDATE notes SET notebookId = :to WHERE notebookId = :from")
    suspend fun reparentNotes(from: Long, to: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notes WHERE trashed = 1")
    suspend fun emptyTrash()

    @Query("SELECT COUNT(*) FROM notes WHERE trashed = 0")
    fun countActive(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE notebookId = :nb AND trashed = 0 AND archived = 0")
    fun countLiveInNotebook(nb: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE archived = 1 AND trashed = 0")
    fun countArchived(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE pinned = 1 AND trashed = 0")
    fun countPinned(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE favorite = 1 AND trashed = 0")
    fun countFavorite(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE trashed = 1")
    fun countTrashed(): Flow<Int>

    @Query("SELECT COALESCE(SUM(LENGTH(documentJson)), 0) FROM notes")
    fun totalContentBytes(): Flow<Long>

    @Query("SELECT * FROM notes WHERE trashed = 1 ORDER BY updatedAt DESC")
    fun observeTrashList(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE title LIKE :q OR documentJson LIKE :q ORDER BY updatedAt DESC LIMIT 80")
    fun searchAll(q: String): Flow<List<NoteEntity>>

    @Query("SELECT COUNT(*) FROM notes WHERE notebookId = :nb AND trashed = 0")
    fun countInNotebook(nb: Long): Flow<Int>

    @Query("SELECT * FROM notes WHERE trashed = 0 AND (title LIKE '%' || :q || '%' OR documentJson LIKE '%' || :q || '%') ORDER BY updatedAt DESC LIMIT 50")
    fun search(q: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE trashed = 0 AND favorite = 1 ORDER BY updatedAt DESC")
    fun observeFavorites(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE trashed = 0 AND pinned = 1 ORDER BY updatedAt DESC")
    fun observePinned(): Flow<List<NoteEntity>>
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(t: TagEntity): Long

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): TagEntity?

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(cross: NoteTagCrossRef)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId AND tagId = :tagId")
    suspend fun unlink(noteId: Long, tagId: Long)

    @Query("SELECT tags.* FROM tags JOIN note_tags ON tags.id = note_tags.tagId WHERE note_tags.noteId = :noteId ORDER BY tags.name ASC")
    fun tagsForNote(noteId: Long): Flow<List<TagEntity>>

    @Query("SELECT notes.* FROM notes JOIN note_tags ON notes.id = note_tags.noteId WHERE note_tags.tagId = :tagId AND notes.trashed = 0 ORDER BY notes.updatedAt DESC")
    fun notesForTag(tagId: Long): Flow<List<NoteEntity>>
}

@Dao
interface CommentDao {
    @Query("SELECT * FROM comments WHERE noteId = :noteId ORDER BY createdAt ASC")
    fun observeForNote(noteId: Long): Flow<List<CommentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(c: CommentEntity): Long

    @Query("UPDATE comments SET text = :text WHERE id = :id")
    suspend fun updateText(id: Long, text: String)

    @Query("UPDATE comments SET resolved = :resolved WHERE id = :id")
    suspend fun setResolved(id: Long, resolved: Boolean)

    @Query("DELETE FROM comments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM comments WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE noteId = :noteId ORDER BY createdAt ASC")
    fun observeForNote(noteId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(b: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bookmarks WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)
}

@Dao
interface FootnoteDao {
    @Query("SELECT * FROM footnotes WHERE noteId = :noteId ORDER BY refNumber ASC")
    fun observeForNote(noteId: Long): Flow<List<FootnoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(f: FootnoteEntity): Long

    @Query("UPDATE footnotes SET text = :text WHERE id = :id")
    suspend fun updateText(id: Long, text: String)

    @Query("DELETE FROM footnotes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COALESCE(MAX(refNumber), 0) FROM footnotes WHERE noteId = :noteId")
    suspend fun maxRef(noteId: Long): Int
}

@Dao
interface VersionDao {
    @Query("SELECT * FROM versions WHERE noteId = :noteId ORDER BY createdAt DESC LIMIT 30")
    fun observeForNote(noteId: Long): Flow<List<VersionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(v: VersionEntity): Long

    @Query("DELETE FROM versions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM versions WHERE noteId = :noteId AND id NOT IN (SELECT id FROM versions WHERE noteId = :noteId ORDER BY createdAt DESC LIMIT 30)")
    suspend fun prune(noteId: Long)
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY name ASC")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(t: TemplateEntity): Long

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE noteId = :noteId ORDER BY atMillis ASC")
    fun observeForNote(noteId: Long): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(r: ReminderEntity): Long

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM reminders WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)

    @Query("SELECT * FROM reminders ORDER BY atMillis ASC")
    suspend fun all(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE atMillis > :now ORDER BY atMillis ASC")
    suspend fun upcoming(now: Long): List<ReminderEntity>
}
