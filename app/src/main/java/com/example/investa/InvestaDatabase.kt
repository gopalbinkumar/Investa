package com.example.investa

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AssetEntity::class, TransactionEntity::class],
    version = 4,
    exportSchema = false
)
abstract class InvestaDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE assets ADD COLUMN averagePrice INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE assets SET averagePrice = CAST(investedAmount / quantity AS INTEGER) " +
                        "WHERE quantity > 0"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        assetId INTEGER NOT NULL,
                        action TEXT NOT NULL,
                        date INTEGER NOT NULL,
                        quantity REAL NOT NULL,
                        price INTEGER NOT NULL,
                        fee INTEGER NOT NULL,
                        total INTEGER NOT NULL,
                        notes TEXT NOT NULL,
                        FOREIGN KEY(assetId) REFERENCES assets(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_transactions_assetId " +
                        "ON transactions(assetId)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE assets ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("UPDATE assets SET updatedAt = createdAt WHERE updatedAt = 0")
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE transactions SET createdAt = date, updatedAt = date " +
                        "WHERE createdAt = 0"
                )
            }
        }

        @Volatile
        private var instance: InvestaDatabase? = null

        fun getInstance(context: Context): InvestaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    InvestaDatabase::class.java,
                    "investa.db"
                ).addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .addMigrations(MIGRATION_3_4)
                    .build().also { instance = it }
            }
    }
}
