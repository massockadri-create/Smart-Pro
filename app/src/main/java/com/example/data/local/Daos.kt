package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY id ASC")
    fun observeAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Long): UserEntity?

    @Query("SELECT * FROM users")
    suspend fun getAllUsersOnce(): List<UserEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUserById(id: Long)

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoles(roles: List<RoleEntity>)

    @Query("SELECT * FROM roles")
    fun observeRoles(): Flow<List<RoleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPermissions(permissions: List<PermissionEntity>)

    @Query("SELECT * FROM permissions")
    fun observePermissions(): Flow<List<PermissionEntity>>

    @Query("SELECT * FROM user_permissions WHERE userId = :userId")
    fun observeUserPermissions(userId: Long): Flow<List<UserPermissionEntity>>

    @Query("SELECT * FROM user_permissions WHERE userId = :userId AND granted = 1")
    suspend fun getGrantedPermissionsForUser(userId: Long): List<UserPermissionEntity>

    @Query("SELECT * FROM user_permissions WHERE userId = :userId")
    suspend fun getAllPermissionsForUser(userId: Long): List<UserPermissionEntity>

    @Query("SELECT * FROM user_permissions")
    fun observeAllUserPermissions(): Flow<List<UserPermissionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserPermissions(perms: List<UserPermissionEntity>)

    @Query("DELETE FROM user_permissions WHERE userId = :userId")
    suspend fun clearPermissionsForUser(userId: Long)

    @Query("DELETE FROM user_permissions")
    suspend fun deleteAllUserPermissions()
}

