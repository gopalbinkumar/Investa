package com.example.investa.data

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.investa.data.dao.AssetDao
import com.example.investa.data.dao.CashAccountDao
import com.example.investa.data.dao.CurrencyDao
import com.example.investa.data.dao.AppPreferenceDao
import com.example.investa.data.dao.TransactionDao
import com.example.investa.data.dao.PerformanceSnapshotDao
import com.example.investa.data.entity.AssetEntity
import com.example.investa.data.entity.CashAccountEntity
import com.example.investa.data.entity.CurrencyEntity
import com.example.investa.data.entity.TransactionEntity
import com.example.investa.data.entity.AppPreferenceEntity
import com.example.investa.data.entity.AssetPriceSnapshotEntity
import com.example.investa.data.entity.CurrencyRateSnapshotEntity
import com.example.investa.data.entity.CashBalanceSnapshotEntity

@Database(
    entities = [AssetEntity::class, TransactionEntity::class, CurrencyEntity::class, CashAccountEntity::class, AppPreferenceEntity::class, AssetPriceSnapshotEntity::class, CurrencyRateSnapshotEntity::class, CashBalanceSnapshotEntity::class],
    version = 12,
    exportSchema = false
)
abstract class InvestaDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun cashAccountDao(): CashAccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun currencyDao(): CurrencyDao
    abstract fun appPreferenceDao(): AppPreferenceDao
    abstract fun performanceSnapshotDao(): PerformanceSnapshotDao

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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")
                db.execSQL(
                    """
                    CREATE TABLE assets_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        symbol TEXT NOT NULL,
                        category TEXT NOT NULL,
                        quantity REAL NOT NULL,
                        investedAmount REAL NOT NULL,
                        averagePrice REAL NOT NULL,
                        currentPrice REAL NOT NULL,
                        currency TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO assets_new
                    SELECT id, name, symbol, category, quantity,
                        CAST(investedAmount AS REAL),
                        CAST(averagePrice AS REAL),
                        CAST(currentPrice AS REAL),
                        currency, notes, createdAt, updatedAt
                    FROM assets
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE assets")
                db.execSQL("ALTER TABLE assets_new RENAME TO assets")

                db.execSQL(
                    """
                    CREATE TABLE transactions_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        assetId INTEGER NOT NULL,
                        action TEXT NOT NULL,
                        date INTEGER NOT NULL,
                        quantity REAL NOT NULL,
                        price REAL NOT NULL,
                        fee REAL NOT NULL,
                        total REAL NOT NULL,
                        notes TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(assetId) REFERENCES assets(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO transactions_new
                    SELECT id, assetId, action, date, quantity,
                        CAST(price AS REAL), CAST(fee AS REAL), CAST(total AS REAL),
                        notes, createdAt, updatedAt
                    FROM transactions
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE transactions")
                db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_transactions_assetId " +
                        "ON transactions(assetId)"
                )
                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS currencies (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        code TEXT NOT NULL,
                        name TEXT NOT NULL,
                        symbol TEXT NOT NULL,
                        exchangeRate REAL NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        isActive INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_currencies_code " +
                        "ON currencies(code)"
                )
                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT OR IGNORE INTO currencies " +
                        "(code, name, symbol, exchangeRate, updatedAt, isActive) " +
                        "VALUES ('IDR', 'Indonesian Rupiah', 'Rp', 1.0, $now, 1)"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO currencies " +
                        "(code, name, symbol, exchangeRate, updatedAt, isActive) " +
                        "VALUES ('USD', 'US Dollar', '$', 16500.0, $now, 1)"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cash_accounts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        currencyCode TEXT NOT NULL,
                        balance REAL NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_cash_accounts_currencyCode " +
                        "ON cash_accounts(currencyCode)"
                )
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN currency TEXT NOT NULL DEFAULT 'IDR'"
                )
                db.execSQL(
                    """
                    UPDATE transactions
                    SET currency = COALESCE(
                        (SELECT currency FROM assets WHERE assets.id = transactions.assetId),
                        'IDR'
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN costBasis REAL NOT NULL DEFAULT 0.0"
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_preferences (
                        id INTEGER NOT NULL PRIMARY KEY,
                        primaryCurrencyCode TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO app_preferences (id, primaryCurrencyCode) VALUES (1, 'IDR')"
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_preferences ADD COLUMN numberFormatStyle TEXT NOT NULL DEFAULT 'ID'"
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS asset_price_snapshots (
                        assetId INTEGER NOT NULL,
                        day INTEGER NOT NULL,
                        price REAL NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(assetId, day),
                        FOREIGN KEY(assetId) REFERENCES assets(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_asset_price_snapshots_assetId_day ON asset_price_snapshots(assetId, day)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS currency_rate_snapshots (
                        currencyCode TEXT NOT NULL,
                        day INTEGER NOT NULL,
                        rateToIdr REAL NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(currencyCode, day)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_currency_rate_snapshots_currencyCode_day ON currency_rate_snapshots(currencyCode, day)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cash_balance_snapshots (
                        currencyCode TEXT NOT NULL,
                        day INTEGER NOT NULL,
                        balance REAL NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(currencyCode, day)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_balance_snapshots_currencyCode_day ON cash_balance_snapshots(currencyCode, day)")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN exchangeRateToIdr REAL NOT NULL DEFAULT 1.0")
                db.execSQL(
                    """
                    UPDATE transactions
                    SET exchangeRateToIdr = COALESCE(
                        (SELECT exchangeRate FROM currencies WHERE currencies.code = transactions.currency),
                        CASE WHEN currency = 'USD' THEN 16500.0 ELSE 1.0 END
                    )
                    """.trimIndent()
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
                    .addMigrations(MIGRATION_4_5)
                    .addMigrations(MIGRATION_5_6)
                    .addMigrations(MIGRATION_6_7)
                    .addMigrations(MIGRATION_7_8)
                    .addMigrations(MIGRATION_8_9)
                    .addMigrations(MIGRATION_9_10)
                    .addMigrations(MIGRATION_10_11)
                    .addMigrations(MIGRATION_11_12)
                    .build().also { instance = it }
            }
    }
}
