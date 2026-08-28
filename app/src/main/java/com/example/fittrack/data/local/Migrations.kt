package com.example.fittrack.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema history. Every step is additive so logged workouts survive an upgrade
 * — the database is never rebuilt from scratch.
 *
 *   v1  steps only
 *   v2  + workouts
 *   v3  + exercises, and workouts.exerciseIcon
 *   v4  + routines and routine_exercises, and workouts.sessionName
 *   v5  + syncId on workouts/exercises/routines, for the Firebase mirror
 *   v6  + chat_messages and ai_response_cache, for the assistant
 */
object Migrations {

    /**
     * v2 shipped before migrations existed, so a device could still be sitting
     * on v1 with only the steps table. Creating `workouts` here matches what
     * Room generated for v2; IF NOT EXISTS keeps it harmless either way.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `workouts` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `type` TEXT NOT NULL,
                    `durationMinutes` INTEGER NOT NULL,
                    `calories` INTEGER NOT NULL,
                    `date` TEXT NOT NULL,
                    `notes` TEXT
                )
                """.trimIndent()
            )
        }
    }

    /** Adds the exercise catalogue and lets a workout remember its emoji. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `exercises` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `catalogKey` TEXT,
                    `name` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `met` REAL NOT NULL,
                    `icon` TEXT NOT NULL,
                    `isCustom` INTEGER NOT NULL,
                    `isFavorite` INTEGER NOT NULL,
                    `intensity` TEXT,
                    `lastUsedAt` INTEGER
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_exercises_catalogKey` ON `exercises` (`catalogKey`)"
            )
            db.execSQL("ALTER TABLE `workouts` ADD COLUMN `exerciseIcon` TEXT")
        }
    }

    /** Adds saved gym sessions and tags workouts with the session they came from. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `routines` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `icon` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `lastUsedAt` INTEGER
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `routine_exercises` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `routineId` INTEGER NOT NULL,
                    `exerciseId` INTEGER NOT NULL,
                    `durationMinutes` INTEGER NOT NULL,
                    `position` INTEGER NOT NULL,
                    FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE ,
                    FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_routine_exercises_routineId` ON `routine_exercises` (`routineId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_routine_exercises_exerciseId` ON `routine_exercises` (`exerciseId`)"
            )
            db.execSQL("ALTER TABLE `workouts` ADD COLUMN `sessionName` TEXT")
        }
    }

    /**
     * Gives every syncable row a stable id the cloud copy can be keyed on.
     * Existing rows are backfilled in place with a random 16-byte hex value, so
     * data already on the device keeps its identity once sync is switched on.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            listOf("workouts", "exercises", "routines").forEach { table ->
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `syncId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `$table` SET `syncId` = lower(hex(randomblob(16))) WHERE `syncId` = ''")
            }
        }
    }

    /**
     * Assistant history and the Tier 3 answer cache. Both are device-local and
     * are deliberately excluded from the Firestore mirror.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chat_messages` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `role` TEXT NOT NULL,
                    `text` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `pendingActionJson` TEXT,
                    `actionState` TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chat_messages_userId` ON `chat_messages` (`userId`)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_response_cache` (
                    `questionHash` TEXT NOT NULL,
                    `answer` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`questionHash`)
                )
                """.trimIndent()
            )
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
}
