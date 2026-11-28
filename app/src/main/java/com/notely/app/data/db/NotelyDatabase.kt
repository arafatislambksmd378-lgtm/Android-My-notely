package com.notely.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NotebookEntity::class, NoteEntity::class,
        TagEntity::class, NoteTagCrossRef::class, CommentEntity::class,
        BookmarkEntity::class, FootnoteEntity::class, VersionEntity::class,
        TemplateEntity::class, ReminderEntity::class, RecoveryDraftEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class NotelyDatabase : RoomDatabase() {
    abstract fun recoveryDao(): RecoveryDao
    abstract fun notebookDao(): NotebookDao
    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao
    abstract fun commentDao(): CommentDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun footnoteDao(): FootnoteDao
    abstract fun versionDao(): VersionDao
    abstract fun templateDao(): TemplateDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile private var INSTANCE: NotelyDatabase? = null

        /** v1 → v2: adds the note "viewMode" column used by the multi-page preview. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN viewMode TEXT NOT NULL DEFAULT 'edit'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS recovery_drafts (
                    noteId INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL,
                    documentJson TEXT NOT NULL, sha256 TEXT NOT NULL,
                    baseUpdatedAt INTEGER NOT NULL, stagedAt INTEGER NOT NULL,
                    FOREIGN KEY(noteId) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE CASCADE)
                """.trimIndent())
            }
        }

        fun get(context: Context): NotelyDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context, NotelyDatabase::class.java, "notely.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
    }
}