@Dao
interface CatalogDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getCategoriesOnce(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    @Query("SELECT * FROM products ORDER BY name ASC")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products")
    suspend fun getProductsOnce(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE barcode = :code OR sku = :code OR reference = :code LIMIT 1")
    suspend fun findByBarcodeOrSku(code: String): ProductEntity?

    @Query("SELECT * FROM products WHERE syncStatus = 'PENDING_SYNC'")
    suspend fun getPendingSyncProducts(): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("UPDATE products SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun markProductSynced(id: Long)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProduct(id: Long)

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()

    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun observeSuppliers(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers")
    suspend fun getSuppliersOnce(): List<SupplierEntity>

    @Query("SELECT * FROM suppliers WHERE id = :id LIMIT 1")
    suspend fun getSupplierById(id: Long): SupplierEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: SupplierEntity): Long

    @Update
    suspend fun updateSupplier(supplier: SupplierEntity)

    @Query("DELETE FROM suppliers WHERE id = :id")
    suspend fun deleteSupplier(id: Long)

    @Query("DELETE FROM suppliers")
    suspend fun deleteAllSuppliers()

    @Query("UPDATE suppliers SET totalPurchased = 0.0, debtAmount = 0.0")
    suspend fun resetSupplierBalances()

    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun observeCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers")
    suspend fun getCustomersOnce(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: Long): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomer(id: Long)

    @Query("DELETE FROM customers")
    suspend fun deleteAllCustomers()

    @Query("UPDATE customers SET totalPurchased = 0.0, creditAmount = 0.0, balance = 0.0, lastTransactionAt = 0")
    suspend fun resetCustomerBalances()
}

@Dao
interface SalesDao {
    @Query("SELECT * FROM sales ORDER BY createdAt DESC")
    fun observeSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales")
    suspend fun getSalesOnce(): List<SaleEntity>

    @Query("SELECT * FROM sales WHERE id = :id LIMIT 1")
    suspend fun getSaleById(id: Long): SaleEntity?

    @Query("SELECT * FROM sales WHERE syncStatus = 'PENDING_SYNC'")
    suspend fun getPendingSyncSales(): List<SaleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleEntity): Long

    @Update
    suspend fun updateSale(sale: SaleEntity)

    @Query("UPDATE sales SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun markSaleSynced(id: Long)

    @Query("DELETE FROM sales")
    suspend fun deleteAllSales()

    @Query("SELECT * FROM sale_items ORDER BY id DESC")
    fun observeAllSaleItems(): Flow<List<SaleItemEntity>>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    suspend fun getSaleItems(saleId: Long): List<SaleItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItems(items: List<SaleItemEntity>)

    @Query("DELETE FROM sale_items")
    suspend fun deleteAllSaleItems()

    @Query("SELECT * FROM payments ORDER BY createdAt DESC")
    fun observePayments(): Flow<List<PaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Query("DELETE FROM payments")
    suspend fun deleteAllPayments()
}

@Dao
interface OnlinePaymentDao {
    @Query("SELECT * FROM online_payments ORDER BY createdAt DESC")
    fun observeOnlinePayments(): Flow<List<OnlinePaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOnlinePayment(payment: OnlinePaymentEntity): Long

    @Update
    suspend fun updateOnlinePayment(payment: OnlinePaymentEntity)

    @Query("DELETE FROM online_payments")
    suspend fun deleteAllOnlinePayments()

    @Query("SELECT * FROM online_payment_configs ORDER BY isEnabled DESC, id ASC")
    fun observeOnlinePaymentConfigs(): Flow<List<OnlinePaymentConfigEntity>>

    @Query("SELECT * FROM online_payment_configs ORDER BY isEnabled DESC, id ASC")
    suspend fun getOnlinePaymentConfigsOnce(): List<OnlinePaymentConfigEntity>

    @Query("SELECT * FROM online_payment_configs WHERE id = :id LIMIT 1")
    suspend fun getOnlinePaymentConfigById(id: Long): OnlinePaymentConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOnlinePaymentConfig(config: OnlinePaymentConfigEntity): Long

    @Update
    suspend fun updateOnlinePaymentConfig(config: OnlinePaymentConfigEntity)

    @Query("DELETE FROM online_payment_configs WHERE id = :id")
    suspend fun deleteOnlinePaymentConfig(id: Long)

    @Query("DELETE FROM online_payment_configs")
    suspend fun deleteAllOnlinePaymentConfigs()
}

@Dao
interface CashDao {
    @Query("SELECT * FROM cash_registers ORDER BY openedAt DESC")
    fun observeCashRegisters(): Flow<List<CashRegisterEntity>>

    @Query("SELECT * FROM cash_registers WHERE status = 'OUVERTE' ORDER BY openedAt DESC LIMIT 1")
    fun observeOpenRegister(): Flow<CashRegisterEntity?>

    @Query("SELECT * FROM cash_registers WHERE status = 'OUVERTE' ORDER BY openedAt DESC LIMIT 1")
    suspend fun getOpenRegisterOnce(): CashRegisterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashRegister(register: CashRegisterEntity): Long

    @Update
    suspend fun updateCashRegister(register: CashRegisterEntity)

    @Query("DELETE FROM cash_registers")
    suspend fun deleteAllCashRegisters()

    @Query("SELECT * FROM cash_movements ORDER BY createdAt DESC")
    fun observeCashMovements(): Flow<List<CashMovementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashMovement(movement: CashMovementEntity): Long

    @Query("DELETE FROM cash_movements")
    suspend fun deleteAllCashMovements()
}

@Dao
interface OperationsDao {
    @Query("SELECT * FROM purchases ORDER BY createdAt DESC")
    fun observePurchases(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchase_items ORDER BY id ASC")
    fun observeAllPurchaseItems(): Flow<List<PurchaseItemEntity>>

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId")
    suspend fun getPurchaseItems(purchaseId: Long): List<PurchaseItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: PurchaseEntity): Long

    @Update
    suspend fun updatePurchase(purchase: PurchaseEntity)

    @Query("DELETE FROM purchases")
    suspend fun deleteAllPurchases()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseItems(items: List<PurchaseItemEntity>)

    @Update
    suspend fun updatePurchaseItem(item: PurchaseItemEntity)

    @Query("DELETE FROM purchase_items")
    suspend fun deleteAllPurchaseItems()

    @Query("SELECT * FROM stock_movements ORDER BY createdAt DESC")
    fun observeStockMovements(): Flow<List<StockMovementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockMovement(movement: StockMovementEntity): Long

    @Query("DELETE FROM stock_movements")
    suspend fun deleteAllStockMovements()

    @Query("SELECT * FROM inventories ORDER BY createdAt DESC")
    fun observeInventories(): Flow<List<InventoryEntity>>

    @Query("SELECT * FROM inventory_items ORDER BY productName ASC")
    fun observeAllInventoryItems(): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE inventoryId = :inventoryId ORDER BY productName ASC")
    suspend fun getInventoryItems(inventoryId: Long): List<InventoryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventory(inventory: InventoryEntity): Long

    @Update
    suspend fun updateInventory(inventory: InventoryEntity)

    @Query("DELETE FROM inventories")
    suspend fun deleteAllInventories()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryItems(items: List<InventoryItemEntity>)

    @Update
    suspend fun updateInventoryItem(item: InventoryItemEntity)

    @Query("DELETE FROM inventory_items")
    suspend fun deleteAllInventoryItems()

    @Query("""
        UPDATE inventories SET
            status = :status,
            totalTheoreticalValue = :theoVal,
            totalActualValue = :actVal,
            totalDiscrepancyValue = :discVal,
            missingCount = :missing,
            surplusCount = :surplus,
            damagedCount = :damaged
        WHERE id = :inventoryId
    """)
    suspend fun updateInventorySummary(
        inventoryId: Long,
        status: String,
        theoVal: Double,
        actVal: Double,
        discVal: Double,
        missing: Int,
        surplus: Int,
        damaged: Int
    )
}

@Dao
interface AdminDao {
    @Query("SELECT * FROM expenses ORDER BY expenseDate DESC")
    fun observeExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses")
    suspend fun getExpensesOnce(): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpense(id: Long)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()

    @Query("SELECT * FROM reports ORDER BY createdAt DESC")
    fun observeReports(): Flow<List<ReportRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: ReportRecordEntity): Long

    @Query("DELETE FROM reports")
    suspend fun deleteAllReports()

    @Query("SELECT * FROM audit_logs ORDER BY createdAt DESC")
    fun observeAuditLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE syncStatus = 'PENDING_SYNC'")
    suspend fun getPendingSyncAuditLogs(): List<AuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity): Long

    @Query("UPDATE audit_logs SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun markAuditLogSynced(id: Long)

    @Query("DELETE FROM audit_logs")
    suspend fun deleteAllAuditLogs()

    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    fun observeSettings(): Flow<StoreSettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsOnce(): StoreSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: StoreSettingsEntity)

    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun observeNotifications(): Flow<List<AlertNotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: AlertNotificationEntity): Long

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationRead(id: Long)

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllNotificationsRead()

    @Query("DELETE FROM notifications")
    suspend fun deleteAllNotifications()

    @Query("SELECT * FROM backups ORDER BY createdAt DESC")
    fun observeBackups(): Flow<List<BackupRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackup(backup: BackupRecordEntity): Long

    @Query("DELETE FROM backups WHERE id = :id")
    suspend fun deleteBackup(id: Long)

    @Query("DELETE FROM backups")
    suspend fun deleteAllBackups()

    @Query("SELECT * FROM invoices ORDER BY createdAt DESC")
    fun observeInvoices(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE saleId = :saleId LIMIT 1")
    suspend fun getInvoiceBySaleId(saleId: Long): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE id = :id LIMIT 1")
    suspend fun getInvoiceById(id: Long): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE invoiceNumber = :invoiceNumber LIMIT 1")
    suspend fun getInvoiceByNumber(invoiceNumber: String): InvoiceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: InvoiceEntity): Long

    @Update
    suspend fun updateInvoice(invoice: InvoiceEntity)

    @Query("DELETE FROM invoices")
    suspend fun deleteAllInvoices()

    @Query("SELECT * FROM document_files ORDER BY createdAt DESC")
    fun observeDocuments(): Flow<List<DocumentFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: DocumentFileEntity): Long

    @Query("DELETE FROM document_files WHERE id = :id")
    suspend fun deleteDocument(id: Long)

    @Query("DELETE FROM document_files")
    suspend fun deleteAllDocuments()
}
