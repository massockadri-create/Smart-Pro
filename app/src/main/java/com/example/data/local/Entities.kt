package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val fullName: String,
    val phone: String = "",
    val whatsappNumber: String = "",
    val email: String = "",
    val positionTitle: String = "",
    val salaryFcfa: Double = 0.0,
    val hiredAt: Long = System.currentTimeMillis(),
    val passwordHash: String,
    val passwordSalt: String,
    val roleCode: String,
    val isActive: Boolean = true,
    val storeId: String = "STORE-CM-01",
    val lastLoginAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "roles")
data class RoleEntity(
    @PrimaryKey val code: String,
    val displayName: String,
    val description: String
)

@Entity(tableName = "permissions")
data class PermissionEntity(
    @PrimaryKey val code: String,
    val label: String,
    val category: String
)

@Entity(tableName = "user_permissions", primaryKeys = ["userId", "permissionCode"])
data class UserPermissionEntity(
    val userId: Long,
    val permissionCode: String,
    val granted: Boolean = true
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val subCategory: String = "",
    val colorHex: String = "#10B981"
)

@Entity(tableName = "suppliers")
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val company: String,
    val phone: String,
    val email: String = "",
    val address: String = "",
    val city: String = "",
    val suppliedProductsNote: String = "",
    val totalPurchased: Double = 0.0,
    val debtAmount: Double = 0.0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val reference: String,
    val sku: String,
    val barcode: String,
    val categoryId: Long,
    val categoryName: String,
    val subCategory: String = "",
    val description: String = "",
    val purchasePrice: Double,
    val sellingPrice: Double,
    val stockQuantity: Int,
    val minThreshold: Int,
    val supplierId: Long = 0L,
    val supplierName: String = "",
    val unit: String = "Unité",
    val status: String = "ACTIF", // ACTIF, INACTIF, ARCHIVE
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING_SYNC" // SYNCED, PENDING_SYNC
)

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val email: String = "",
    val address: String = "",
    val totalPurchased: Double = 0.0,
    val creditAmount: Double = 0.0,
    val balance: Double = 0.0,
    val lastTransactionAt: Long = 0L,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNumber: String,
    val customerId: Long = 0L,
    val customerName: String = "Client Comptant",
    val userId: Long,
    val userName: String,
    val cashRegisterId: Long = 0L,
    val subtotal: Double,
    val discountAmount: Double = 0.0,
    val taxRate: Double = 0.0,
    val taxAmount: Double = 0.0,
    val totalAmount: Double,
    val totalCost: Double,
    val profit: Double,
    val paymentMethod: String,
    val amountPaid: Double,
    val changeGiven: Double = 0.0,
    val status: String = "VALIDEE", // VALIDEE, ANNULEE
    val syncStatus: String = "PENDING_SYNC",
    val storeId: String = "STORE-CM-01",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sale_items")
data class SaleItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val productId: Long,
    val productName: String,
    val sku: String,
    val categoryName: String = "",
    val productInvoiceCode: String = "",
    val productBarcode: String = "",
    val quantity: Int,
    val unitPrice: Double,
    val purchasePrice: Double,
    val lineTotal: Double,
    val lineProfit: Double
)

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val method: String,
    val amount: Double,
    val referenceCode: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "online_payments")
data class OnlinePaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: String,
    val invoiceNumber: String = "",
    val provider: String, // CinetPay, NotchPay, Orange Money, MTN MoMo, Custom, etc.
    val configId: Long = 0L,
    val customerName: String,
    val customerPhone: String = "",
    val customerEmail: String = "",
    val amountXaf: Double,
    val status: String = "EN_ATTENTE_PAIEMENT", // EN_ATTENTE_PAIEMENT, EN_ATTENTE_VALIDATION, CONFIRME, ECHOUE
    val paymentUrl: String = "",
    val gatewayMessage: String = "",
    val isLiveGateway: Boolean = false,
    val createdByName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "online_payment_configs")
data class OnlinePaymentConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // CINETPAY, NOTCHPAY, ORANGE_MONEY, MTN_MOMO, BANK_CARD, CUSTOM_LINK, CUSTOM_API
    val displayName: String,
    val isEnabled: Boolean = true,
    val apiKey: String = "",
    val secretOrToken: String = "",
    val siteOrMerchantId: String = "",
    val receiverPhone: String = "",
    val accountHolderName: String = "",
    val ussdPattern: String = "",
    val paymentBaseUrl: String = "",
    val callbackUrl: String = "",
    val verifyEndpointUrl: String = "",
    val instructions: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cash_registers")
data class CashRegisterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storeId: String = "STORE-CM-01",
    val openedByUserId: Long,
    val openedByName: String,
    val closedByUserId: Long = 0L,
    val closedByName: String = "",
    val openingBalance: Double,
    val totalSalesCash: Double = 0.0,
    val totalSalesMobile: Double = 0.0,
    val totalCashIn: Double = 0.0,
    val totalCashOut: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val theoreticalBalance: Double = 0.0,
    val actualCountedBalance: Double = 0.0,
    val discrepancy: Double = 0.0,
    val discrepancyReason: String = "",
    val status: String = "OUVERTE", // OUVERTE, FERMEE
    val openedAt: Long = System.currentTimeMillis(),
    val closedAt: Long = 0L
)

