package io.github.coolcoderme.haphone.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object HubMigrations {
    /** v0.1.0-test was version 1 without last_changed timestamps. */
    val V1_ENTITY_TABLE = """
        CREATE TABLE IF NOT EXISTS `entities` (
          `id` TEXT NOT NULL,
          `deviceId` TEXT NOT NULL,
          `kind` TEXT NOT NULL,
          `name` TEXT NOT NULL,
          `available` INTEGER NOT NULL,
          `onState` INTEGER,
          `brightness` INTEGER,
          `extraJson` TEXT NOT NULL,
          PRIMARY KEY(`id`)
        )
    """.trimIndent()

    const val ADD_LAST_CHANGED = "ALTER TABLE entities ADD COLUMN lastChangedAt INTEGER NOT NULL DEFAULT 0"
    const val ADD_LAST_UPDATED = "ALTER TABLE entities ADD COLUMN lastUpdatedAt INTEGER NOT NULL DEFAULT 0"

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(ADD_LAST_CHANGED)
            db.execSQL(ADD_LAST_UPDATED)
        }
    }
}
