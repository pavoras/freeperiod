package org.freeperiod.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS custom_categories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT COLLATE NOCASE NOT NULL, iconKey TEXT NOT NULL, sortOrder INTEGER NOT NULL, archived INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_custom_categories_name ON custom_categories (name)")
        db.execSQL("ALTER TABLE tags ADD COLUMN categoryId INTEGER REFERENCES custom_categories(id) ON DELETE RESTRICT")
        db.execSQL("ALTER TABLE tags ADD COLUMN iconKey TEXT NOT NULL DEFAULT 'tag'")
        db.execSQL("DROP INDEX index_tags_name")
        db.execSQL("CREATE INDEX index_tags_categoryId ON tags (categoryId)")
        db.execSQL("CREATE UNIQUE INDEX index_tags_categoryId_name ON tags (categoryId, name)")
        db.execSQL("ALTER TABLE day_logs ADD COLUMN ovulationTest TEXT")
        db.execSQL("CREATE TABLE IF NOT EXISTS ui_overrides (`key` TEXT NOT NULL PRIMARY KEY, hidden INTEGER NOT NULL, sortOrder INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS situation (id INTEGER NOT NULL PRIMARY KEY, phase TEXT NOT NULL, method TEXT NOT NULL, pillPackStartEpochDay INTEGER, pillActiveDays INTEGER, pillBreakDays INTEGER, fertileWindowEnabled INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS reminders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, kind TEXT NOT NULL, title TEXT, recurrenceKind TEXT NOT NULL, recurrenceN INTEGER, anchorEpochDay INTEGER, weekday TEXT, monthDay INTEGER, onceEpochDay INTEGER, time TEXT NOT NULL, enabled INTEGER NOT NULL, daysBefore INTEGER, lastDeliveredDate INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS hint_dismissals (startPeriodId INTEGER NOT NULL PRIMARY KEY, FOREIGN KEY(startPeriodId) REFERENCES periods(id) ON DELETE CASCADE)")
    }
}