@Entity(tableName = "cash_movements")
data class CashMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cashRegisterId: Long,
    val type: String, // ENTREE, SORTIE, RETRAIT, DEPENSE, VENTE
    val amount: Double,
    val reason: String,
    val userId: Long,
    val userName: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderNumber: String,
    val supplierId: Long,
    val supplierName: String,
    val userId: Long,
    val userName: String,
    val status: String = "COMMANDE", // BROUILLON, COMMANDE, PARTIELLEMENT_RECU, RECU, ANNULE
    val totalAmount: Double,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val receivedAt: Long = 0L
)

@Entity(tableName = "purchase_items")
data class PurchaseItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val productId: Long,
    val productName: String,
    val orderedQty: Int,
    val receivedQty: Int = 0,
    val unitCost: Double,
    val lineTotal: Double
)

@Entity(tableName = "stock_movements")
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val productName: String,
    val type: String, // ENTREE, SORTIE, VENTE, RECEPTION, RETOUR_CLIENT, RETOUR_FOURNISSEUR, PERTE, ENDOMMAGE, TRANSFERT, AJUSTEMENT, INVENTAIRE
    val quantityDelta: Int,
    val previousStock: Int,
    val newStock: Int,
    val unitCost: Double,
    val reason: String,
    val referenceId: String = "",
    val userId: Long,
    val userName: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "inventories")
data class InventoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val frequency: String, // QUOTIDIEN, HEBDOMADAIRE, MENSUEL, TRIMESTRIEL, ANNUEL, PERSONNALISE
    val scheduledDate: Long,
    val status: String = "PROGRAMME", // PROGRAMME, EN_COURS, EN_ATTENTE_VALIDATION, VALIDE
    val totalTheoreticalValue: Double = 0.0,
    val totalActualValue: Double = 0.0,
    val totalDiscrepancyValue: Double = 0.0,
    val missingCount: Int = 0,
    val surplusCount: Int = 0,
    val damagedCount: Int = 0,
    val createdByUserId: Long,
    val createdByName: String,
    val validatedByUserId: Long = 0L,
    val validatedByName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val validatedAt: Long = 0L
)

@Entity(tableName = "inventory_items")
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inventoryId: Long,
    val productId: Long,
    val productName: String,
    val sku: String,
    val theoreticalQty: Int,
    val actualQty: Int,
    val damagedQty: Int = 0,
    val discrepancyQty: Int = 0,
    val unitCost: Double,
    val discrepancyValue: Double = 0.0,
    val note: String = "",
    val isCounted: Boolean = false
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val amount: Double,
    val description: String,
    val receiptRef: String = "",
    val userId: Long,
    val userName: String,
    val expenseDate: Long = System.currentTimeMillis()
)

@Entity(tableName = "reports")
data class ReportRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reportType: String,
    val title: String,
    val periodLabel: String,
    val summaryText: String,
    val generatedByName: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val userName: String,
    val action: String,
    val module: String,
    val oldValue: String = "",
    val newValue: String = "",
    val deviceInfo: String = "",
    val syncStatus: String = "PENDING_SYNC",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "settings")
data class StoreSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val storeName: String = "",
    val city: String = "",
    val location: String = "",
    val slogan: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val structureType: String = "GENERAL",
    val logoKey: String = "GENERAL",
    val currency: String = "FCFA (XAF)",
    val vatRate: Double = 19.25,
    val taxEnabled: Boolean = false,
    val receiptFormat: String = "80mm Thermique",
    val receiptFooter: String = "",
    val invoicePrefix: String = "FAC",
    val nextInvoiceNumber: Int = 1001,
    val defaultMinStock: Int = 5,
    val enabledPaymentMethods: String = "Espèces,Mobile Money,Orange Money,MTN MoMo,Carte,Virement,CinetPay,NotchPay,Autre",
    val autoLogoutMinutes: Int = 30,
    val autoBackupEnabled: Boolean = true,
    val offlineModeEnabled: Boolean = false,
    val darkThemeEnabled: Boolean = false,
    val lastSyncAt: Long = 0L
)

@Entity(tableName = "notifications")
data class AlertNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val message: String,
    val severity: String = "WARNING",
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "backups")
data class BackupRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val type: String,
    val recordCount: Int,
    val sizeBytes: Long,
    val jsonPayload: String,
    val createdByName: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "invoices")
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNumber: String,
    val invoiceType: String = "FACTURE", // FACTURE, PROFORMA, AVOIR
    val saleId: Long = 0L,
    val customerId: Long = 0L,
    val customerName: String,
    val customerPhone: String = "",
    val customerAddress: String = "",
    val subtotalHt: Double,
    val discountAmount: Double = 0.0,
    val taxRate: Double = 0.0,
    val taxAmount: Double = 0.0,
    val totalTtc: Double,
    val amountPaid: Double,
    val balanceDue: Double = 0.0,
    val paymentMethod: String,
    val status: String = "PAYEE", // PAYEE, PARTIELLE, IMPAYEE, ANNULEE, PROFORMA
    val dueDate: Long = 0L,
    val issuedByName: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "document_files")
data class DocumentFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val mimeType: String,
    val extension: String,
    val category: String,
    val localPath: String,
    val sizeBytes: Long,
    val direction: String,
    val notes: String = "",
    val uploadedByName: String,
    val createdAt: Long = System.currentTimeMillis()
)
