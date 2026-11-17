package com.example.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.data.local.*
import com.example.data.remote.CloudSyncRepository
import com.example.data.remote.OnlinePaymentService
import com.example.security.AppPermission
import com.example.security.PasswordHasher
import com.example.security.RoleCode
import com.example.util.DocumentExporter
import com.example.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

data class CartLine(
    val product: ProductEntity,
    val quantity: Int
)

data class PurchaseDraftLine(
    val product: ProductEntity,
    val quantity: Int,
    val unitCost: Double
)

enum class AdminResetScope(val title: String, val description: String) {
    OPERATIONS_ONLY(
        title = "Opérations, Ventes & Caisses",
        description = "Supprime ventes, factures, paiements en ligne, caisses, dépenses, achats, inventaires, mouvements de stock, rapports et alertes. Conserve la structure, les employés, le catalogue produits, les clients et les fournisseurs (compteurs remis à zéro)."
    ),
    CATALOG_AND_PARTNERS(
        title = "Catalogue, Stocks & Partenaires",
        description = "Supprime produits, catégories, clients, fournisseurs, achats, inventaires et mouvements de stock. Conserve la structure et les comptes utilisateurs."
    ),
    FULL_FACTORY_RESET(
        title = "Réinitialisation totale (Base 100 % vierge)",
        description = "Supprime toutes les données locales, y compris la structure et tous les comptes utilisateurs, pour repartir d'une application totalement vierge."
    )
}

