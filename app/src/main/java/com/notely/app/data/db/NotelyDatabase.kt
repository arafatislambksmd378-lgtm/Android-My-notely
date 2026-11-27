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
        TemplateEntity::class, ReminderEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class NotelyDatabase : RoomDatabase() {
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

        fun get(context: Context): NotelyDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context, NotelyDatabase::class.java, "notely.db")
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
