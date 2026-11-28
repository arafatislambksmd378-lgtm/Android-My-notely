package com.notely.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = "book",
    val colorIndex: Int = 0,
    val archived: Boolean = false,
    val trashed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notes", indices = [Index("notebookId"), Index("updatedAt"), Index("trashed")])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val title: String = "Untitled",
    val documentJson: String = "",
    val cardColorIndex: Int = 0,
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val archived: Boolean = false,
    val trashed: Boolean = false,
    /** "edit" or "view" – opens the note read-only (multi-page preview / print view). */
    val viewMode: String = "edit",
    val reminderAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tags", indices = [Index("name", unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(tableName = "note_tags", primaryKeys = ["noteId", "tagId"])
data class NoteTagCrossRef(val noteId: Long, val tagId: Long)

@Entity(tableName = "comments", indices = [Index("noteId")])
data class CommentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val text: String,
    val author: String = "You",
    val resolved: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks", indices = [Index("noteId")])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val name: String,
    val blockId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "footnotes", indices = [Index("noteId")])
data class FootnoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val refNumber: Int,
    val text: String
)

@Entity(tableName = "versions", indices = [Index("noteId"), Index("createdAt")])
data class VersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val documentJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val documentJson: String,
    val builtin: Boolean = false
)

@Entity(tableName = "reminders", indices = [Index("noteId")])
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val atMillis: Long,
    val label: String = "Reminder"
)

/** Last acknowledged valid editor snapshot, separate from the main note row. */
@Entity(tableName = "recovery_drafts", foreignKeys = [androidx.room.ForeignKey(
    entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"],
    onDelete = androidx.room.ForeignKey.CASCADE
)])
data class RecoveryDraftEntity(
    @PrimaryKey val noteId: Long,
    val title: String,
    val documentJson: String,
    val sha256: String,
    val baseUpdatedAt: Long,
    val stagedAt: Long
)
