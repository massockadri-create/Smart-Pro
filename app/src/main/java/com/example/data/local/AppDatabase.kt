package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserEntity::class,
        RoleEntity::class,
        PermissionEntity::class,
        UserPermissionEntity::class,
        CategoryEntity::class,
        SupplierEntity::class,
        ProductEntity::class,
        CustomerEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        PaymentEntity::class,
        OnlinePaymentEntity::class,
        OnlinePaymentConfigEntity::class,
        CashRegisterEntity::class,
        CashMovementEntity::class,
        PurchaseEntity::class,
        PurchaseItemEntity::class,
        StockMovementEntity::class,
        InventoryEntity::class,
        InventoryItemEntity::class,
        ExpenseEntity::class,
        ReportRecordEntity::class,
        AuditLogEntity::class,
        StoreSettingsEntity::class,
        AlertNotificationEntity::class,
        BackupRecordEntity::class,
        InvoiceEntity::class,
        DocumentFileEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun catalogDao(): CatalogDao
    abstract fun salesDao(): SalesDao
    abstract fun onlinePaymentDao(): OnlinePaymentDao
    abstract fun cashDao(): CashDao
    abstract fun operationsDao(): OperationsDao
    abstract fun adminDao(): AdminDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE online_payments ADD COLUMN configId INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `online_payment_configs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `category` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `apiKey` TEXT NOT NULL,
                        `secretOrToken` TEXT NOT NULL,
                        `siteOrMerchantId` TEXT NOT NULL,
                        `receiverPhone` TEXT NOT NULL,
                        `accountHolderName` TEXT NOT NULL,
                        `ussdPattern` TEXT NOT NULL,
                        `paymentBaseUrl` TEXT NOT NULL,
                        `callbackUrl` TEXT NOT NULL,
                        `verifyEndpointUrl` TEXT NOT NULL,
                        `instructions` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_store_pro.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