class StoreRepository(
    private val db: AppDatabase,
    private val cloudSync: CloudSyncRepository
) {
    private val userDao = db.userDao()
    private val catalogDao = db.catalogDao()
    private val salesDao = db.salesDao()
    private val cashDao = db.cashDao()
    private val opsDao = db.operationsDao()
    private val adminDao = db.adminDao()
    private val onlinePaymentDao = db.onlinePaymentDao()

    // Reactive streams
    val users: Flow<List<UserEntity>> = userDao.observeAllUsers()
    val roles: Flow<List<RoleEntity>> = userDao.observeRoles()
    val permissionsCatalog: Flow<List<PermissionEntity>> = userDao.observePermissions()
    val userPermissions: Flow<List<UserPermissionEntity>> = userDao.observeAllUserPermissions()
    val categories: Flow<List<CategoryEntity>> = catalogDao.observeCategories()
    val products: Flow<List<ProductEntity>> = catalogDao.observeProducts()
    val suppliers: Flow<List<SupplierEntity>> = catalogDao.observeSuppliers()
    val customers: Flow<List<CustomerEntity>> = catalogDao.observeCustomers()
    val sales: Flow<List<SaleEntity>> = salesDao.observeSales()
    val saleItems: Flow<List<SaleItemEntity>> = salesDao.observeAllSaleItems()
    val payments: Flow<List<PaymentEntity>> = salesDao.observePayments()
    val cashRegisters: Flow<List<CashRegisterEntity>> = cashDao.observeCashRegisters()
    val openRegister: Flow<CashRegisterEntity?> = cashDao.observeOpenRegister()
    val cashMovements: Flow<List<CashMovementEntity>> = cashDao.observeCashMovements()
    val purchases: Flow<List<PurchaseEntity>> = opsDao.observePurchases()
    val purchaseItems: Flow<List<PurchaseItemEntity>> = opsDao.observeAllPurchaseItems()
    val stockMovements: Flow<List<StockMovementEntity>> = opsDao.observeStockMovements()
    val inventories: Flow<List<InventoryEntity>> = opsDao.observeInventories()
    val inventoryItems: Flow<List<InventoryItemEntity>> = opsDao.observeAllInventoryItems()
    val expenses: Flow<List<ExpenseEntity>> = adminDao.observeExpenses()
    val reports: Flow<List<ReportRecordEntity>> = adminDao.observeReports()
    val auditLogs: Flow<List<AuditLogEntity>> = adminDao.observeAuditLogs()
    val settings: Flow<StoreSettingsEntity?> = adminDao.observeSettings()
    val notifications: Flow<List<AlertNotificationEntity>> = adminDao.observeNotifications()
    val backups: Flow<List<BackupRecordEntity>> = adminDao.observeBackups()
    val invoices: Flow<List<InvoiceEntity>> = adminDao.observeInvoices()
    val documents: Flow<List<DocumentFileEntity>> = adminDao.observeDocuments()
    val onlinePayments: Flow<List<OnlinePaymentEntity>> = onlinePaymentDao.observeOnlinePayments()
    val onlinePaymentConfigs: Flow<List<OnlinePaymentConfigEntity>> = onlinePaymentDao.observeOnlinePaymentConfigs()

    suspend fun ensureSeeded(): Boolean {
        SeedDataInitializer.seedIfNeeded(db)
        return userDao.getAllUsersOnce().isNotEmpty()
    }

    suspend fun setupInitialStructureAndAdmin(
        storeName: String,
        city: String,
        location: String,
        address: String,
        storePhone: String,
        storeEmail: String,
        structureType: String = "GENERAL",
        logoKey: String = "GENERAL",
        adminFullName: String,
        adminUsername: String,
        adminPassword: String,
        adminPhone: String,
        adminWhatsapp: String,
        adminEmail: String,
        createManager: Boolean = false,
        managerFullName: String = "",
        managerUsername: String = "",
        managerPassword: String = "",
        managerPhone: String = "",
        managerWhatsapp: String = "",
        managerEmail: String = "",
        cloudUid: String? = null
    ): Result<Pair<UserEntity, Set<String>>> {
        if (storeName.isBlank()) return Result.failure(IllegalArgumentException("Le nom de la structure est obligatoire"))
        if (city.isBlank()) return Result.failure(IllegalArgumentException("La ville de la structure est obligatoire"))
        if (adminFullName.isBlank()) return Result.failure(IllegalArgumentException("Le nom complet de l'administrateur est obligatoire"))
        if (adminUsername.isBlank()) return Result.failure(IllegalArgumentException("L'identifiant administrateur est obligatoire"))
        if (adminPassword.length < 4) return Result.failure(IllegalArgumentException("Mot de passe admin : 4 caractères minimum"))
        if (createManager) {
            if (managerFullName.isBlank()) return Result.failure(IllegalArgumentException("Le nom complet du gestionnaire est obligatoire"))
            if (managerUsername.isBlank()) return Result.failure(IllegalArgumentException("L'identifiant du gestionnaire est obligatoire"))
            if (managerUsername.trim().equals(adminUsername.trim(), ignoreCase = true)) {
                return Result.failure(IllegalArgumentException("L'identifiant du gestionnaire doit être différent de celui de l'administrateur"))
            }
            if (managerPassword.length < 4) return Result.failure(IllegalArgumentException("Mot de passe gestionnaire : 4 caractères minimum"))
        }

        val now = System.currentTimeMillis()
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword(adminPassword, salt)

        val currentSettings = adminDao.getSettingsOnce() ?: StoreSettingsEntity()
        val updatedSettings = currentSettings.copy(
            id = 1,
            storeName = storeName.trim(),
            city = city.trim(),
            location = location.trim(),
            address = address.trim(),
            phone = storePhone.trim(),
            email = storeEmail.trim(),
            structureType = structureType.ifBlank { "GENERAL" },
            logoKey = logoKey.ifBlank { structureType.ifBlank { "GENERAL" } }
        )

        val setupResult = db.withTransaction {
            adminDao.upsertSettings(updatedSettings)
            userDao.insertRoles(RoleCode.entries.map { RoleEntity(it.code, it.displayName, it.description) })
            userDao.insertPermissions(AppPermission.entries.map { PermissionEntity(it.code, it.label, it.category) })

            val admin = UserEntity(
                username = adminUsername.trim(),
                fullName = adminFullName.trim(),
                phone = adminPhone.trim(),
                whatsappNumber = adminWhatsapp.trim().ifBlank { adminPhone.trim() },
                email = adminEmail.trim(),
                positionTitle = "Administrateur Principal",
                salaryFcfa = 0.0,
                hiredAt = now,
                passwordHash = hash,
                passwordSalt = salt,
                roleCode = RoleCode.ADMIN.code,
                isActive = true,
                lastLoginAt = now,
                createdAt = now
            )
            val uid = userDao.insertUser(admin)
            val allCodes = AppPermission.entries.map { it.code }.toSet()
            userDao.insertUserPermissions(
                allCodes.map { UserPermissionEntity(userId = uid, permissionCode = it, granted = true) }
            )
            adminDao.insertAuditLog(
                AuditLogEntity(
                    userId = uid,
                    userName = admin.fullName,
                    action = "Initialisation de la structure ${storeName.trim()} (${city.trim()})",
                    module = "Système",
                    oldValue = "Base vide",
                    newValue = "${admin.username} (ADMIN)",
                    deviceInfo = Formatters.deviceLabel(),
                    createdAt = now
                )
            )
            if (createManager) {
                val mgrSalt = PasswordHasher.generateSalt()
                val mgrHash = PasswordHasher.hashPassword(managerPassword, mgrSalt)
                val mgr = UserEntity(
                    username = managerUsername.trim(),
                    fullName = managerFullName.trim(),
                    phone = managerPhone.trim(),
                    whatsappNumber = managerWhatsapp.trim().ifBlank { managerPhone.trim() },
                    email = managerEmail.trim(),
                    positionTitle = "Gestionnaire du Magasin",
                    salaryFcfa = 0.0,
                    hiredAt = now,
                    passwordHash = mgrHash,
                    passwordSalt = mgrSalt,
                    roleCode = RoleCode.MANAGER.code,
                    isActive = true,
                    createdAt = now
                )
                val mgrId = userDao.insertUser(mgr)
                val mgrDefaults = AppPermission.defaultPermissionsForRole(RoleCode.MANAGER.code)
                userDao.insertUserPermissions(
                    AppPermission.entries.map {
                        UserPermissionEntity(userId = mgrId, permissionCode = it.code, granted = it.code in mgrDefaults)
                    }
                )
                adminDao.insertAuditLog(
                    AuditLogEntity(
                        userId = uid,
                        userName = admin.fullName,
                        action = "Création du compte Gestionnaire ${mgr.fullName} (@${mgr.username})",
                        module = "Système",
                        oldValue = "—",
                        newValue = "${mgr.username} (MANAGER)",
                        deviceInfo = Formatters.deviceLabel(),
                        createdAt = now
                    )
                )
            }
            Result.success(admin.copy(id = uid) to allCodes)
        }
        if (setupResult.isSuccess && !cloudUid.isNullOrBlank()) {
            cloudSync.upsertStoreProfile(cloudUid, updatedSettings)
        }
        return setupResult
    }

    suspend fun resetAllDataToEmpty() {
        withContext(Dispatchers.IO) {
            db.clearAllTables()
        }
        SeedDataInitializer.seedIfNeeded(db)
    }

    suspend fun performAdminReset(
        actor: UserEntity,
        adminPassword: String,
        scope: AdminResetScope,
        createBackupFirst: Boolean,
        alsoCleanCloud: Boolean,
        cloudUid: String?
    ): Result<String> {
        val freshActor = userDao.getUserById(actor.id)
            ?: return Result.failure(IllegalStateException("Compte introuvable"))
        val actorGranted = getPermissionsForUser(freshActor.id)
        val isAdmin = freshActor.roleCode == RoleCode.ADMIN.code
        val canResetScoped = isAdmin || (AppPermission.RESET_DATA.code in actorGranted)
        val authorized = freshActor.isActive && when (scope) {
            AdminResetScope.FULL_FACTORY_RESET -> isAdmin
            AdminResetScope.OPERATIONS_ONLY, AdminResetScope.CATALOG_AND_PARTNERS -> canResetScoped
        }
        if (!authorized) {
            recordUnauthorizedOrAlert(
                title = "Tentative de réinitialisation refusée",
                message = "${actor.fullName} (${actor.roleCode}) a tenté une réinitialisation (${scope.title}).",
                severity = "CRITICAL",
                type = "ACCES_REFUSE",
                userId = actor.id,
                userName = actor.fullName
            )
            return Result.failure(
                IllegalStateException(
                    if (scope == AdminResetScope.FULL_FACTORY_RESET) {
                        "La réinitialisation totale est strictement réservée à l'administrateur principal."
                    } else {
                        "Permission de réinitialisation requise (Administrateur ou Gestionnaire autorisé)."
                    }
                )
            )
        }
        if (adminPassword.isBlank() || !PasswordHasher.verifyPassword(adminPassword, freshActor.passwordSalt, freshActor.passwordHash)) {
            recordUnauthorizedOrAlert(
                title = "Mot de passe invalide (réinitialisation)",
                message = "Échec de vérification du mot de passe de ${freshActor.fullName} avant réinitialisation (${scope.title}).",
                severity = "CRITICAL",
                type = "ACCES_REFUSE",
                userId = freshActor.id,
                userName = freshActor.fullName
            )
            return Result.failure(IllegalArgumentException("Mot de passe de confirmation incorrect."))
        }

        val now = System.currentTimeMillis()
        if (createBackupFirst) {
            createBackupSnapshot(
                actor = freshActor,
                label = "Pré-réinitialisation · ${scope.title}",
                type = "AUTOMATIQUE",
                cloudUid = if (alsoCleanCloud) null else cloudUid
            )
        }

        val msg = when (scope) {
            AdminResetScope.OPERATIONS_ONLY -> {
                db.withTransaction {
                    salesDao.deleteAllSaleItems()
                    salesDao.deleteAllPayments()
                    salesDao.deleteAllSales()
                    onlinePaymentDao.deleteAllOnlinePayments()
                    adminDao.deleteAllInvoices()
                    cashDao.deleteAllCashMovements()
                    cashDao.deleteAllCashRegisters()
                    opsDao.deleteAllInventoryItems()
                    opsDao.deleteAllInventories()
                    opsDao.deleteAllPurchaseItems()
                    opsDao.deleteAllPurchases()
                    opsDao.deleteAllStockMovements()
                    adminDao.deleteAllExpenses()
                    adminDao.deleteAllReports()
                    adminDao.deleteAllNotifications()
                    catalogDao.resetCustomerBalances()
                    catalogDao.resetSupplierBalances()
                    val st = adminDao.getSettingsOnce()
                    if (st != null) {
                        adminDao.upsertSettings(st.copy(nextInvoiceNumber = 1001))
                    }
                    adminDao.insertAuditLog(
                        AuditLogEntity(
                            userId = freshActor.id,
                            userName = freshActor.fullName,
                            action = "${freshActor.fullName} a réinitialisé les opérations, ventes, caisses et factures.",
                            module = "Administration · Reset",
                            oldValue = "Données d'exploitation",
                            newValue = "Remis à zéro (catalogue & équipe conservés)",
                            deviceInfo = Formatters.deviceLabel(),
                            createdAt = now
                        )
                    )
                    adminDao.insertNotification(
                        AlertNotificationEntity(
                            type = "SYSTEME",
                            title = "Réinitialisation des opérations",
                            message = "Les ventes, factures, caisses et dépenses ont été remises à zéro par ${freshActor.fullName}.",
                            severity = "WARNING",
                            createdAt = now
                        )
                    )
                }
                "Opérations, ventes, factures et caisses remises à zéro."
            }
            AdminResetScope.CATALOG_AND_PARTNERS -> {
                db.withTransaction {
                    opsDao.deleteAllInventoryItems()
                    opsDao.deleteAllInventories()
                    opsDao.deleteAllPurchaseItems()
                    opsDao.deleteAllPurchases()
                    opsDao.deleteAllStockMovements()
                    catalogDao.deleteAllProducts()
                    catalogDao.deleteAllCategories()
                    catalogDao.deleteAllCustomers()
                    catalogDao.deleteAllSuppliers()
                    adminDao.insertAuditLog(
                        AuditLogEntity(
                            userId = freshActor.id,
                            userName = freshActor.fullName,
                            action = "${freshActor.fullName} a réinitialisé le catalogue produits et les partenaires.",
                            module = "Administration · Reset",
                            oldValue = "Produits, catégories, clients, fournisseurs",
                            newValue = "Remis à zéro (structure & employés conservés)",
                            deviceInfo = Formatters.deviceLabel(),
                            createdAt = now
                        )
                    )
                    adminDao.insertNotification(
                        AlertNotificationEntity(
                            type = "SYSTEME",
                            title = "Réinitialisation du catalogue",
                            message = "Produits, catégories, clients et fournisseurs remis à zéro par ${freshActor.fullName}.",
                            severity = "WARNING",
                            createdAt = now
                        )
                    )
                }
                "Catalogue produits, catégories, clients et fournisseurs remis à zéro."
            }
            AdminResetScope.FULL_FACTORY_RESET -> {
                resetAllDataToEmpty()
                if (alsoCleanCloud && !cloudUid.isNullOrBlank()) {
                    cloudSync.clearDeletableStoreData(cloudUid)
                }
                "Réinitialisation totale terminée : application remise à 100 % vide."
            }
        }
        return Result.success(msg)
    }

    // --- Staff Authentication & RBAC ---
    suspend fun authenticateStaff(username: String, password: String): Result<Pair<UserEntity, Set<String>>> {
        val user = userDao.getUserByUsername(username.trim())
            ?: return Result.failure(IllegalArgumentException("Identifiant introuvable"))
        if (!user.isActive) {
            recordUnauthorizedOrAlert(
                title = "Compte révoqué bloqué",
                message = "Tentative de connexion sur le compte désactivé : ${user.username}",
                severity = "CRITICAL",
                type = "ACCES_REFUSE",
                userId = user.id,
                userName = user.fullName
            )
            return Result.failure(IllegalStateException("Accès révoqué par l'administrateur"))
        }
        val valid = PasswordHasher.verifyPassword(password, user.passwordSalt, user.passwordHash)
        if (!valid) {
            recordUnauthorizedOrAlert(
                title = "Échec d'authentification",
                message = "Mot de passe incorrect pour l'utilisateur ${user.username}",
                severity = "WARNING",
                type = "ACCES_REFUSE",
                userId = user.id,
                userName = user.fullName
            )
            return Result.failure(IllegalArgumentException("Mot de passe incorrect"))
        }
        val now = System.currentTimeMillis()
        val updated = user.copy(lastLoginAt = now)
        userDao.updateUser(updated)
        if (user.roleCode == RoleCode.MANAGER.code) {
            val allRows = userDao.getAllPermissionsForUser(user.id)
            if (allRows.none { it.permissionCode == AppPermission.RESET_DATA.code }) {
                userDao.insertUserPermissions(
                    listOf(UserPermissionEntity(user.id, AppPermission.RESET_DATA.code, true))
                )
            }
        }
        val granted = userDao.getGrantedPermissionsForUser(user.id).map { it.permissionCode }.toSet()
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = user.id,
                userName = user.fullName,
                action = "${user.fullName} s'est connecté au terminal.",
                module = "Authentification",
                oldValue = "Hors session",
                newValue = "Session active (${user.roleCode})",
                deviceInfo = Formatters.deviceLabel(),
                createdAt = now
            )
        )
        adminDao.insertNotification(
            AlertNotificationEntity(
                type = "CONNEXION",
                title = "Nouvelle connexion",
                message = "${user.fullName} (${user.roleCode}) s'est connecté.",
                severity = "INFO",
                createdAt = now
            )
        )
        return Result.success(updated to granted)
    }

    suspend fun getPermissionsForUser(userId: Long): Set<String> =
        userDao.getGrantedPermissionsForUser(userId).map { it.permissionCode }.toSet()

    suspend fun recordUnauthorizedAttempt(actor: UserEntity?, actionDesc: String, module: String) {
        recordUnauthorizedOrAlert(
            title = "Tentative d'accès non autorisée",
            message = "${actor?.fullName ?: "Inconnu"} a tenté : $actionDesc ($module)",
            severity = "CRITICAL",
            type = "ACCES_REFUSE",
            userId = actor?.id ?: 0L,
            userName = actor?.fullName ?: "Inconnu"
        )
    }

    private suspend fun recordUnauthorizedOrAlert(
        title: String,
        message: String,
        severity: String,
        type: String,
        userId: Long,
        userName: String
    ) {
        val now = System.currentTimeMillis()
        adminDao.insertNotification(
            AlertNotificationEntity(type = type, title = title, message = message, severity = severity, createdAt = now)
        )
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = userId,
                userName = userName,
                action = message,
                module = "Sécurité",
                oldValue = "Non autorisé",
                newValue = "Bloqué",
                deviceInfo = Formatters.deviceLabel(),
                createdAt = now
            )
        )
    }

    // --- POS Sale + Automatic Invoice + Stock Deduction + Cash Update + Audit ---
    suspend fun validateSale(
        actor: UserEntity,
        cart: List<CartLine>,
        customer: CustomerEntity?,
        discountAmount: Double,
        paymentMethod: String,
        amountPaid: Double,
        paymentRef: String,
        cloudUid: String?
    ): Result<Pair<SaleEntity, InvoiceEntity>> {
        if (cart.isEmpty()) return Result.failure(IllegalArgumentException("Le panier est vide"))
        val now = System.currentTimeMillis()
        val settings = adminDao.getSettingsOnce() ?: StoreSettingsEntity()

        val result = db.withTransaction {
            val openReg = cashDao.getOpenRegisterOnce()
            val subtotal = cart.sumOf { it.product.sellingPrice * it.quantity }
            val safeDiscount = discountAmount.coerceIn(0.0, subtotal)
            val netHt = (subtotal - safeDiscount).coerceAtLeast(0.0)
            val taxRate = if (settings.taxEnabled) settings.vatRate else 0.0
            val taxAmount = netHt * (taxRate / 100.0)
            val totalTtc = netHt + taxAmount
            val totalCost = cart.sumOf { it.product.purchasePrice * it.quantity }
            val profit = netHt - totalCost
            val changeGiven = (amountPaid - totalTtc).coerceAtLeast(0.0)
            val balanceDue = (totalTtc - amountPaid).coerceAtLeast(0.0)

            val basePrefix = settings.invoicePrefix.ifBlank { "FAC" }
            val invoiceNum = "$basePrefix-${settings.nextInvoiceNumber}"
            val officialInvoiceNum = invoiceNum
            adminDao.upsertSettings(settings.copy(nextInvoiceNumber = settings.nextInvoiceNumber + 1))

            val sale = SaleEntity(
                invoiceNumber = invoiceNum,
                customerId = customer?.id ?: 0L,
                customerName = customer?.name ?: "Client Comptant",
                userId = actor.id,
                userName = actor.fullName,
                cashRegisterId = openReg?.id ?: 0L,
                subtotal = subtotal,
                discountAmount = safeDiscount,
                taxRate = taxRate,
                taxAmount = taxAmount,
                totalAmount = totalTtc,
                totalCost = totalCost,
                profit = profit,
                paymentMethod = paymentMethod,
                amountPaid = amountPaid,
                changeGiven = changeGiven,
                status = "VALIDEE",
                syncStatus = if (settings.offlineModeEnabled) "PENDING_SYNC" else "PENDING_SYNC",
                createdAt = now
            )
            val saleId = salesDao.insertSale(sale)
            val savedSale = sale.copy(id = saleId)

            val items = cart.map { line ->
                val catCode = Formatters.productInvoiceCode(officialInvoiceNum, line.product.categoryName)
                SaleItemEntity(
                    saleId = saleId,
                    productId = line.product.id,
                    productName = line.product.name,
                    sku = line.product.sku,
                    productBarcode = line.product.barcode.ifBlank { line.product.sku },
                    categoryName = line.product.categoryName,
                    productInvoiceCode = catCode,
                    quantity = line.quantity,
                    unitPrice = line.product.sellingPrice,
                    purchasePrice = line.product.purchasePrice,
                    lineTotal = line.product.sellingPrice * line.quantity,
                    lineProfit = (line.product.sellingPrice - line.product.purchasePrice) * line.quantity
                )
            }
            salesDao.insertSaleItems(items)
            salesDao.insertPayment(
                PaymentEntity(
                    saleId = saleId,
                    method = paymentMethod,
                    amount = amountPaid.coerceAtMost(totalTtc),
                    referenceCode = paymentRef,
                    createdAt = now
                )
            )

            // Deduct stock + stock movement + threshold alerts
            for (line in cart) {
                val freshProd = catalogDao.getProductById(line.product.id) ?: line.product
                val prev = freshProd.stockQuantity
                val next = (prev - line.quantity).coerceAtLeast(0)
                catalogDao.updateProduct(
                    freshProd.copy(
                        stockQuantity = next,
                        updatedAt = now,
                        syncStatus = "PENDING_SYNC"
                    )
                )
                opsDao.insertStockMovement(
                    StockMovementEntity(
                        productId = freshProd.id,
                        productName = freshProd.name,
                        type = "VENTE",
                        quantityDelta = -line.quantity,
                        previousStock = prev,
                        newStock = next,
                        unitCost = freshProd.purchasePrice,
                        reason = "Vente POS $invoiceNum",
                        referenceId = invoiceNum,
                        userId = actor.id,
                        userName = actor.fullName,
                        createdAt = now
                    )
                )
                if (next == 0) {
                    adminDao.insertNotification(
                        AlertNotificationEntity(
                            type = "RUPTURE",
                            title = "Rupture de stock",
                            message = "${freshProd.name} (${freshProd.sku}) est désormais en rupture (0).",
                            severity = "CRITICAL",
                            createdAt = now
                        )
                    )
                } else if (next <= freshProd.minThreshold) {
                    adminDao.insertNotification(
                        AlertNotificationEntity(
                            type = "STOCK_FAIBLE",
                            title = "Stock faible",
                            message = "${freshProd.name} : $next restants (seuil ${freshProd.minThreshold}).",
                            severity = "WARNING",
                            createdAt = now
                        )
                    )
                }
            }

            // Update customer stats / credit balance
            if (customer != null) {
                val freshCust = catalogDao.getCustomerById(customer.id) ?: customer
                catalogDao.updateCustomer(
                    freshCust.copy(
                        totalPurchased = freshCust.totalPurchased + totalTtc,
                        creditAmount = freshCust.creditAmount + balanceDue,
                        balance = freshCust.balance + balanceDue,
                        lastTransactionAt = now
                    )
                )
            }

            // Update open cash register
            if (openReg != null) {
                val isCash = paymentMethod.equals("Espèces", ignoreCase = true)
                val newCash = openReg.totalSalesCash + if (isCash) totalTtc else 0.0
                val newMobile = openReg.totalSalesMobile + if (!isCash) totalTtc else 0.0
                val newTheo = openReg.openingBalance + newCash + openReg.totalCashIn - openReg.totalCashOut - openReg.totalExpenses
                cashDao.updateCashRegister(
                    openReg.copy(
                        totalSalesCash = newCash,
                        totalSalesMobile = newMobile,
                        theoreticalBalance = newTheo
                    )
                )
                cashDao.insertCashMovement(
                    CashMovementEntity(
                        cashRegisterId = openReg.id,
                        type = "VENTE",
                        amount = totalTtc,
                        reason = "Vente $invoiceNum ($paymentMethod)",
                        userId = actor.id,
                        userName = actor.fullName,
                        createdAt = now
                    )
                )
            }

            // Automatic official invoice creation
            val invStatus = when {
                balanceDue <= 0.5 -> "PAYEE"
                amountPaid > 0.0 -> "PARTIELLE"
                else -> "IMPAYEE"
            }
            val productCodesNote = items.map { it.productInvoiceCode }.distinct().joinToString(", ")
            val invoice = InvoiceEntity(
                invoiceNumber = officialInvoiceNum,
                invoiceType = "FACTURE",
                saleId = saleId,
                customerId = customer?.id ?: 0L,
                customerName = customer?.name ?: "Client Comptant",
                customerPhone = customer?.phone ?: "",
                customerAddress = customer?.address ?: "",
                subtotalHt = subtotal,
                discountAmount = safeDiscount,
                taxRate = taxRate,
                taxAmount = taxAmount,
                totalTtc = totalTtc,
                amountPaid = amountPaid.coerceAtMost(totalTtc),
                balanceDue = balanceDue,
                paymentMethod = paymentMethod,
                status = invStatus,
                issuedByName = actor.fullName,
                notes = "Facture auto $officialInvoiceNum · Codes : $productCodesNote",
                createdAt = now
            )
            val invoiceId = adminDao.insertInvoice(invoice)

            // Immutable audit log
            adminDao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    action = "${actor.fullName} a enregistré une vente de ${Formatters.fcfa(totalTtc)} ($invoiceNum · $paymentMethod).",
                    module = "Point de vente",
                    oldValue = "Stock vérifié",
                    newValue = "Bénéfice ${Formatters.fcfa(profit)}",
                    deviceInfo = Formatters.deviceLabel(),
                    createdAt = now
                )
            )

            savedSale to invoice.copy(id = invoiceId)
        }

        // Opportunistic cloud sync if online mode
        if (!settings.offlineModeEnabled && !cloudUid.isNullOrBlank()) {
            syncPendingToCloud(cloudUid)
        }
        return Result.success(result)
    }

    suspend fun cancelSale(actor: UserEntity, sale: SaleEntity, cloudUid: String?): Result<Unit> {
        if (sale.status == "ANNULEE") return Result.failure(IllegalStateException("Vente déjà annulée"))
        val now = System.currentTimeMillis()
        db.withTransaction {
            salesDao.updateSale(sale.copy(status = "ANNULEE", syncStatus = "PENDING_SYNC"))
            val items = salesDao.getSaleItems(sale.id)
            for (item in items) {
                val prod = catalogDao.getProductById(item.productId) ?: continue
                val prev = prod.stockQuantity
                val next = prev + item.quantity
                catalogDao.updateProduct(prod.copy(stockQuantity = next, updatedAt = now, syncStatus = "PENDING_SYNC"))
                opsDao.insertStockMovement(
                    StockMovementEntity(
                        productId = prod.id,
                        productName = prod.name,
                        type = "RETOUR_CLIENT",
                        quantityDelta = item.quantity,
                        previousStock = prev,
                        newStock = next,
                        unitCost = prod.purchasePrice,
                        reason = "Annulation vente ${sale.invoiceNumber}",
                        referenceId = sale.invoiceNumber,
                        userId = actor.id,
                        userName = actor.fullName,
                        createdAt = now
                    )
                )
            }
            adminDao.getInvoiceBySaleId(sale.id)?.let { inv ->
                adminDao.updateInvoice(inv.copy(status = "ANNULEE"))
            }
            adminDao.insertNotification(
                AlertNotificationEntity(
                    type = "VENTE_ANNULEE",
                    title = "Vente annulée",
                    message = "${sale.invoiceNumber} (${Formatters.fcfa(sale.totalAmount)}) annulée par ${actor.fullName}.",
                    severity = "WARNING",
                    createdAt = now
                )
            )
            adminDao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    action = "${actor.fullName} a annulé la vente ${sale.invoiceNumber} (${Formatters.fcfa(sale.totalAmount)}).",
                    module = "Ventes",
                    oldValue = "VALIDEE",
                    newValue = "ANNULEE (Stock restauré)",
                    deviceInfo = Formatters.deviceLabel(),
                    createdAt = now
                )
            )
        }
        if (!cloudUid.isNullOrBlank()) {
            cloudSync.cancelSale(cloudUid, sale.invoiceNumber)
            syncPendingToCloud(cloudUid)
        }
        return Result.success(Unit)
    }

    suspend fun createManualInvoice(
        actor: UserEntity,
        invoiceType: String,
        customer: CustomerEntity?,
        customerNameFallback: String,
        amountHt: Double,
        discount: Double,
        paymentMethod: String,
        amountPaid: Double,
        notes: String,
        selectedLines: List<CartLine> = emptyList()
    ): InvoiceEntity {
        val now = System.currentTimeMillis()
        val settings = adminDao.getSettingsOnce() ?: StoreSettingsEntity()
        val computedHt = if (selectedLines.isNotEmpty()) {
            selectedLines.sumOf { it.product.sellingPrice * it.quantity }
        } else {
            amountHt
        }
        val safeDiscount = discount.coerceIn(0.0, computedHt)
        val netHt = (computedHt - safeDiscount).coerceAtLeast(0.0)
        val taxRate = if (settings.taxEnabled) settings.vatRate else 0.0
        val taxAmt = netHt * (taxRate / 100.0)
        val totalTtc = netHt + taxAmt
        val effectivePaid = if (invoiceType == "PROFORMA") 0.0 else amountPaid.coerceAtMost(totalTtc)
        val due = (totalTtc - effectivePaid).coerceAtLeast(0.0)
        val num = when (invoiceType) {
            "PROFORMA" -> "PRO-${settings.nextInvoiceNumber}"
            "AVOIR" -> "AVR-${settings.nextInvoiceNumber}"
            else -> "${settings.invoicePrefix.ifBlank { "FAC" }}-${settings.nextInvoiceNumber}"
        }
        adminDao.upsertSettings(settings.copy(nextInvoiceNumber = settings.nextInvoiceNumber + 1))

        val status = when {
            invoiceType == "PROFORMA" -> "PROFORMA"
            due <= 0.5 -> "PAYEE"
            effectivePaid > 0.0 -> "PARTIELLE"
            else -> "IMPAYEE"
        }

        val itemCodes = selectedLines.map {
            Formatters.productInvoiceCode(num, it.product.categoryName)
        }.distinct()
        val fullNotes = buildString {
            if (notes.isNotBlank()) append(notes.trim())
            if (itemCodes.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append("Codes : ${itemCodes.joinToString(", ")}")
            }
        }

        val inv = InvoiceEntity(
            invoiceNumber = num,
            invoiceType = invoiceType,
            customerId = customer?.id ?: 0L,
            customerName = customer?.name ?: customerNameFallback.ifBlank { "Client Comptant" },
            customerPhone = customer?.phone ?: "",
            customerAddress = customer?.address ?: "",
            subtotalHt = computedHt,
            discountAmount = safeDiscount,
            taxRate = taxRate,
            taxAmount = taxAmt,
            totalTtc = totalTtc,
            amountPaid = effectivePaid,
            balanceDue = due,
            paymentMethod = paymentMethod,
            status = status,
            dueDate = now + 15 * 86_400_000L,
            issuedByName = actor.fullName,
            notes = fullNotes,
            createdAt = now
        )
        val id = db.withTransaction {
            val insertedId = adminDao.insertInvoice(inv)
            if (selectedLines.isNotEmpty()) {
                val items = selectedLines.map { line ->
                    SaleItemEntity(
                        saleId = -insertedId,
                        productId = line.product.id,
                        productName = line.product.name,
                        sku = line.product.sku,
                        productBarcode = line.product.barcode.ifBlank { line.product.sku },
                        categoryName = line.product.categoryName,
                        productInvoiceCode = Formatters.productInvoiceCode(num, line.product.categoryName),
                        quantity = line.quantity,
                        unitPrice = line.product.sellingPrice,
                        purchasePrice = line.product.purchasePrice,
                        lineTotal = line.product.sellingPrice * line.quantity,
                        lineProfit = (line.product.sellingPrice - line.product.purchasePrice) * line.quantity
                    )
                }
                salesDao.insertSaleItems(items)
            }
            adminDao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    action = "${actor.fullName} a émis une facture $invoiceType ($num) de ${Formatters.fcfa(totalTtc)}.",
                    module = "Facturation",
                    oldValue = "—",
                    newValue = status,
                    deviceInfo = Formatters.deviceLabel(),
                    createdAt = now
                )
            )
            insertedId
        }
        return inv.copy(id = id)
    }

    suspend fun convertProformaToInvoice(
        actor: UserEntity,
        proforma: InvoiceEntity,
        paymentMethod: String = proforma.paymentMethod,
        amountPaid: Double = proforma.totalTtc
    ): InvoiceEntity {
        if (proforma.invoiceType != "PROFORMA") return proforma
        val now = System.currentTimeMillis()
        val settings = adminDao.getSettingsOnce() ?: StoreSettingsEntity()
        val newNum = "${settings.invoicePrefix.ifBlank { "FAC" }}-${settings.nextInvoiceNumber}"
        adminDao.upsertSettings(settings.copy(nextInvoiceNumber = settings.nextInvoiceNumber + 1))
        val safePaid = amountPaid.coerceIn(0.0, proforma.totalTtc)
        val newDue = (proforma.totalTtc - safePaid).coerceAtLeast(0.0)
        val newStatus = when {
            newDue <= 0.5 -> "PAYEE"
            safePaid > 0.0 -> "PARTIELLE"
            else -> "IMPAYEE"
        }
        val converted = proforma.copy(
            invoiceNumber = newNum,
            invoiceType = "FACTURE",
            paymentMethod = paymentMethod,
            amountPaid = safePaid,
            balanceDue = newDue,
            status = newStatus,
            notes = "${proforma.notes} · Convertie depuis ${proforma.invoiceNumber}".trim(' ', '·')
        )
        db.withTransaction {
            adminDao.updateInvoice(converted)
            val attachedItems = salesDao.getSaleItems(-proforma.id)
            if (attachedItems.isNotEmpty()) {
                salesDao.insertSaleItems(
                    attachedItems.map { item ->
                        item.copy(
                            productInvoiceCode = Formatters.productInvoiceCode(newNum, item.categoryName)
                        )
                    }
                )
            }
            adminDao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    action = "${actor.fullName} a converti la proforma ${proforma.invoiceNumber} en facture officielle $newNum.",
                    module = "Facturation",
                    oldValue = proforma.invoiceNumber,
                    newValue = newNum,
                    deviceInfo = Formatters.deviceLabel(),
                    createdAt = now
                )
            )
        }
        return converted
    }

    suspend fun settleInvoicePayment(actor: UserEntity, invoice: InvoiceEntity, paymentAmount: Double) {
        val now = System.currentTimeMillis()
        val newPaid = (invoice.amountPaid + paymentAmount).coerceAtMost(invoice.totalTtc)
        val newDue = (invoice.totalTtc - newPaid).coerceAtLeast(0.0)
        val newStatus = if (newDue <= 0.5) "PAYEE" else "PARTIELLE"
        adminDao.updateInvoice(invoice.copy(amountPaid = newPaid, balanceDue = newDue, status = newStatus))
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = actor.id,
                userName = actor.fullName,
                action = "${actor.fullName} a encaissé ${Formatters.fcfa(paymentAmount)} sur ${invoice.invoiceNumber}.",
                module = "Facturation",
                oldValue = invoice.status,
                newValue = newStatus,
                deviceInfo = Formatters.deviceLabel(),
                createdAt = now
            )
        )
    }

    // --- Products & Categories ---
    /**
     * Synchro entrante (cloud -> téléphone) : applique dans la base locale les produits et ventes
     * créés ou modifiés depuis la version ordinateur. Le plus récent gagne (clientUpdatedAt).
     */
    suspend fun applyCloudChanges(
        cloudUid: String,
        products: List<com.example.data.remote.CloudProduct>,
        sales: List<com.example.data.remote.CloudSale>
    ) {
        val local = catalogDao.getProductsOnce()
        for (c in products) {
            if (c.status !in listOf("ACTIF", "INACTIF", "ARCHIVE") || c.name.isBlank()) continue
            val key = c.sku.ifBlank { c.name }.trim().lowercase()
            val match = if (c.localId > 0L) {
                local.firstOrNull { it.id == c.localId }
            } else {
                local.firstOrNull { it.sku.ifBlank { it.name }.trim().lowercase() == key }
            }
            if (match == null) {
                if (c.localId > 0L) continue // supprimé sur le téléphone : on ne le recrée pas
                val newId = catalogDao.insertProduct(
                    ProductEntity(
                        name = c.name, reference = c.sku.ifBlank { "REF-WEB" }, sku = c.sku, barcode = c.barcode,
                        categoryId = 0L, categoryName = c.categoryName,
                        purchasePrice = c.purchasePrice, sellingPrice = c.sellingPrice,
                        stockQuantity = c.stockQuantity.toInt(), minThreshold = c.minThreshold.toInt(),
                        unit = c.unit, status = c.status,
                        createdAt = c.clientUpdatedAt, updatedAt = c.clientUpdatedAt, syncStatus = "SYNCED"
                    )
                )
                val saved = catalogDao.getProductById(newId) ?: continue
                // Republier sous l'identifiant du téléphone, puis retirer le doublon créé par le PC
                if (cloudSync.upsertProduct(cloudUid, saved).isSuccess) cloudSync.deleteProductDoc(cloudUid, c.id)
            } else {
                var current = match
                if (c.clientUpdatedAt > match.updatedAt) {
                    current = match.copy(
                        name = c.name, sku = c.sku, barcode = c.barcode, categoryName = c.categoryName,
                        purchasePrice = c.purchasePrice, sellingPrice = c.sellingPrice,
                        stockQuantity = c.stockQuantity.toInt(), minThreshold = c.minThreshold.toInt(),
                        unit = c.unit, status = c.status, updatedAt = c.clientUpdatedAt, syncStatus = "SYNCED"
                    )
                    catalogDao.updateProduct(current)
                }
                if (c.localId == 0L && cloudSync.upsertProduct(cloudUid, current).isSuccess) {
                    cloudSync.deleteProductDoc(cloudUid, c.id)
                }
            }
        }
        val byInvoice = salesDao.getSalesOnce().associateBy { it.invoiceNumber }
        for (c in sales) {
            val s = byInvoice[c.invoiceNumber]
            if (s == null) {
                // On n'importe que les ventes faites sur PC : une vente supprimée ici ne revient pas.
                if (!c.invoiceNumber.startsWith("WEB-")) continue
                val newSaleId = salesDao.insertSale(
                    SaleEntity(
                        invoiceNumber = c.invoiceNumber,
                        customerName = c.customerName.ifBlank { "Client Comptant" },
                        userId = 0L, userName = c.cashierName,
                        subtotal = c.totalAmount, totalAmount = c.totalAmount,
                        totalCost = c.totalAmount - c.profit, profit = c.profit,
                        paymentMethod = c.paymentMethod, amountPaid = c.totalAmount,
                        status = c.status, syncStatus = "SYNCED",
                        createdAt = if (c.clientCreatedAt > 0L) c.clientCreatedAt else System.currentTimeMillis()
                    )
                )
                val lines = c.items.mapNotNull { m ->
                    val qty = (m["qty"] as? Number)?.toInt() ?: return@mapNotNull null
                    val sku = (m["sku"] as? String).orEmpty()
                    val name = (m["name"] as? String).orEmpty()
                    val price = (m["price"] as? Number)?.toDouble() ?: 0.0
                    val cost = (m["cost"] as? Number)?.toDouble() ?: 0.0
                    val prod = local.firstOrNull { sku.isNotBlank() && it.sku == sku } ?: local.firstOrNull { it.name == name }
                    SaleItemEntity(
                        saleId = newSaleId, productId = prod?.id ?: 0L, productName = name, sku = sku,
                        quantity = qty, unitPrice = price, purchasePrice = cost,
                        lineTotal = price * qty, lineProfit = (price - cost) * qty
                    )
                }
                if (lines.isNotEmpty()) salesDao.insertSaleItems(lines)
            } else if (c.status == "ANNULEE" && s.status != "ANNULEE") {
                salesDao.updateSale(s.copy(status = "ANNULEE", syncStatus = "SYNCED"))
            }
        }
    }

    suspend fun saveProduct(actor: UserEntity, product: ProductEntity, isNew: Boolean, cloudUid: String?): Long {
        val now = System.currentTimeMillis()
        val settings = adminDao.getSettingsOnce() ?: StoreSettingsEntity()
        val old = if (!isNew) catalogDao.getProductById(product.id) else null
        val invoiceBase = "${settings.invoicePrefix.ifBlank { "FAC" }}-${settings.nextInvoiceNumber}"
        val autoRef = Formatters.autoProductReference(invoiceBase, product.categoryName)
        val finalRef = when {
            product.reference.isBlank() || product.reference.startsWith("REF-") -> autoRef
            old != null && old.categoryName != product.categoryName -> autoRef
            else -> product.reference.trim()
        }
        val finalBarcode = product.barcode.ifBlank {
            "617${(now % 1_000_000_000L).toString().padStart(9, '0')}"
        }
        val toSave = product.copy(
            reference = finalRef,
            barcode = finalBarcode,
            updatedAt = now,
            createdAt = if (isNew) now else product.createdAt,
            syncStatus = "PENDING_SYNC"
        )
        val savedId = catalogDao.insertProduct(toSave)
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = actor.id,
                userName = actor.fullName,
                action = if (isNew) {
                    "${actor.fullName} a créé le produit ${product.name} ($finalRef · ${Formatters.fcfa(product.sellingPrice)})."
                } else {
                    "${actor.fullName} a modifié le produit ${product.name} ($finalRef)."
                },
                module = "Produits",
                oldValue = old?.let { "${Formatters.fcfa(it.sellingPrice)} / Qté ${it.stockQuantity}" } ?: "Nouveau",
                newValue = "${Formatters.fcfa(product.sellingPrice)} / Qté ${product.stockQuantity}",
                deviceInfo = Formatters.deviceLabel(),
                createdAt = now
            )
        )
        if (!cloudUid.isNullOrBlank()) {
            val saved = toSave.copy(id = savedId)
            if (cloudSync.upsertProduct(cloudUid, saved).isSuccess) {
                catalogDao.markProductSynced(savedId)
            }
        }
        return savedId
    }

    suspend fun deleteProduct(actor: UserEntity, product: ProductEntity, cloudUid: String?) {
        val now = System.currentTimeMillis()
        catalogDao.deleteProduct(product.id)
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = actor.id,
                userName = actor.fullName,
                action = "${actor.fullName} a supprimé le produit ${product.name} (${product.sku}).",
                module = "Produits",
                oldValue = product.name,
                newValue = "Supprimé",
                deviceInfo = Formatters.deviceLabel(),
                createdAt = now
            )
        )
        if (!cloudUid.isNullOrBlank()) {
            cloudSync.deleteProduct(cloudUid, product)
        }
    }

    suspend fun addCategory(actor: UserEntity, name: String, subCategory: String, colorHex: String) {
        catalogDao.insertCategory(CategoryEntity(name = name, subCategory = subCategory, colorHex = colorHex))
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = actor.id,
                userName = actor.fullName,
                action = "${actor.fullName} a ajouté la catégorie $name.",
                module = "Produits",
                oldValue = "—",
                newValue = name,
                deviceInfo = Formatters.deviceLabel()
            )
        )
    }

    // --- Stock Movements ---
    suspend fun recordStockAdjustment(
        actor: UserEntity,
        product: ProductEntity,
        movementType: String,
        quantity: Int,
        reason: String
    ) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val fresh = catalogDao.getProductById(product.id) ?: product
            val isAddition = movementType in setOf("ENTREE", "RECEPTION", "RETOUR_CLIENT")
            val delta = if (isAddition) kotlin.math.abs(quantity) else -kotlin.math.abs(quantity)
            val prev = fresh.stockQuantity
            val next = (prev + delta).coerceAtLeast(0)
            catalogDao.updateProduct(fresh.copy(stockQuantity = next, updatedAt = now, syncStatus = "PENDING_SYNC"))
            opsDao.insertStockMovement(
                StockMovementEntity(
                    productId = fresh.id,
                    productName = fresh.name,
                    type = movementType,
                    quantityDelta = delta,
                    previousStock = prev,
                    newStock = next,
                    unitCost = fresh.purchasePrice,
                    reason = reason.ifBlank { movementType },
                    referenceId = "STK-${now % 10000}",
                    userId = actor.id,
                    userName = actor.fullName,
                    createdAt = now
                )
            )
            if (next == 0) {
                adminDao.insertNotification(
                    AlertNotificationEntity(
                        type = "RUPTURE",
                        title = "Rupture de stock",
                        message = "${fresh.name} est en rupture de stock.",
                        severity = "CRITICAL",
                        createdAt = now
                    )
                )
            } else if (next <= fresh.minThreshold) {
                adminDao.insertNotification(
                    AlertNotificationEntity(
                        type = "STOCK_FAIBLE",
                        title = "Stock faible",
                        message = "${fresh.name} ($next restants / seuil ${fresh.minThreshold}).",
                        severity = "WARNING",
                        createdAt = now
                    )
                )
            }
            adminDao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    action = "${actor.fullName} a enregistré un mouvement de stock ($movementType) sur ${fresh.name}.",
                    module = "Stocks",
                    oldValue = "$prev",
                    newValue = "$next ($reason)",
                    deviceInfo = Formatters.deviceLabel(),
                    createdAt = now
                )
            )
        }
    }

    // --- Periodic & Automatic Inventories ---
    suspend fun scheduleInventory(
        actor: UserEntity,
        title: String,
        frequency: String,
        scheduledDate: Long
    ): Long {
        val now = System.currentTimeMillis()
        val productsList = catalogDao.getProductsOnce()
        return db.withTransaction {
            val totalTheo = productsList.sumOf { it.stockQuantity * it.purchasePrice }
            val invId = opsDao.insertInventory(
                InventoryEntity(
                    title = title.ifBlank { "Inventaire $frequency" },
                    frequency = frequency,
                    scheduledDate = scheduledDate,
                    status = "EN_COURS",
                    totalTheoreticalValue = totalTheo,
                    totalActualValue = totalTheo,
                    createdByUserId = actor.id,
                    createdByName = actor.fullName,
                    createdAt = now
                )
            )
            opsDao.insertInventoryItems(
                productsList.map { prod ->
                    InventoryItemEntity(
                        inventoryId = invId,
                        productId = prod.id,
                        productName = prod.name,
                        sku = prod.sku,
                        theoreticalQty = prod.stockQuantity,
                        actualQty = prod.stockQuantity,
                        unitCost = prod.purchasePrice,
                        isCounted = false
                    )
                }
            )
            adminDao.insertNotification(
                AlertNotificationEntity(
                    type = "INVENTAIRE_ATTENTE",
                    title = "Inventaire programmé prêt",
                    message = "$title ($frequency) préparé avec ${productsList.size} produits.",
                    severity = "INFO",
                    createdAt = now
                )
            )
            adminDao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    action = "${actor.fullName} a programmé l'inventaire $title ($frequency).",
                    module = "Inventaires",
                    oldValue = "—",
                    newValue = "${productsList.size} produits",
                    deviceInfo = Formatters.deviceLabel(),
                    createdAt = now
                )
            )
            invId
        }
    }

    suspend fun updateInventoryItemCount(
        inventoryId: Long,
        item: InventoryItemEntity,
        actualQty: Int,
        damagedQty: Int,
        note: String
    ) {
        val discQty = actualQty - item.theoreticalQty
        val discVal = discQty * item.unitCost
        opsDao.updateInventoryItem(
            item.copy(
                actualQty = actualQty.coerceAtLeast(0),
                damagedQty = damagedQty.coerceAtLeast(0),
                discrepancyQty = discQty,
                discrepancyValue = discVal,
                note = note,
                isCounted = true
            )
        )
        // Recompute parent inventory summary
        val allItems = opsDao.getInventoryItems(inventoryId)
        val theoVal = allItems.sumOf { it.theoreticalQty * it.unitCost }
        val actVal = allItems.sumOf { it.actualQty * it.unitCost }
        val discTotal = actVal - theoVal
        val missing = allItems.count { it.discrepancyQty < 0 }
        val surplus = allItems.count { it.discrepancyQty > 0 }
        val damaged = allItems.sumOf { it.damagedQty }
        val allCounted = allItems.all { it.isCounted }
        opsDao.updateInventorySummary(
            inventoryId = inventoryId,
            status = if (allCounted) "EN_ATTENTE_VALIDATION" else "EN_COURS",
            theoVal = theoVal,
            actVal = actVal,
            discVal = discTotal,
            missing = missing,
            surplus = surplus,
            damaged = damaged
        )
    }

    suspend fun submitInventoryForValidation(actor: UserEntity, inventory: InventoryEntity) {
        val items = opsDao.getInventoryItems(inventory.id)
        val theoVal = items.sumOf { it.theoreticalQty * it.unitCost }
        val actVal = items.sumOf { it.actualQty * it.unitCost }
        opsDao.updateInventory(
            inventory.copy(
                status = "EN_ATTENTE_VALIDATION",
                totalTheoreticalValue = theoVal,
                totalActualValue = actVal,
                totalDiscrepancyValue = actVal - theoVal,
                missingCount = items.count { it.discrepancyQty < 0 },
                surplusCount = items.count { it.discrepancyQty > 0 },
                damagedCount = items.sumOf { it.damagedQty }
            )
        )
        adminDao.insertNotification(
            AlertNotificationEntity(
                type = "INVENTAIRE_ATTENTE",
                title = "Inventaire prêt à valider",
                message = "${inventory.title} soumis par ${actor.fullName} (Écart : ${Formatters.fcfa(actVal - theoVal)}).",
                severity = "WARNING"
            )
        )
    }

    suspend fun validateInventory(actor: UserEntity, inventory: InventoryEntity) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val items = opsDao.getInventoryItems(inventory.id)
            for (item in items) {
                val prod = catalogDao.getProductById(item.productId) ?: continue
                if (prod.stockQuantity != item.actualQty) {
                    val delta = item.actualQty - prod.stockQuantity
                    catalogDao.updateProduct(prod.copy(stockQuantity = item.actualQty, updatedAt = now, syncStatus = "PENDING_SYNC"))
                    opsDao.insertStockMovement(
                        StockMovementEntity(
                            productId = prod.id,
                            productName = prod.name,
                            type = "INVENTAIRE",
                            quantityDelta = delta,
                            previousStock = prod.stockQuantity,
                            newStock = item.actualQty,
                            unitCost = prod.purchasePrice,
                            reason = "Correction inventaire ${inventory.title}",
                            referenceId = "INV-${inventory.id}",
                            userId = actor.id,
                            userName = actor.fullName,
                            createdAt = now
                        )
                    )
                }
            }
            val theoVal = items.sumOf { it.theoreticalQty * it.unitCost }
            val actVal = items.sumOf { it.actualQty * it.unitCost }
            opsDao.updateInventory(
                inventory.copy(
                    status = "VALIDE",
                    totalTheoreticalValue = theoVal,
                    totalActualValue = actVal,
                    totalDiscrepancyValue = actVal - theoVal,
                    missingCount = items.count { it.discrepancyQty < 0 },
                    surplusCount = items.count { it.discrepancyQty > 0 },
                    damagedCount = items.sumOf { it.damagedQty },
                    validatedByUserId = actor.id,
                    validatedByName = actor.fullName,
                    validatedAt = now
                )
            )
            adminDao.insertReport(
                ReportRecordEntity(
                    reportType = "INVENTAIRE",
                    title = "Rapport d'inventaire : ${inventory.title}",
                    periodLabel = Formatters.date(now),
                    summaryText = "Théorique ${Formatters.fcfa(theoVal)} · Réel ${Formatters.fcfa(actVal)} · Écart ${Formatters.fcfa(actVal - theoVal)}",
                    generatedByName = actor.fullName,
                    createdAt = now
                )
            )
            adminDao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    action = "${actor.fullName} a validé l'inventaire ${inventory.title} (Écart : ${Formatters.fcfa(actVal - theoVal)}).",
                    module = "Inventaires",
                    oldValue = "EN_ATTENTE_VALIDATION",
                    newValue = "VALIDE (Stocks corrigés)",
                    deviceInfo = Formatters.deviceLabel(),
                    createdAt = now
                )
            )
        }
    }

    // --- Purchases & Suppliers ---
    suspend fun createPurchaseOrder(
        actor: UserEntity,
        supplier: SupplierEntity,
        lines: List<PurchaseDraftLine>,
        status: String,
        notes: String
    ): Long {
        val now = System.currentTimeMillis()
        val total = lines.sumOf { it.quantity * it.unitCost }
        return db.withTransaction {
            val orderNum = "ACH-${now % 100000}"
            val pId = opsDao.insertPurchase(
                PurchaseEntity(
                    orderNumber = orderNum,
                    supplierId = supplier.id,
                    supplierName = supplier.company.ifBlank { supplier.name },
                    userId = actor.id,
                    userName = actor.fullName,
                    status = status,
                    totalAmount = total,
                    notes = notes,
                    createdAt = now
                )
            )
            opsDao.insertPurchaseItems(
                lines.map {
                    PurchaseItemEntity(
                        purchaseId = pId,
                        productId = it.product.id,
                        productName = it.product.name,
                        orderedQty = it.quantity,
                        receivedQty = 0,
                        unitCost = it.unitCost,
                        lineTotal = it.quantity * it.unitCost
                    )
                }
            )
            adminDao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    action = "${actor.fullName} a créé la commande fournisseur $orderNum (${Formatters.fcfa(total)}).",
                    module = "Achats",
                    oldValue = "—",
                    newValue = status,
                    deviceInfo = Formatters.deviceLabel(),
                    createdAt = now
                )
            )
            pId
        }
    }

    suspend fun receivePurchaseOrder(actor: UserEntity, purchase: PurchaseEntity, partial: Boolean = false) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val items = opsDao.getPurchaseItems(purchase.id)
            for (item in items) {
                val qtyToAdd = (item.orderedQty - item.receivedQty).coerceAtLeast(0)
                if (qtyToAdd <= 0) continue
                val addNow = if (partial) (qtyToAdd / 2).coerceAtLeast(1) else qtyToAdd
                opsDao.updatePurchaseItem(item.copy(receivedQty = item.receivedQty + addNow))
                val prod = catalogDao.getProductById(item.productId) ?: continue
                val prev = prod.stockQuantity
                val next = prev + addNow
                catalogDao.updateProduct(
                    prod.copy(
                        stockQuantity = next,
                        purchasePrice = item.unitCost,
                        updatedAt = now,
                        syncStatus = "PENDING_SYNC"
                    )
                )
                opsDao.insertStockMovement(
                    StockMovementEntity(
                        productId = prod.id,
                        productName = prod.name,
                        type = "RECEPTION",
                        quantityDelta = addNow,
                        previousStock = prev,
                        newStock = next,
                        unitCost = item.unitCost,
                        reason = "Réception commande ${purchase.orderNumber}",
                        referenceId = purchase.orderNumber,
                        userId = actor.id,
                        userName = actor.fullName,
                        createdAt = now
                    )
                )
            }
            val newStatus = if (partial) "PARTIELLEMENT_RECU" else "RECU"
            opsDao.updatePurchase(purchase.copy(status = newStatus, receivedAt = now))
            catalogDao.getSupplierById(purchase.supplierId)?.let { sup ->
                catalogDao.updateSupplier(sup.copy(totalPurchased = sup.totalPurchased + purchase.totalAmount))
            }
            adminDao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    action = "${actor.fullName} a enregistré la réception de ${purchase.orderNumber} (${Formatters.fcfa(purchase.totalAmount)}).",
                    module = "Achats",
                    oldValue = purchase.status,
                    newValue = newStatus,
                    deviceInfo = Formatters.deviceLabel(),
                    createdAt = now
                )
            )
        }
    }

    suspend fun saveSupplier(actor: UserEntity, supplier: SupplierEntity) {
        catalogDao.insertSupplier(supplier)
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = actor.id,
                userName = actor.fullName,
                action = "${actor.fullName} a enregistré le fournisseur ${supplier.company.ifBlank { supplier.name }}.",
                module = "Fournisseurs",
                oldValue = "—",
                newValue = supplier.phone,
                deviceInfo = Formatters.deviceLabel()
            )
        )
    }

    suspend fun saveCustomer(actor: UserEntity, customer: CustomerEntity) {
        catalogDao.insertCustomer(customer)
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = actor.id,
                userName = actor.fullName,
                action = "${actor.fullName} a enregistré le client ${customer.name}.",
                module = "Clients",
                oldValue = "—",
                newValue = customer.phone,
                deviceInfo = Formatters.deviceLabel()
            )
        )
    }

    suspend fun settleCustomerCredit(actor: UserEntity, customer: CustomerEntity, amount: Double) {
        val now = System.currentTimeMillis()
        val newBalance = (customer.balance - amount).coerceAtLeast(0.0)
        catalogDao.updateCustomer(customer.copy(balance = newBalance, creditAmount = newBalance, lastTransactionAt = now))
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = actor.id,
                userName = actor.fullName,
                action = "${actor.fullName} a encaissé un règlement crédit de ${Formatters.fcfa(amount)} pour ${customer.name}.",
                module = "Clients",
                oldValue = Formatters.fcfa(customer.balance),
                newValue = Formatters.fcfa(newBalance),
                deviceInfo = Formatters.deviceLabel(),
                createdAt = now
            )
        )
    }

    // --- Cash Register ---
    suspend fun openCashRegister(actor: UserEntity, openingBalance: Double): Result<Long> {
        if (cashDao.getOpenRegisterOnce() != null) {
            return Result.failure(IllegalStateException("Une caisse est déjà ouverte"))
        }
        val now = System.currentTimeMillis()
        val id = cashDao.insertCashRegister(
            CashRegisterEntity(
                openedByUserId = actor.id,
                openedByName = actor.fullName,
                openingBalance = openingBalance,
                theoreticalBalance = openingBalance,
                status = "OUVERTE",
                openedAt = now
            )
        )
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = actor.id,
                userName = actor.fullName,
                action = "${actor.fullName} a ouvert la caisse avec un fonds initial de ${Formatters.fcfa(openingBalance)}.",
                module = "Caisse",
                oldValue = "FERMEE",
                newValue = "OUVERTE (${Formatters.fcfa(openingBalance)})",
                deviceInfo = Formatters.deviceLabel(),
                createdAt = now
            )
        )
        return Result.success(id)
    }

    suspend fun recordCashMovement(actor: UserEntity, type: String, amount: Double, reason: String): Result<Unit> {
        val reg = cashDao.getOpenRegisterOnce()
            ?: return Result.failure(IllegalStateException("Aucune caisse ouverte"))
        val now = System.currentTimeMillis()
        db.withTransaction {
            cashDao.insertCashMovement(
                CashMovementEntity(
                    cashRegisterId = reg.id,
                    type = type,
                    amount = amount,
                    reason = reason,
                    userId = actor.id,
                    userName = actor.fullName,
                    createdAt = now
                )
            )
            val newIn = reg.totalCashIn + if (type == "ENTREE") amount else 0.0
            val newOut = reg.totalCashOut + if (type in setOf("SORTIE", "RETRAIT")) amount else 0.0
            val newExp = reg.totalExpenses + if (type == "DEPENSE") amount else 0.0
            val newTheo = reg.openingBalance + reg.totalSalesCash + newIn - newOut - newExp
            cashDao.updateCashRegister(
                reg.copy(
                    totalCashIn = newIn,
                    totalCashOut = newOut,
                    totalExpenses = newExp,
                    theoreticalBalance = newTheo
                )
            )
            if (type == "DEPENSE") {
                adminDao.insertExpense(
                    ExpenseEntity(
                        category = "Autres",
                        amount = amount,
                        description = reason,
                        userId = actor.id,
                        userName = actor.fullName,
                        expenseDate = now
                    )
                )
            }
            adminDao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    action = "${actor.fullName} a enregistré un mouvement de caisse ($type) de ${Formatters.fcfa(amount)} : $reason",
                    module = "Caisse",
                    oldValue = Formatters.fcfa(reg.theoreticalBalance),
                    newValue = Formatters.fcfa(newTheo),
                    deviceInfo = Formatters.deviceLabel(),
                    createdAt = now
                )
            )
        }
        return Result.success(Unit)
    }

    suspend fun closeCashRegister(actor: UserEntity, actualCounted: Double, reason: String): Result<CashRegisterEntity> {
        val reg = cashDao.getOpenRegisterOnce()
            ?: return Result.failure(IllegalStateException("Aucune caisse ouverte à fermer"))
        val now = System.currentTimeMillis()
        val discrepancy = actualCounted - reg.theoreticalBalance
        val closed = reg.copy(
            closedByUserId = actor.id,
            closedByName = actor.fullName,
            actualCountedBalance = actualCounted,
            discrepancy = discrepancy,
            discrepancyReason = reason,
            status = "FERMEE",
            closedAt = now
        )
        db.withTransaction {
            cashDao.updateCashRegister(closed)
            if (kotlin.math.abs(discrepancy) > 0.5) {
                val label = if (discrepancy > 0) "Excédent" else "Déficit"
                adminDao.insertNotification(
                    AlertNotificationEntity(
                        type = "ECART_CAISSE",
                        title = "Écart de caisse ($label)",
                        message = "${actor.fullName} a fermé la caisse avec un $label de ${Formatters.fcfa(kotlin.math.abs(discrepancy))}.",
                        severity = "CRITICAL",
                        createdAt = now
                    )
                )
            }
            adminDao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    action = "${actor.fullName} a fermé sa caisse avec un écart de ${Formatters.fcfa(discrepancy)}.",
                    module = "Caisse",
                    oldValue = "Théorique ${Formatters.fcfa(reg.theoreticalBalance)}",
                    newValue = "Compté ${Formatters.fcfa(actualCounted)}",
                    deviceInfo = Formatters.deviceLabel(),
                    createdAt = now
                )
            )
        }
        return Result.success(closed)
    }

    // --- Expenses & Reports ---
    suspend fun addExpense(actor: UserEntity, category: String, amount: Double, description: String, receiptRef: String) {
        val now = System.currentTimeMillis()
        adminDao.insertExpense(
            ExpenseEntity(
                category = category,
                amount = amount,
                description = description,
                receiptRef = receiptRef,
                userId = actor.id,
                userName = actor.fullName,
                expenseDate = now
            )
        )
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = actor.id,
                userName = actor.fullName,
                action = "${actor.fullName} a enregistré une dépense ($category) de ${Formatters.fcfa(amount)}.",
                module = "Finances",
                oldValue = "—",
                newValue = description,
                deviceInfo = Formatters.deviceLabel(),
                createdAt = now
            )
        )
    }

    suspend fun recordGeneratedReport(actor: UserEntity, type: String, title: String, period: String, summary: String) {
        val now = System.currentTimeMillis()
        adminDao.insertReport(
            ReportRecordEntity(
                reportType = type,
                title = title,
                periodLabel = period,
                summaryText = summary,
                generatedByName = actor.fullName,
                createdAt = now
            )
        )
        adminDao.insertNotification(
            AlertNotificationEntity(
                type = "RAPPORT",
                title = "Rapport disponible",
                message = "$title ($period) généré par ${actor.fullName}.",
                severity = "INFO",
                createdAt = now
            )
        )
    }

    // --- Users, Staff HR & Granular Permissions Management ---
    suspend fun createStaffUser(
        actor: UserEntity,
        username: String,
        fullName: String,
        rawPassword: String,
        roleCode: String,
        phone: String = "",
        whatsappNumber: String = "",
        email: String = "",
        positionTitle: String = "",
        salaryFcfa: Double = 0.0
    ): Result<Long> {
        if (userDao.getUserByUsername(username.trim()) != null) {
            return Result.failure(IllegalArgumentException("Cet identifiant existe déjà"))
        }
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword(rawPassword, salt)
        val now = System.currentTimeMillis()
        val uid = userDao.insertUser(
            UserEntity(
                username = username.trim(),
                fullName = fullName.trim(),
                phone = phone.trim(),
                whatsappNumber = whatsappNumber.trim().ifBlank { phone.trim() },
                email = email.trim(),
                positionTitle = positionTitle.trim(),
                salaryFcfa = salaryFcfa.coerceAtLeast(0.0),
                hiredAt = now,
                passwordHash = hash,
                passwordSalt = salt,
                roleCode = roleCode,
                isActive = true,
                createdAt = now
            )
        )
        val defaults = AppPermission.defaultPermissionsForRole(roleCode)
        userDao.insertUserPermissions(
            AppPermission.entries.map { UserPermissionEntity(uid, it.code, it.code in defaults) }
        )
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = actor.id,
                userName = actor.fullName,
                action = "${actor.fullName} a recruté/créé l'employé $fullName ($roleCode · ${phone.ifBlank { "sans tél" }}).",
                module = "Employés & Utilisateurs",
                oldValue = "—",
                newValue = "$username · $email",
                deviceInfo = Formatters.deviceLabel(),
                createdAt = now
            )
        )
        return Result.success(uid)
    }

    suspend fun changeOwnPassword(
        actor: UserEntity,
        currentPassword: String,
        newPassword: String
    ): Result<UserEntity> {
        val fresh = userDao.getUserById(actor.id)
            ?: return Result.failure(IllegalStateException("Compte utilisateur introuvable"))
        if (!fresh.isActive) {
            return Result.failure(IllegalStateException("Compte révoqué"))
        }
        if (currentPassword.isBlank() || !PasswordHasher.verifyPassword(currentPassword, fresh.passwordSalt, fresh.passwordHash)) {
            recordUnauthorizedOrAlert(
                title = "Échec changement mot de passe",
                message = "${fresh.fullName} (@${fresh.username}) a saisi un mot de passe actuel incorrect.",
                severity = "WARNING",
                type = "ACCES_REFUSE",
                userId = fresh.id,
                userName = fresh.fullName
            )
            return Result.failure(IllegalArgumentException("Mot de passe actuel incorrect"))
        }
        if (newPassword.length < 4) {
            return Result.failure(IllegalArgumentException("Le nouveau mot de passe doit contenir au moins 4 caractères"))
        }
        if (newPassword == currentPassword) {
            return Result.failure(IllegalArgumentException("Le nouveau mot de passe doit être différent de l'actuel"))
        }
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword(newPassword, salt)
        val updated = fresh.copy(passwordSalt = salt, passwordHash = hash)
        userDao.updateUser(updated)
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = fresh.id,
                userName = fresh.fullName,
                action = "${fresh.fullName} a modifié son propre mot de passe.",
                module = "Sécurité",
                oldValue = "Mot de passe précédent",
                newValue = "Mot de passe mis à jour",
                deviceInfo = Formatters.deviceLabel(),
                createdAt = System.currentTimeMillis()
            )
        )
        return Result.success(updated)
    }

    suspend fun resetStaffPasswordByAdmin(
        actor: UserEntity,
        target: UserEntity,
        newPassword: String
    ): Result<UserEntity> {
        val actorPerms = getPermissionsForUser(actor.id)
        val isActorAdmin = actor.roleCode == RoleCode.ADMIN.code
        val canManagerReset = (AppPermission.MANAGE_USERS.code in actorPerms) && target.roleCode != RoleCode.ADMIN.code
        if (!isActorAdmin && !canManagerReset) {
            recordUnauthorizedAttempt(actor, "Réinitialiser mot de passe de ${target.username}", "Sécurité")
            return Result.failure(IllegalStateException("Action réservée à l'administrateur"))
        }
        if (newPassword.length < 4) {
            return Result.failure(IllegalArgumentException("Le mot de passe doit contenir au moins 4 caractères"))
        }
        val freshTarget = userDao.getUserById(target.id) ?: target
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword(newPassword, salt)
        val updated = freshTarget.copy(passwordSalt = salt, passwordHash = hash)
        userDao.updateUser(updated)
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = actor.id,
                userName = actor.fullName,
                action = "${actor.fullName} a modifié le mot de passe de ${freshTarget.fullName} (@${freshTarget.username}).",
                module = "Sécurité",
                oldValue = "Ancien mot de passe",
                newValue = "Réinitialisé par ${actor.roleCode}",
                deviceInfo = Formatters.deviceLabel(),
                createdAt = System.currentTimeMillis()
            )
        )
        return Result.success(updated)
    }

    suspend fun updateStaffMember(
        actor: UserEntity,
        target: UserEntity,
        fullName: String,
        phone: String,
        whatsappNumber: String,
        email: String,
        positionTitle: String,
        salaryFcfa: Double,
        roleCode: String,
        newPassword: String?
    ): Result<Unit> {
        if (!newPassword.isNullOrBlank() && newPassword.length < 4) {
            return Result.failure(IllegalArgumentException("Nouveau mot de passe : 4 caractères minimum"))
        }
        val now = System.currentTimeMillis()
        val (salt, hash) = if (!newPassword.isNullOrBlank()) {
            val s = PasswordHasher.generateSalt()
            s to PasswordHasher.hashPassword(newPassword, s)
        } else {
            target.passwordSalt to target.passwordHash
        }
        val updated = target.copy(
            fullName = fullName.trim().ifBlank { target.fullName },
            phone = phone.trim(),
            whatsappNumber = whatsappNumber.trim().ifBlank { phone.trim() },
            email = email.trim(),
            positionTitle = positionTitle.trim(),
            salaryFcfa = salaryFcfa.coerceAtLeast(0.0),
            roleCode = roleCode,
            passwordSalt = salt,
            passwordHash = hash
        )
        userDao.updateUser(updated)
        if (target.roleCode != roleCode) {
            val defaults = AppPermission.defaultPermissionsForRole(roleCode)
            userDao.insertUserPermissions(
                AppPermission.entries.map { UserPermissionEntity(target.id, it.code, it.code in defaults) }
            )
        }
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = actor.id,
                userName = actor.fullName,
                action = "${actor.fullName} a mis à jour la fiche employé de ${updated.fullName} ($roleCode).",
                module = "Employés & Utilisateurs",
                oldValue = "${target.fullName} (${target.roleCode})",
                newValue = "${updated.fullName} (${updated.roleCode}) · ${updated.phone}",
                deviceInfo = Formatters.deviceLabel(),
                createdAt = now
            )
        )
        return Result.success(Unit)
    }

    suspend fun toggleUserActive(actor: UserEntity, target: UserEntity) {
        val updated = target.copy(isActive = !target.isActive)
        userDao.updateUser(updated)
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = actor.id,
                userName = actor.fullName,
                action = "${actor.fullName} a ${if (updated.isActive) "activé" else "révoqué"} le compte ${target.fullName}.",
                module = "Utilisateurs",
                oldValue = if (target.isActive) "Actif" else "Révoqué",
                newValue = if (updated.isActive) "Actif" else "Révoqué",
                deviceInfo = Formatters.deviceLabel()
            )
        )
    }

    suspend fun updateUserRoleAndPermission(
        actor: UserEntity,
        target: UserEntity,
        permissionCode: String,
        granted: Boolean
    ) {
        userDao.insertUserPermissions(listOf(UserPermissionEntity(target.id, permissionCode, granted)))
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = actor.id,
                userName = actor.fullName,
                action = "${actor.fullName} a modifié la permission $permissionCode pour ${target.fullName}.",
                module = "Permissions",
                oldValue = (!granted).toString(),
                newValue = granted.toString(),
                deviceInfo = Formatters.deviceLabel()
            )
        )
    }

    suspend fun updateStoreSettings(
        actor: UserEntity,
        newSettings: StoreSettingsEntity,
        cloudUid: String? = null
    ) {
        adminDao.upsertSettings(newSettings)
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = actor.id,
                userName = actor.fullName,
                action = "${actor.fullName} a mis à jour les paramètres de la structure.",
                module = "Paramètres",
                oldValue = "Configuration précédente",
                newValue = "${newSettings.storeName} (${newSettings.city} · ${newSettings.location}) · TVA ${newSettings.vatRate}%",
                deviceInfo = Formatters.deviceLabel()
            )
        )
        if (!cloudUid.isNullOrBlank()) {
            cloudSync.upsertStoreProfile(cloudUid, newSettings)
        }
    }

    suspend fun markNotificationRead(id: Long) = adminDao.markNotificationRead(id)
    suspend fun markAllNotificationsRead() = adminDao.markAllNotificationsRead()

    // --- Backups, Restore & Cloud Sync ---
    suspend fun buildBackupJson(): Pair<String, Int> {
        val prods = catalogDao.getProductsOnce()
        val cats = catalogDao.getCategoriesOnce()
        val sups = catalogDao.getSuppliersOnce()
        val custs = catalogDao.getCustomersOnce()
        val salesList = salesDao.getSalesOnce()
        val exps = adminDao.getExpensesOnce()
        val settings = adminDao.getSettingsOnce()

        val root = JSONObject().apply {
            put("app", "SMART STORE PRO")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("storeName", settings?.storeName ?: "SMART STORE PRO")
            put("city", settings?.city ?: "")
            put("location", settings?.location ?: "")
            put("categories", JSONArray(cats.map { c ->
                JSONObject().apply {
                    put("name", c.name); put("subCategory", c.subCategory); put("colorHex", c.colorHex)
                }
            }))
            put("products", JSONArray(prods.map { p ->
                JSONObject().apply {
                    put("name", p.name); put("reference", p.reference); put("sku", p.sku)
                    put("barcode", p.barcode); put("categoryName", p.categoryName)
                    put("subCategory", p.subCategory); put("purchasePrice", p.purchasePrice)
                    put("sellingPrice", p.sellingPrice); put("stockQuantity", p.stockQuantity)
                    put("minThreshold", p.minThreshold); put("unit", p.unit); put("status", p.status)
                }
            }))
            put("customers", JSONArray(custs.map { c ->
                JSONObject().apply {
                    put("name", c.name); put("phone", c.phone); put("email", c.email); put("address", c.address)
                }
            }))
            put("suppliers", JSONArray(sups.map { s ->
                JSONObject().apply {
                    put("name", s.name); put("company", s.company); put("phone", s.phone); put("city", s.city)
                }
            }))
            put("salesCount", salesList.size)
            put("expensesCount", exps.size)
        }
        val count = prods.size + cats.size + sups.size + custs.size + salesList.size + exps.size
        return root.toString(2) to count
    }

    suspend fun createBackupSnapshot(actor: UserEntity, label: String, type: String, cloudUid: String?): BackupRecordEntity {
        val (json, count) = buildBackupJson()
        val now = System.currentTimeMillis()
        val record = BackupRecordEntity(
            label = label.ifBlank { "Sauvegarde ${Formatters.dateTime(now)}" },
            type = type,
            recordCount = count,
            sizeBytes = json.toByteArray(Charsets.UTF_8).size.toLong(),
            jsonPayload = json,
            createdByName = actor.fullName,
            createdAt = now
        )
        val id = adminDao.insertBackup(record)
        val saved = record.copy(id = id)
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = actor.id,
                userName = actor.fullName,
                action = "${actor.fullName} a créé une sauvegarde ($type) : ${saved.label}.",
                module = "Sauvegardes",
                oldValue = "—",
                newValue = "$count enregistrements",
                deviceInfo = Formatters.deviceLabel(),
                createdAt = now
            )
        )
        if (!cloudUid.isNullOrBlank()) {
            cloudSync.pushBackup(cloudUid, saved, "$count enregistrements")
        }
        return saved
    }

    suspend fun restoreFromBackupJson(actor: UserEntity, jsonText: String): Result<Int> {
        return try {
            val root = JSONObject(jsonText)
            val prods = root.optJSONArray("products") ?: JSONArray()
            var imported = 0
            db.withTransaction {
                for (i in 0 until prods.length()) {
                    val obj = prods.getJSONObject(i)
                    val sku = obj.optString("sku")
                    val existing = if (sku.isNotBlank()) catalogDao.findByBarcodeOrSku(sku) else null
                    val entity = ProductEntity(
                        id = existing?.id ?: 0L,
                        name = obj.getString("name"),
                        reference = obj.optString("reference", "REF-$i"),
                        sku = sku.ifBlank { "SKU-$i" },
                        barcode = obj.optString("barcode", ""),
                        categoryId = existing?.categoryId ?: 1L,
                        categoryName = obj.optString("categoryName", "Autres produits"),
                        subCategory = obj.optString("subCategory", ""),
                        purchasePrice = obj.optDouble("purchasePrice", 0.0),
                        sellingPrice = obj.optDouble("sellingPrice", 0.0),
                        stockQuantity = obj.optInt("stockQuantity", 0),
                        minThreshold = obj.optInt("minThreshold", 10),
                        unit = obj.optString("unit", "Unité"),
                        status = obj.optString("status", "ACTIF")
                    )
                    catalogDao.insertProduct(entity)
                    imported++
                }
                adminDao.insertAuditLog(
                    AuditLogEntity(
                        userId = actor.id,
                        userName = actor.fullName,
                        action = "${actor.fullName} a restauré/importé $imported produits depuis une sauvegarde JSON.",
                        module = "Sauvegardes",
                        oldValue = "—",
                        newValue = "$imported produits",
                        deviceInfo = Formatters.deviceLabel()
                    )
                )
            }
            Result.success(imported)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importProductsFromCsv(actor: UserEntity, csvText: String): Result<Int> {
        return try {
            val lines = csvText.lines().filter { it.isNotBlank() }
            if (lines.size < 2) return Result.failure(IllegalArgumentException("CSV vide ou sans données"))
            val sep = if (lines.first().contains(';')) ';' else ','
            var count = 0
            db.withTransaction {
                for (line in lines.drop(1)) {
                    val cols = line.split(sep).map { it.trim().trim('"') }
                    if (cols.size < 3 || cols[0].isBlank()) continue
                    val name = cols[0]
                    val sku = cols.getOrElse(1) { "IMP-${System.currentTimeMillis() % 10000}" }
                    val barcode = cols.getOrElse(2) { "" }
                    val cat = cols.getOrElse(3) { "Autres produits" }.ifBlank { "Autres produits" }
                    val buy = cols.getOrElse(4) { "0" }.toDoubleOrNull() ?: 0.0
                    val sell = cols.getOrElse(5) { "0" }.toDoubleOrNull() ?: 0.0
                    val qty = cols.getOrElse(6) { "0" }.toIntOrNull() ?: 0
                    val min = cols.getOrElse(7) { "10" }.toIntOrNull() ?: 10
                    val existing = catalogDao.findByBarcodeOrSku(sku)
                    catalogDao.insertProduct(
                        ProductEntity(
                            id = existing?.id ?: 0L,
                            name = name,
                            reference = existing?.reference ?: "IMP-$sku",
                            sku = sku,
                            barcode = barcode,
                            categoryId = existing?.categoryId ?: 1L,
                            categoryName = cat,
                            purchasePrice = buy,
                            sellingPrice = sell,
                            stockQuantity = qty,
                            minThreshold = min,
                            unit = existing?.unit ?: "Unité",
                            status = "ACTIF"
                        )
                    )
                    count++
                }
                adminDao.insertAuditLog(
                    AuditLogEntity(
                        userId = actor.id,
                        userName = actor.fullName,
                        action = "${actor.fullName} a importé $count produits via fichier CSV.",
                        module = "Documents & Import",
                        oldValue = "—",
                        newValue = "$count produits",
                        deviceInfo = Formatters.deviceLabel()
                    )
                )
            }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importAnyDocumentFile(
        context: Context,
        actor: UserEntity,
        uri: Uri,
        category: String,
        notes: String
    ): Result<Pair<DocumentFileEntity, String>> {
        return try {
            val info = DocumentExporter.copyUriToInternalDocuments(context, uri)
            var extraMsg = "Fichier archivé (${info.extension.uppercase()})"
            if (info.textPreviewIfText != null) {
                when (info.extension) {
                    "csv", "tsv" -> {
                        val imported = importProductsFromCsv(actor, info.textPreviewIfText).getOrNull()
                        if (imported != null && imported > 0) {
                            extraMsg = "$imported produits importés automatiquement depuis le CSV"
                        }
                    }
                    "json" -> {
                        val restored = restoreFromBackupJson(actor, info.textPreviewIfText).getOrNull()
                        if (restored != null && restored > 0) {
                            extraMsg = "$restored enregistrements restaurés depuis le JSON"
                        }
                    }
                }
            }
            val doc = DocumentFileEntity(
                fileName = info.fileName,
                mimeType = info.mimeType,
                extension = info.extension,
                category = category,
                localPath = info.localPath,
                sizeBytes = info.sizeBytes,
                direction = "IMPORT",
                notes = notes.ifBlank { extraMsg },
                uploadedByName = actor.fullName
            )
            val id = adminDao.insertDocument(doc)
            adminDao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    action = "${actor.fullName} a importé le document ${info.fileName} ($category).",
                    module = "Documents",
                    oldValue = "—",
                    newValue = "${info.sizeBytes} octets",
                    deviceInfo = Formatters.deviceLabel()
                )
            )
            Result.success(doc.copy(id = id) to extraMsg)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerExportedDocument(
        context: Context,
        actor: UserEntity,
        fileName: String,
        mimeType: String,
        extension: String,
        category: String,
        bytes: ByteArray,
        notes: String
    ): DocumentFileEntity {
        val file = DocumentExporter.saveInternalDocument(context, fileName, bytes)
        val doc = DocumentFileEntity(
            fileName = fileName,
            mimeType = mimeType,
            extension = extension,
            category = category,
            localPath = file.absolutePath,
            sizeBytes = file.length(),
            direction = "EXPORT",
            notes = notes,
            uploadedByName = actor.fullName
        )
        val id = adminDao.insertDocument(doc)
        return doc.copy(id = id)
    }

    suspend fun deleteDocumentRecord(actor: UserEntity, doc: DocumentFileEntity) {
        try { File(doc.localPath).delete() } catch (_: Exception) {}
        adminDao.deleteDocument(doc.id)
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = actor.id,
                userName = actor.fullName,
                action = "${actor.fullName} a supprimé le document ${doc.fileName}.",
                module = "Documents",
                oldValue = doc.fileName,
                newValue = "Supprimé",
                deviceInfo = Formatters.deviceLabel()
            )
        )
    }

    // --- Cloud Synchronization (Offline-First Queue + Conflict Resolution) ---
    suspend fun syncPendingToCloud(cloudUid: String): Result<Int> {
        var syncedCount = 0
        return try {
            val pendingProds = catalogDao.getPendingSyncProducts()
            for (p in pendingProds) {
                if (cloudSync.upsertProduct(cloudUid, p).isSuccess) {
                    catalogDao.markProductSynced(p.id)
                    syncedCount++
                }
            }
            val pendingSales = salesDao.getPendingSyncSales()
            for (s in pendingSales) {
                val items = salesDao.getSaleItems(s.id)
                if (cloudSync.upsertSale(cloudUid, s, items.size.coerceAtLeast(1), items).isSuccess) {
                    salesDao.markSaleSynced(s.id)
                    syncedCount++
                }
            }
            val pendingLogs = adminDao.getPendingSyncAuditLogs()
            for (l in pendingLogs) {
                if (cloudSync.pushAuditLog(cloudUid, l).isSuccess) {
                    adminDao.markAuditLogSynced(l.id)
                    syncedCount++
                }
            }
            val settings = adminDao.getSettingsOnce()
            if (settings != null) {
                cloudSync.upsertStoreProfile(cloudUid, settings)
                adminDao.upsertSettings(settings.copy(lastSyncAt = System.currentTimeMillis()))
                syncedCount++
            }
            Result.success(syncedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Multi-company Admin Online Payment Gateway Configuration ---
    suspend fun saveOnlinePaymentConfig(
        actor: UserEntity,
        config: OnlinePaymentConfigEntity
    ): Result<OnlinePaymentConfigEntity> {
        val freshActor = userDao.getUserById(actor.id)
            ?: return Result.failure(IllegalStateException("Compte introuvable."))
        val granted = getPermissionsForUser(freshActor.id)
        if (!freshActor.isActive || (freshActor.roleCode != RoleCode.ADMIN.code && AppPermission.MANAGE_SETTINGS.code !in granted)) {
            recordUnauthorizedOrAlert(
                title = "Accès passerelles refusé",
                message = "${actor.fullName} a tenté de modifier les passerelles de paiement en ligne.",
                severity = "CRITICAL",
                type = "ACCES_REFUSE",
                userId = actor.id,
                userName = actor.fullName
            )
            return Result.failure(IllegalStateException("Permission d'administration requise pour configurer les paiements en ligne."))
        }

        val cleanName = config.displayName.trim()
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Le nom du mode de paiement est obligatoire."))
        }

        val cat = com.example.data.remote.OnlinePaymentGatewayType.fromCode(config.category).code
        when (cat) {
            com.example.data.remote.OnlinePaymentGatewayType.CINETPAY.code -> {
                if (config.apiKey.isBlank() || config.siteOrMerchantId.isBlank()) {
                    return Result.failure(IllegalArgumentException("CinetPay requiert votre API Key et votre Site ID."))
                }
            }
            com.example.data.remote.OnlinePaymentGatewayType.NOTCHPAY.code -> {
                if (config.apiKey.isBlank()) {
                    return Result.failure(IllegalArgumentException("NotchPay requiert votre clé publique."))
                }
            }
            com.example.data.remote.OnlinePaymentGatewayType.ORANGE_MONEY.code,
            com.example.data.remote.OnlinePaymentGatewayType.MTN_MOMO.code -> {
                if (config.receiverPhone.isBlank() && config.siteOrMerchantId.isBlank()) {
                    return Result.failure(IllegalArgumentException("Saisissez le numéro téléphone ou le code marchand du compte."))
                }
            }
            com.example.data.remote.OnlinePaymentGatewayType.BANK_CARD.code -> {
                if (config.siteOrMerchantId.isBlank() && config.paymentBaseUrl.isBlank()) {
                    return Result.failure(IllegalArgumentException("Saisissez un IBAN / RIB / TPE ou une URL de paiement."))
                }
            }
            com.example.data.remote.OnlinePaymentGatewayType.CUSTOM_LINK.code,
            com.example.data.remote.OnlinePaymentGatewayType.CUSTOM_API.code -> {
                val url = config.paymentBaseUrl.trim()
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    return Result.failure(IllegalArgumentException("Saisissez une URL valide commençant par https:// ou http://."))
                }
            }
        }

        val now = System.currentTimeMillis()
        val sanitized = config.copy(
            category = cat,
            displayName = cleanName,
            apiKey = config.apiKey.trim(),
            secretOrToken = config.secretOrToken.trim(),
            siteOrMerchantId = config.siteOrMerchantId.trim(),
            receiverPhone = config.receiverPhone.trim(),
            accountHolderName = config.accountHolderName.trim(),
            ussdPattern = config.ussdPattern.trim(),
            paymentBaseUrl = config.paymentBaseUrl.trim(),
            callbackUrl = config.callbackUrl.trim(),
            verifyEndpointUrl = config.verifyEndpointUrl.trim(),
            instructions = config.instructions.trim(),
            updatedAt = now
        )
        val savedId = if (sanitized.id == 0L) {
            onlinePaymentDao.insertOnlinePaymentConfig(sanitized)
        } else {
            onlinePaymentDao.updateOnlinePaymentConfig(sanitized)
            sanitized.id
        }
        val finalEntity = sanitized.copy(id = savedId)
        val keyHint = if (finalEntity.apiKey.isNotBlank()) {
            "Clé API configurée (***${finalEntity.apiKey.takeLast(4)})"
        } else if (finalEntity.receiverPhone.isNotBlank()) {
            "Tél : ${finalEntity.receiverPhone}"
        } else if (finalEntity.siteOrMerchantId.isNotBlank()) {
            "ID/Compte : ${finalEntity.siteOrMerchantId}"
        } else {
            "URL configurée"
        }
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = freshActor.id,
                userName = freshActor.fullName,
                action = "${freshActor.fullName} a configuré le mode de paiement « $cleanName » ($cat).",
                module = "Paiements en ligne · Config",
                oldValue = if (config.id == 0L) "Nouveau mode" else "Mise à jour",
                newValue = "$keyHint · ${if (finalEntity.isEnabled) "Actif" else "Désactivé"}",
                deviceInfo = Formatters.deviceLabel(),
                createdAt = now
            )
        )
        return Result.success(finalEntity)
    }

    suspend fun toggleOnlinePaymentConfig(
        actor: UserEntity,
        config: OnlinePaymentConfigEntity,
        enabled: Boolean
    ): Result<Unit> {
        val freshActor = userDao.getUserById(actor.id)
            ?: return Result.failure(IllegalStateException("Compte introuvable."))
        val granted = getPermissionsForUser(freshActor.id)
        if (!freshActor.isActive || (freshActor.roleCode != RoleCode.ADMIN.code && AppPermission.MANAGE_SETTINGS.code !in granted)) {
            return Result.failure(IllegalStateException("Permission refusée."))
        }
        val now = System.currentTimeMillis()
        onlinePaymentDao.updateOnlinePaymentConfig(config.copy(isEnabled = enabled, updatedAt = now))
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = freshActor.id,
                userName = freshActor.fullName,
                action = "${freshActor.fullName} a ${if (enabled) "activé" else "désactivé"} « ${config.displayName} ».",
                module = "Paiements en ligne · Config",
                oldValue = if (config.isEnabled) "Actif" else "Désactivé",
                newValue = if (enabled) "Actif" else "Désactivé",
                deviceInfo = Formatters.deviceLabel(),
                createdAt = now
            )
        )
        return Result.success(Unit)
    }

    suspend fun deleteOnlinePaymentConfig(
        actor: UserEntity,
        config: OnlinePaymentConfigEntity
    ): Result<Unit> {
        val freshActor = userDao.getUserById(actor.id)
            ?: return Result.failure(IllegalStateException("Compte introuvable."))
        val granted = getPermissionsForUser(freshActor.id)
        if (!freshActor.isActive || (freshActor.roleCode != RoleCode.ADMIN.code && AppPermission.MANAGE_SETTINGS.code !in granted)) {
            return Result.failure(IllegalStateException("Permission refusée."))
        }
        val now = System.currentTimeMillis()
        onlinePaymentDao.deleteOnlinePaymentConfig(config.id)
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = freshActor.id,
                userName = freshActor.fullName,
                action = "${freshActor.fullName} a supprimé le mode de paiement « ${config.displayName} ».",
                module = "Paiements en ligne · Config",
                oldValue = config.category,
                newValue = "Supprimé",
                deviceInfo = Formatters.deviceLabel(),
                createdAt = now
            )
        )
        return Result.success(Unit)
    }

    // --- Real Online Payments (Multi-tenant Admin Config + CinetPay / NotchPay / Mobile Money / Bank / Custom) ---
    private suspend fun resolveGatewayConfig(configId: Long, providerKey: String): OnlinePaymentConfigEntity? {
        if (configId > 0L) {
            return onlinePaymentDao.getOnlinePaymentConfigById(configId)
        }
        val all = onlinePaymentDao.getOnlinePaymentConfigsOnce()
        val norm = providerKey.trim()
        return all.firstOrNull { it.isEnabled && it.displayName.equals(norm, ignoreCase = true) }
            ?: all.firstOrNull { it.isEnabled && it.category.equals(norm.replace(" ", "_"), ignoreCase = true) }
            ?: all.firstOrNull { it.displayName.equals(norm, ignoreCase = true) || it.category.equals(norm.replace(" ", "_"), ignoreCase = true) }
    }

    suspend fun initiateOnlinePayment(
        actor: UserEntity,
        provider: String,
        channel: String,
        amountXaf: Double,
        customerName: String,
        customerPhone: String,
        customerEmail: String,
        invoiceNumber: String = "",
        description: String = "",
        configId: Long = 0L
    ): OnlinePaymentEntity {
        val now = System.currentTimeMillis()
        val txId = "SSP-${(now % 1_000_000L).toString().padStart(6, '0')}-${(100..999).random()}"
        val matchedConfig = resolveGatewayConfig(configId, provider)
        val effectiveProvider = matchedConfig?.displayName?.ifBlank { provider } ?: provider
        val desc = description.ifBlank {
            if (invoiceNumber.isNotBlank()) "Règlement facture $invoiceNumber"
            else "Paiement · $effectiveProvider ($channel)"
        }
        val cleanName = customerName.trim().ifBlank { "Client Comptant" }
        val cleanEmail = customerEmail.trim().ifBlank { "client@entreprise.local" }

        val gatewayResp = OnlinePaymentService.initiateWithConfig(
            config = matchedConfig,
            fallbackProvider = effectiveProvider,
            transactionId = txId,
            amountXaf = amountXaf,
            description = desc,
            customerName = cleanName,
            customerPhone = customerPhone,
            customerEmail = cleanEmail,
            channel = channel
        )

        val entity = OnlinePaymentEntity(
            transactionId = gatewayResp.transactionId,
            invoiceNumber = invoiceNumber.trim(),
            provider = gatewayResp.gateway.ifBlank { effectiveProvider },
            configId = matchedConfig?.id ?: 0L,
            customerName = cleanName,
            customerPhone = customerPhone.trim(),
            customerEmail = cleanEmail,
            amountXaf = amountXaf,
            status = gatewayResp.status,
            paymentUrl = gatewayResp.paymentUrl,
            gatewayMessage = gatewayResp.message,
            isLiveGateway = gatewayResp.isLiveGatewayResponse,
            createdByName = actor.fullName,
            createdAt = now,
            updatedAt = now
        )
        val savedId = onlinePaymentDao.insertOnlinePayment(entity)
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = actor.id,
                userName = actor.fullName,
                action = "${actor.fullName} a initié un paiement en ligne (${entity.provider} / $channel) de ${Formatters.fcfa(amountXaf)} [${gatewayResp.transactionId}].",
                module = "Paiements en ligne",
                oldValue = "—",
                newValue = gatewayResp.status,
                deviceInfo = Formatters.deviceLabel(),
                createdAt = now
            )
        )
        return entity.copy(id = savedId)
    }

    suspend fun verifyOnlinePayment(actor: UserEntity, payment: OnlinePaymentEntity): OnlinePaymentEntity {
        if (payment.status == "CONFIRME") return payment
        val now = System.currentTimeMillis()
        val matchedConfig = resolveGatewayConfig(payment.configId, payment.provider)
        val (newStatus, newMessage) = OnlinePaymentService.verifyWithConfig(
            transactionId = payment.transactionId,
            providerName = payment.provider,
            config = matchedConfig
        )
        val updated = payment.copy(
            status = newStatus,
            gatewayMessage = newMessage,
            updatedAt = now
        )
        onlinePaymentDao.updateOnlinePayment(updated)
        if (newStatus == "CONFIRME" && payment.status != "CONFIRME") {
            finalizeOnlinePaymentEffects(actor, updated, now, "API ${payment.provider}")
        }
        return updated
    }

    suspend fun confirmOnlinePaymentManually(
        actor: UserEntity,
        payment: OnlinePaymentEntity,
        operatorRef: String
    ): OnlinePaymentEntity {
        if (payment.status == "CONFIRME") return payment
        val now = System.currentTimeMillis()
        val cleanRef = operatorRef.trim().ifBlank { "COMPTOIR-${payment.provider.take(3).uppercase()}" }
        val updated = payment.copy(
            status = "CONFIRME",
            gatewayMessage = "Confirmé au comptoir · Réf $cleanRef",
            updatedAt = now
        )
        onlinePaymentDao.updateOnlinePayment(updated)
        finalizeOnlinePaymentEffects(actor, updated, now, cleanRef)
        return updated
    }

    suspend fun cancelOnlinePayment(actor: UserEntity, payment: OnlinePaymentEntity): OnlinePaymentEntity {
        val now = System.currentTimeMillis()
        val updated = payment.copy(
            status = "ECHOUE",
            gatewayMessage = "Annulé par ${actor.fullName}",
            updatedAt = now
        )
        onlinePaymentDao.updateOnlinePayment(updated)
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = actor.id,
                userName = actor.fullName,
                action = "${actor.fullName} a annulé le paiement en ligne ${payment.transactionId}.",
                module = "Paiements en ligne",
                oldValue = payment.status,
                newValue = "ECHOUE",
                deviceInfo = Formatters.deviceLabel(),
                createdAt = now
            )
        )
        return updated
    }

    private suspend fun finalizeOnlinePaymentEffects(
        actor: UserEntity,
        payment: OnlinePaymentEntity,
        now: Long,
        refNote: String
    ) {
        if (payment.invoiceNumber.isNotBlank()) {
            val targetInv = adminDao.getInvoiceByNumber(payment.invoiceNumber)
            if (targetInv != null && targetInv.balanceDue > 0.5) {
                settleInvoicePayment(actor, targetInv, payment.amountXaf)
            }
        }
        val openReg = cashDao.getOpenRegisterOnce()
        if (openReg != null) {
            cashDao.updateCashRegister(
                openReg.copy(totalSalesMobile = openReg.totalSalesMobile + payment.amountXaf)
            )
            cashDao.insertCashMovement(
                CashMovementEntity(
                    cashRegisterId = openReg.id,
                    type = "VENTE",
                    amount = payment.amountXaf,
                    reason = "Paiement en ligne ${payment.provider} (${payment.transactionId})",
                    userId = actor.id,
                    userName = actor.fullName,
                    createdAt = now
                )
            )
        }
        adminDao.insertNotification(
            AlertNotificationEntity(
                type = "PAIEMENT_EN_LIGNE",
                title = "Paiement en ligne confirmé",
                message = "${Formatters.fcfa(payment.amountXaf)} reçus via ${payment.provider} (${payment.transactionId}).",
                severity = "INFO",
                createdAt = now
            )
        )
        adminDao.insertAuditLog(
            AuditLogEntity(
                userId = actor.id,
                userName = actor.fullName,
                action = "Paiement en ligne confirmé (${payment.transactionId}) : ${Formatters.fcfa(payment.amountXaf)} [${payment.provider}].",
                module = "Paiements en ligne",
                oldValue = payment.status,
                newValue = "CONFIRME ($refNote)",
                deviceInfo = Formatters.deviceLabel(),
                createdAt = now
            )
        )
    }
}
