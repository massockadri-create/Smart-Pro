package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.remote.CloudAuditLog
import com.example.data.remote.CloudBackup
import com.example.data.remote.CloudProduct
import com.example.data.remote.CloudSale
import com.example.data.remote.CloudSyncRepository
import com.example.data.remote.OnlinePaymentService
import com.example.data.repository.AdminResetScope
import com.example.data.repository.CartLine
import com.example.data.repository.PurchaseDraftLine
import com.example.data.repository.StoreRepository
import com.example.security.AppPermission
import com.example.util.DesktopSnapshotState
import com.example.util.DesktopWebServer
import com.example.util.DocumentExporter
import com.example.util.Formatters
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class StoreRoute(val title: String, val group: String) {
    DASHBOARD("Tableau de bord", "Général"),
    POS("Point de vente", "Ventes"),
    ONLINE_PAYMENTS("Paiements en ligne", "Ventes"),
    SALES_HISTORY("Historique des ventes", "Ventes"),
    INVOICES("Facturation & Tickets", "Ventes"),
    PRODUCTS("Produits & Prix", "Produits"),
    STOCKS("Gestion des stocks", "Stocks"),
    INVENTORIES("Inventaires", "Inventaires"),
    PURCHASES("Achats & Réceptions", "Achats"),
    SUPPLIERS("Fournisseurs", "Achats"),
    CUSTOMERS("Clients", "Clients"),
    CASH_REGISTER("Caisse", "Caisse"),
    FINANCES("Finances & Dépenses", "Finances"),
    REPORTS("Rapports & Exports", "Rapports"),
    DOCUMENTS("Documents & Import/Export", "Documents"),
    CALCULATOR("Calculatrice scientifique", "Outils"),
    DESKTOP_SYNC("Version Ordinateur", "Outils"),
    USERS("Utilisateurs & Permissions", "Administration"),
    SETTINGS("Paramètres & Sauvegardes", "Administration"),
    AUDIT("Journal d'audit & Alertes", "Administration")
}

data class DashboardMetrics(
    val todaySales: Double = 0.0,
    val weekSales: Double = 0.0,
    val monthSales: Double = 0.0,
    val totalProfit: Double = 0.0,
    val marginPercent: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val salesCount: Int = 0,
    val productsCount: Int = 0,
    val lowStockCount: Int = 0,
    val outOfStockCount: Int = 0,
    val customersCount: Int = 0,
    val suppliersCount: Int = 0,
    val totalStockValue: Double = 0.0,
    val pendingSyncCount: Int = 0,
    val dailySeries: List<Pair<String, Double>> = emptyList(),
    val topProducts: List<Pair<String, Int>> = emptyList()
)

data class SmartStoreUiState(
    val currentUser: UserEntity? = null,
    val currentUserPermissions: Set<String> = emptySet(),
    val currentRoute: StoreRoute = StoreRoute.DASHBOARD,
    val loginError: String? = null,
    val snackbarMessage: String? = null,
    val cart: List<CartLine> = emptyList(),
    val selectedCustomer: CustomerEntity? = null,
    val cartDiscountFcfa: Double = 0.0,
    val lastValidatedSale: SaleEntity? = null,
    val lastValidatedInvoice: InvoiceEntity? = null,
    val activeOnlinePayment: OnlinePaymentEntity? = null,
    val isProcessingOnlinePayment: Boolean = false,
    val isSyncing: Boolean = false,
    val isDesktopServerRunning: Boolean = false,
    val desktopServerUrl: String = "",
    val desktopServerPort: Int = 8080,
    val desktopLocalIps: List<String> = emptyList(),
    val isDatabaseReady: Boolean = false,
    val lastActivityMs: Long = System.currentTimeMillis()
)

class SmartStoreViewModel(
    private val repository: StoreRepository,
    private val cloudSync: CloudSyncRepository,
    private val currentCloudUid: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(SmartStoreUiState())
    val uiState: StateFlow<SmartStoreUiState> = _uiState.asStateFlow()

    val settings: StateFlow<StoreSettingsEntity> = repository.settings
        .map { it ?: StoreSettingsEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StoreSettingsEntity())

    val users: StateFlow<List<UserEntity>> = repository.users
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userPermissions: StateFlow<List<UserPermissionEntity>> = repository.userPermissions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = repository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<ProductEntity>> = repository.products
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val suppliers: StateFlow<List<SupplierEntity>> = repository.suppliers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<CustomerEntity>> = repository.customers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sales: StateFlow<List<SaleEntity>> = repository.sales
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val saleItems: StateFlow<List<SaleItemEntity>> = repository.saleItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val payments: StateFlow<List<PaymentEntity>> = repository.payments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cashRegisters: StateFlow<List<CashRegisterEntity>> = repository.cashRegisters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val openRegister: StateFlow<CashRegisterEntity?> = repository.openRegister
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val cashMovements: StateFlow<List<CashMovementEntity>> = repository.cashMovements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val purchases: StateFlow<List<PurchaseEntity>> = repository.purchases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val purchaseItems: StateFlow<List<PurchaseItemEntity>> = repository.purchaseItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stockMovements: StateFlow<List<StockMovementEntity>> = repository.stockMovements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inventories: StateFlow<List<InventoryEntity>> = repository.inventories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inventoryItems: StateFlow<List<InventoryItemEntity>> = repository.inventoryItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses: StateFlow<List<ExpenseEntity>> = repository.expenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reports: StateFlow<List<ReportRecordEntity>> = repository.reports
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLogEntity>> = repository.auditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<AlertNotificationEntity>> = repository.notifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backups: StateFlow<List<BackupRecordEntity>> = repository.backups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val invoices: StateFlow<List<InvoiceEntity>> = repository.invoices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val documents: StateFlow<List<DocumentFileEntity>> = repository.documents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val onlinePayments: StateFlow<List<OnlinePaymentEntity>> = repository.onlinePayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val onlinePaymentConfigs: StateFlow<List<OnlinePaymentConfigEntity>> = repository.onlinePaymentConfigs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Cloud synced streams (auth-gated inside ViewModel instantiated only when currentCloudUid is authenticated)
    val cloudProducts: StateFlow<List<CloudProduct>> = cloudSync.observeCloudProducts(currentCloudUid)
        .catch { err -> unwrapAndLogCloudError("cloudProducts", err); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cloudSales: StateFlow<List<CloudSale>> = cloudSync.observeCloudSales(currentCloudUid)
        .catch { err -> unwrapAndLogCloudError("cloudSales", err); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cloudAuditLogs: StateFlow<List<CloudAuditLog>> = cloudSync.observeCloudAuditLogs(currentCloudUid)
        .catch { err -> unwrapAndLogCloudError("cloudAuditLogs", err); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cloudBackups: StateFlow<List<CloudBackup>> = cloudSync.observeCloudBackups(currentCloudUid)
        .catch { err -> unwrapAndLogCloudError("cloudBackups", err); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Real-time 1-second system clock stream
    val currentTimeMs: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis())

    private val realtimeMinuteTicker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis() / 60_000L)
            delay(15_000L)
        }
    }

    val dashboardMetrics: StateFlow<DashboardMetrics> = combine(
        sales, saleItems, products, expenses, customers, suppliers, realtimeMinuteTicker
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        val salesList = arr[0] as List<SaleEntity>
        @Suppress("UNCHECKED_CAST")
        val itemsList = arr[1] as List<SaleItemEntity>
        @Suppress("UNCHECKED_CAST")
        val prodsList = arr[2] as List<ProductEntity>
        @Suppress("UNCHECKED_CAST")
        val expList = arr[3] as List<ExpenseEntity>
        @Suppress("UNCHECKED_CAST")
        val custList = arr[4] as List<CustomerEntity>
        @Suppress("UNCHECKED_CAST")
        val supList = arr[5] as List<SupplierEntity>

        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        val validSales = salesList.filter { it.status == "VALIDEE" }
        val today = validSales.filter { now - it.createdAt <= dayMs }.sumOf { it.totalAmount }
        val week = validSales.filter { now - it.createdAt <= 7 * dayMs }.sumOf { it.totalAmount }
        val month = validSales.filter { now - it.createdAt <= 30 * dayMs }.sumOf { it.totalAmount }
        val totalRev = validSales.sumOf { it.totalAmount }
        val totalProfit = validSales.sumOf { it.profit }
        val marginPct = if (totalRev > 0) (totalProfit / totalRev) * 100.0 else 0.0
        val totalExp = expList.sumOf { it.amount }
        val lowStock = prodsList.count { it.stockQuantity in 1..it.minThreshold }
        val outStock = prodsList.count { it.stockQuantity == 0 }
        val stockVal = prodsList.sumOf { it.stockQuantity * it.purchasePrice }
        val pendingSync = prodsList.count { it.syncStatus == "PENDING_SYNC" } + salesList.count { it.syncStatus == "PENDING_SYNC" }

        val dailySeries = (6 downTo 0).map { d ->
            val start = now - (d + 1) * dayMs
            val end = now - d * dayMs
            val amt = validSales.filter { it.createdAt in start..end }.sumOf { it.totalAmount }
            Formatters.shortDay(end) to amt
        }

        val validSaleIds = validSales.map { it.id }.toSet()
        val topProducts = itemsList
            .filter { it.saleId in validSaleIds }
            .groupBy { it.productName }
            .mapValues { (_, lines) -> lines.sumOf { it.quantity } }
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to it.value }

        DashboardMetrics(
            todaySales = today,
            weekSales = week,
            monthSales = month,
            totalProfit = totalProfit,
            marginPercent = marginPct,
            totalExpenses = totalExp,
            salesCount = validSales.size,
            productsCount = prodsList.size,
            lowStockCount = lowStock,
            outOfStockCount = outStock,
            customersCount = custList.size,
            suppliersCount = supList.size,
            totalStockValue = stockVal,
            pendingSyncCount = pendingSync,
            dailySeries = dailySeries,
            topProducts = topProducts
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardMetrics())

    private val desktopWebServer = DesktopWebServer()

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            _uiState.update { it.copy(isDatabaseReady = true) }
        }
        // Synchro entrante : applique sur le téléphone ce qui vient de l'ordinateur
        viewModelScope.launch {
            combine(cloudProducts, cloudSales) { p, s -> p to s }.collect { (p, s) ->
                if (currentCloudUid.isNotBlank()) {
                    try {
                        repository.applyCloudChanges(currentCloudUid, p, s)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.e("CloudPull", "Synchro entrante: ${e.message}")
                    }
                }
            }
        }
        startDesktopServer(silent = true)
        // Push real-time snapshots to the embedded LAN Desktop Web Portal
        viewModelScope.launch {
            combine(
                settings,
                products,
                sales,
                saleItems,
                invoices,
                users,
                onlinePayments,
                notifications,
                dashboardMetrics
            ) { arr ->
                @Suppress("UNCHECKED_CAST")
                val st = arr[0] as StoreSettingsEntity
                @Suppress("UNCHECKED_CAST")
                val prods = arr[1] as List<ProductEntity>
                @Suppress("UNCHECKED_CAST")
                val sls = arr[2] as List<SaleEntity>
                @Suppress("UNCHECKED_CAST")
                val sItems = arr[3] as List<SaleItemEntity>
                @Suppress("UNCHECKED_CAST")
                val invs = arr[4] as List<InvoiceEntity>
                @Suppress("UNCHECKED_CAST")
                val emps = arr[5] as List<UserEntity>
                @Suppress("UNCHECKED_CAST")
                val ops = arr[6] as List<OnlinePaymentEntity>
                @Suppress("UNCHECKED_CAST")
                val notifs = arr[7] as List<AlertNotificationEntity>
                val m = arr[8] as DashboardMetrics
                DesktopSnapshotState(
                    settings = st,
                    products = prods,
                    sales = sls,
                    saleItems = sItems,
                    invoices = invs,
                    employees = emps,
                    onlinePayments = ops,
                    notifications = notifs,
                    todaySalesFcfa = m.todaySales,
                    monthSalesFcfa = m.monthSales,
                    totalProfitFcfa = m.totalProfit
                )
            }.collect { snap ->
                desktopWebServer.updateSnapshot(snap)
            }
        }
        // Observe active user status for immediate revocation enforcement
        viewModelScope.launch {
            users.collect { list ->
                val active = _uiState.value.currentUser ?: return@collect
                val fresh = list.find { it.id == active.id }
                if (fresh == null || !fresh.isActive) {
                    _uiState.update {
                        it.copy(
                            currentUser = null,
                            currentUserPermissions = emptySet(),
                            loginError = "Session révoquée par l'administrateur."
                        )
                    }
                } else {
                    val perms = repository.getPermissionsForUser(fresh.id)
                    _uiState.update { it.copy(currentUser = fresh, currentUserPermissions = perms) }
                }
            }
        }
        // Real-time inactivity auto-logout watcher
        viewModelScope.launch {
            while (true) {
                delay(15_000L)
                val state = _uiState.value
                val activeUser = state.currentUser
                val timeoutMins = settings.value.autoLogoutMinutes
                if (activeUser != null && timeoutMins > 0) {
                    val elapsedMs = System.currentTimeMillis() - state.lastActivityMs
                    if (elapsedMs > timeoutMins * 60_000L) {
                        _uiState.update {
                            it.copy(
                                currentUser = null,
                                currentUserPermissions = emptySet(),
                                cart = emptyList(),
                                cartDiscountFcfa = 0.0,
                                loginError = "Session verrouillée automatiquement après $timeoutMins min d'inactivité."
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        desktopWebServer.stop()
        super.onCleared()
    }

    fun startDesktopServer(silent: Boolean = false) {
        val res = desktopWebServer.start()
        res.onSuccess { port ->
            val url = desktopWebServer.getLanUrl()
            val ip = desktopWebServer.detectLocalIpv4()
            _uiState.update {
                it.copy(
                    isDesktopServerRunning = true,
                    desktopServerUrl = url,
                    desktopServerPort = port,
                    desktopLocalIps = listOfNotNull(ip),
                    snackbarMessage = if (silent) it.snackbarMessage else "Portail PC démarré sur $url"
                )
            }
        }.onFailure { err ->
            if (!silent) {
                postMessage("Serveur PC indisponible : ${err.message}")
            }
        }
    }

    fun stopDesktopServer() {
        desktopWebServer.stop()
        _uiState.update {
            it.copy(
                isDesktopServerRunning = false,
                snackbarMessage = "Serveur Version Ordinateur arrêté."
            )
        }
    }

    private fun unwrapAndLogCloudError(tag: String, err: Throwable) {
        val firestoreErr = generateSequence(err) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreErr == null && err is CancellationException) throw err
        Log.w("SmartStoreVM", "Cloud observe [$tag]: ${(firestoreErr ?: err).message}")
    }

    fun hasPermission(perm: AppPermission): Boolean {
        val state = _uiState.value
        val user = state.currentUser ?: return false
        if (user.roleCode == "ADMIN") return true
        return perm.code in state.currentUserPermissions
    }

    private fun requirePerm(perm: AppPermission, actionLabel: String, module: String): UserEntity? {
        val user = _uiState.value.currentUser
        if (user == null) {
            postMessage("Veuillez vous connecter.")
            return null
        }
        if (!hasPermission(perm)) {
            viewModelScope.launch {
                repository.recordUnauthorizedAttempt(user, actionLabel, module)
            }
            postMessage("Accès refusé : permission ${perm.label} requise.")
            return null
        }
        touchActivity()
        return user
    }

    fun touchActivity() {
        _uiState.update { it.copy(lastActivityMs = System.currentTimeMillis()) }
    }

    fun postMessage(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun loginStaff(username: String, password: String) {
        viewModelScope.launch {
            val res = repository.authenticateStaff(username, password)
            res.onSuccess { (user, perms) ->
                val defaultRoute = when (user.roleCode) {
                    "CASHIER" -> StoreRoute.POS
                    else -> StoreRoute.DASHBOARD
                }
                _uiState.update {
                    it.copy(
                        currentUser = user,
                        currentUserPermissions = perms,
                        currentRoute = defaultRoute,
                        loginError = null,
                        lastActivityMs = System.currentTimeMillis()
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(loginError = err.message ?: "Erreur d'authentification") }
            }
        }
    }

    fun setupInitialStructureAndAdmin(
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
        managerEmail: String = ""
    ) {
        viewModelScope.launch {
            val res = repository.setupInitialStructureAndAdmin(
                storeName = storeName,
                city = city,
                location = location,
                address = address,
                storePhone = storePhone,
                storeEmail = storeEmail,
                structureType = structureType,
                logoKey = logoKey,
                adminFullName = adminFullName,
                adminUsername = adminUsername,
                adminPassword = adminPassword,
                adminPhone = adminPhone,
                adminWhatsapp = adminWhatsapp,
                adminEmail = adminEmail,
                createManager = createManager,
                managerFullName = managerFullName,
                managerUsername = managerUsername,
                managerPassword = managerPassword,
                managerPhone = managerPhone,
                managerWhatsapp = managerWhatsapp,
                managerEmail = managerEmail,
                cloudUid = currentCloudUid
            )
            res.onSuccess { (user, perms) ->
                _uiState.update {
                    it.copy(
                        currentUser = user,
                        currentUserPermissions = perms,
                        currentRoute = StoreRoute.DASHBOARD,
                        loginError = null,
                        snackbarMessage = if (createManager) {
                            "Structure « ${storeName.trim()} » initialisée (Admin + Gestionnaire créés)."
                        } else {
                            "Structure « ${storeName.trim()} » initialisée."
                        },
                        lastActivityMs = System.currentTimeMillis()
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(loginError = err.message ?: "Erreur lors de l'initialisation") }
            }
        }
    }

    fun resetAllDataToEmpty() {
        viewModelScope.launch {
            repository.resetAllDataToEmpty()
            _uiState.update {
                it.copy(
                    currentUser = null,
                    currentUserPermissions = emptySet(),
                    cart = emptyList(),
                    selectedCustomer = null,
                    cartDiscountFcfa = 0.0,
                    lastValidatedSale = null,
                    lastValidatedInvoice = null,
                    activeOnlinePayment = null,
                    loginError = null,
                    snackbarMessage = "Toutes les données ont été vidées."
                )
            }
        }
    }

    fun performAdminReset(
        adminPassword: String,
        scope: AdminResetScope,
        createBackupFirst: Boolean,
        alsoCleanCloud: Boolean,
        onResult: (Result<String>) -> Unit
    ) {
        val actor = _uiState.value.currentUser
        if (actor == null) {
            onResult(Result.failure(IllegalStateException("Aucune session active")))
            return
        }
        viewModelScope.launch {
            val res = repository.performAdminReset(
                actor = actor,
                adminPassword = adminPassword,
                scope = scope,
                createBackupFirst = createBackupFirst,
                alsoCleanCloud = alsoCleanCloud,
                cloudUid = if (settings.value.offlineModeEnabled) null else currentCloudUid
            )
            res.onSuccess { msg ->
                if (scope == AdminResetScope.FULL_FACTORY_RESET) {
                    _uiState.update {
                        it.copy(
                            currentUser = null,
                            currentUserPermissions = emptySet(),
                            cart = emptyList(),
                            selectedCustomer = null,
                            cartDiscountFcfa = 0.0,
                            lastValidatedSale = null,
                            lastValidatedInvoice = null,
                            activeOnlinePayment = null,
                            loginError = null,
                            snackbarMessage = msg
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            cart = emptyList(),
                            selectedCustomer = null,
                            cartDiscountFcfa = 0.0,
                            lastValidatedSale = null,
                            lastValidatedInvoice = null,
                            activeOnlinePayment = null,
                            snackbarMessage = msg,
                            lastActivityMs = System.currentTimeMillis()
                        )
                    }
                }
            }
            onResult(res)
        }
    }

    fun changeOwnPassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val actor = _uiState.value.currentUser
        if (actor == null) {
            onResult(Result.failure(IllegalStateException("Veuillez vous connecter.")))
            return
        }
        if (newPassword != confirmPassword) {
            onResult(Result.failure(IllegalArgumentException("La confirmation ne correspond pas au nouveau mot de passe.")))
            return
        }
        touchActivity()
        viewModelScope.launch {
            val res = repository.changeOwnPassword(actor, currentPassword, newPassword)
            res.onSuccess { updatedUser ->
                _uiState.update {
                    it.copy(
                        currentUser = updatedUser,
                        snackbarMessage = "Votre mot de passe a été modifié avec succès.",
                        lastActivityMs = System.currentTimeMillis()
                    )
                }
                onResult(Result.success(Unit))
            }.onFailure { err ->
                onResult(Result.failure(err))
            }
        }
    }

    fun logoutStaff() {
        _uiState.update {
            it.copy(
                currentUser = null,
                currentUserPermissions = emptySet(),
                cart = emptyList(),
                cartDiscountFcfa = 0.0,
                loginError = null
            )
        }
    }

    fun navigateTo(route: StoreRoute) {
        touchActivity()
        _uiState.update { it.copy(currentRoute = route) }
    }

    // --- POS Cart & Checkout ---
    fun addToCart(product: ProductEntity) {
        touchActivity()
        if (product.stockQuantity <= 0) {
            postMessage("Produit en rupture de stock : ${product.name}")
            return
        }
        _uiState.update { state ->
            val existing = state.cart.find { it.product.id == product.id }
            val updated = if (existing != null) {
                if (existing.quantity >= product.stockQuantity) {
                    postMessage("Stock maximal atteint (${product.stockQuantity})")
                    return@update state
                }
                state.cart.map {
                    if (it.product.id == product.id) it.copy(quantity = it.quantity + 1) else it
                }
            } else {
                state.cart + CartLine(product, 1)
            }
            state.copy(cart = updated)
        }
    }

    fun scanOrLookupCode(query: String) {
        val code = query.trim()
        if (code.isBlank()) return
        val found = products.value.find {
            it.barcode.equals(code, ignoreCase = true) ||
                it.sku.equals(code, ignoreCase = true)
        }
        if (found != null) {
            addToCart(found)
            postMessage("Ajouté : ${found.name}")
        } else {
            postMessage("Aucun produit trouvé pour le code $code")
        }
    }

    fun updateCartQty(productId: Long, delta: Int) {
        touchActivity()
        _uiState.update { state ->
            val updated = state.cart.mapNotNull { line ->
                if (line.product.id != productId) line
                else {
                    val nextQty = line.quantity + delta
                    when {
                        nextQty <= 0 -> null
                        nextQty > line.product.stockQuantity -> {
                            postMessage("Stock disponible : ${line.product.stockQuantity}")
                            line
                        }
                        else -> line.copy(quantity = nextQty)
                    }
                }
            }
            state.copy(cart = updated)
        }
    }

    fun removeFromCart(productId: Long) {
        _uiState.update { state ->
            state.copy(cart = state.cart.filterNot { it.product.id == productId })
        }
    }

    fun setCartDiscount(amount: Double) {
        _uiState.update { it.copy(cartDiscountFcfa = amount.coerceAtLeast(0.0)) }
    }

    fun selectCustomer(customer: CustomerEntity?) {
        _uiState.update { it.copy(selectedCustomer = customer) }
    }

    fun validateSale(paymentMethod: String, amountPaid: Double, paymentRef: String) {
        val actor = requirePerm(AppPermission.CREATE_SALE, "Valider une vente POS", "Point de vente") ?: return
        val state = _uiState.value
        viewModelScope.launch {
            val res = repository.validateSale(
                actor = actor,
                cart = state.cart,
                customer = state.selectedCustomer,
                discountAmount = state.cartDiscountFcfa,
                paymentMethod = paymentMethod,
                amountPaid = amountPaid,
                paymentRef = paymentRef,
                cloudUid = if (settings.value.offlineModeEnabled) null else currentCloudUid
            )
            res.onSuccess { (sale, invoice) ->
                _uiState.update {
                    it.copy(
                        cart = emptyList(),
                        cartDiscountFcfa = 0.0,
                        selectedCustomer = null,
                        lastValidatedSale = sale,
                        lastValidatedInvoice = invoice,
                        snackbarMessage = "Vente ${sale.invoiceNumber} validée · Facture ${invoice.invoiceNumber} créée !"
                    )
                }
            }.onFailure { err ->
                postMessage(err.message ?: "Erreur lors de la validation")
            }
        }
    }

    fun dismissReceiptDialog() {
        _uiState.update { it.copy(lastValidatedSale = null, lastValidatedInvoice = null) }
    }

    fun cancelSale(sale: SaleEntity) {
        val actor = requirePerm(AppPermission.CANCEL_SALE, "Annuler la vente ${sale.invoiceNumber}", "Ventes") ?: return
        viewModelScope.launch {
            repository.cancelSale(actor, sale, if (settings.value.offlineModeEnabled) null else currentCloudUid)
                .onSuccess { postMessage("Vente ${sale.invoiceNumber} annulée et stock restauré.") }
                .onFailure { postMessage(it.message ?: "Impossible d'annuler") }
        }
    }

    // --- Manual / Proforma Invoicing ---
    fun createManualInvoice(
        type: String,
        customer: CustomerEntity?,
        customerNameFallback: String,
        amountHt: Double,
        discount: Double,
        paymentMethod: String,
        amountPaid: Double,
        notes: String,
        selectedLines: List<CartLine> = emptyList()
    ) {
        val actor = requirePerm(AppPermission.CREATE_SALE, "Émettre facture $type", "Facturation") ?: return
        viewModelScope.launch {
            val inv = repository.createManualInvoice(
                actor, type, customer, customerNameFallback, amountHt, discount, paymentMethod, amountPaid, notes, selectedLines
            )
            postMessage("Facture ${inv.invoiceNumber} générée automatiquement.")
        }
    }

    fun convertProformaToInvoice(proforma: InvoiceEntity) {
        val actor = requirePerm(AppPermission.CREATE_SALE, "Convertir proforma ${proforma.invoiceNumber}", "Facturation") ?: return
        viewModelScope.launch {
            val inv = repository.convertProformaToInvoice(actor, proforma)
            postMessage("Proforma convertie en facture officielle ${inv.invoiceNumber} !")
        }
    }

    fun settleInvoice(invoice: InvoiceEntity, paymentAmount: Double) {
        val actor = requirePerm(AppPermission.MODIFY_SALE, "Encaisser facture ${invoice.invoiceNumber}", "Facturation") ?: return
        viewModelScope.launch {
            repository.settleInvoicePayment(actor, invoice, paymentAmount)
            postMessage("Paiement enregistré sur ${invoice.invoiceNumber}")
        }
    }

    // --- Products ---
    fun saveProduct(product: ProductEntity, isNew: Boolean) {
        val perm = if (isNew) AppPermission.CREATE_PRODUCT else AppPermission.MODIFY_PRODUCT
        val actor = requirePerm(perm, "${if (isNew) "Créer" else "Modifier"} produit ${product.name}", "Produits") ?: return
        viewModelScope.launch {
            repository.saveProduct(actor, product, isNew, if (settings.value.offlineModeEnabled) null else currentCloudUid)
            postMessage("Produit ${product.name} enregistré.")
        }
    }

    fun deleteProduct(product: ProductEntity) {
        val actor = requirePerm(AppPermission.DELETE_PRODUCT, "Supprimer produit ${product.name}", "Produits") ?: return
        viewModelScope.launch {
            repository.deleteProduct(actor, product, if (settings.value.offlineModeEnabled) null else currentCloudUid)
            postMessage("Produit supprimé.")
        }
    }

    fun addCategory(name: String, sub: String, colorHex: String) {
        val actor = requirePerm(AppPermission.CREATE_PRODUCT, "Ajouter catégorie $name", "Produits") ?: return
        viewModelScope.launch {
            repository.addCategory(actor, name, sub, colorHex)
            postMessage("Catégorie $name ajoutée.")
        }
    }

    // --- Stock ---
    fun adjustStock(product: ProductEntity, type: String, qty: Int, reason: String) {
        val actor = requirePerm(AppPermission.MODIFY_STOCK, "Ajustement stock $type (${product.name})", "Stocks") ?: return
        viewModelScope.launch {
            repository.recordStockAdjustment(actor, product, type, qty, reason)
            postMessage("Mouvement de stock enregistré.")
        }
    }

    // --- Inventories ---
    fun scheduleInventory(title: String, frequency: String, dateMs: Long) {
        val actor = requirePerm(AppPermission.PERFORM_INVENTORY, "Programmer inventaire $frequency", "Inventaires") ?: return
        viewModelScope.launch {
            repository.scheduleInventory(actor, title, frequency, dateMs)
            postMessage("Inventaire préparé automatiquement.")
        }
    }

    fun updateInventoryCount(inventoryId: Long, item: InventoryItemEntity, actualQty: Int, damagedQty: Int, note: String) {
        requirePerm(AppPermission.PERFORM_INVENTORY, "Comptage physique ${item.productName}", "Inventaires") ?: return
        viewModelScope.launch {
            repository.updateInventoryItemCount(inventoryId, item, actualQty, damagedQty, note)
        }
    }

    fun submitInventory(inventory: InventoryEntity) {
        val actor = requirePerm(AppPermission.PERFORM_INVENTORY, "Soumettre inventaire ${inventory.title}", "Inventaires") ?: return
        viewModelScope.launch {
            repository.submitInventoryForValidation(actor, inventory)
            postMessage("Inventaire soumis à l'administrateur pour validation.")
        }
    }

    fun validateInventory(inventory: InventoryEntity) {
        val actor = requirePerm(AppPermission.VALIDATE_INVENTORY, "Valider inventaire ${inventory.title}", "Inventaires") ?: return
        viewModelScope.launch {
            repository.validateInventory(actor, inventory)
            postMessage("Inventaire validé et stocks corrigés !")
        }
    }

    // --- Purchases & Suppliers & Customers ---
    fun createPurchase(supplier: SupplierEntity, lines: List<PurchaseDraftLine>, status: String, notes: String) {
        val actor = requirePerm(AppPermission.MANAGE_PURCHASES, "Créer commande fournisseur", "Achats") ?: return
        viewModelScope.launch {
            repository.createPurchaseOrder(actor, supplier, lines, status, notes)
            postMessage("Commande fournisseur enregistrée.")
        }
    }

    fun receivePurchase(purchase: PurchaseEntity, partial: Boolean) {
        val actor = requirePerm(AppPermission.MANAGE_PURCHASES, "Réception commande ${purchase.orderNumber}", "Achats") ?: return
        viewModelScope.launch {
            repository.receivePurchaseOrder(actor, purchase, partial)
            postMessage("Réception enregistrée et stock mis à jour.")
        }
    }

    fun saveSupplier(supplier: SupplierEntity) {
        val actor = requirePerm(AppPermission.MANAGE_SUPPLIERS, "Enregistrer fournisseur ${supplier.name}", "Fournisseurs") ?: return
        viewModelScope.launch {
            repository.saveSupplier(actor, supplier)
            postMessage("Fournisseur enregistré.")
        }
    }

    fun saveCustomer(customer: CustomerEntity) {
        val actor = requirePerm(AppPermission.MANAGE_CUSTOMERS, "Enregistrer client ${customer.name}", "Clients") ?: return
        viewModelScope.launch {
            repository.saveCustomer(actor, customer)
            postMessage("Client enregistré.")
        }
    }

    fun settleCustomerCredit(customer: CustomerEntity, amount: Double) {
        val actor = requirePerm(AppPermission.MANAGE_CUSTOMERS, "Encaisser crédit client ${customer.name}", "Clients") ?: return
        viewModelScope.launch {
            repository.settleCustomerCredit(actor, customer, amount)
            postMessage("Crédit client mis à jour.")
        }
    }

    // --- Cash Register ---
    fun openCashRegister(openingBalance: Double) {
        val actor = requirePerm(AppPermission.OPEN_CASH_REGISTER, "Ouvrir caisse", "Caisse") ?: return
        viewModelScope.launch {
            repository.openCashRegister(actor, openingBalance)
                .onSuccess { postMessage("Caisse ouverte avec ${Formatters.fcfa(openingBalance)}") }
                .onFailure { postMessage(it.message ?: "Erreur") }
        }
    }

    fun addCashMovement(type: String, amount: Double, reason: String) {
        val actor = requirePerm(AppPermission.VIEW_CASH_REGISTER, "Mouvement caisse $type", "Caisse") ?: return
        viewModelScope.launch {
            repository.recordCashMovement(actor, type, amount, reason)
                .onSuccess { postMessage("Mouvement de caisse enregistré.") }
                .onFailure { postMessage(it.message ?: "Erreur") }
        }
    }

    fun closeCashRegister(actualCounted: Double, reason: String) {
        val actor = requirePerm(AppPermission.CLOSE_CASH_REGISTER, "Fermer caisse", "Caisse") ?: return
        viewModelScope.launch {
            repository.closeCashRegister(actor, actualCounted, reason)
                .onSuccess { closed ->
                    val status = when {
                        closed.discrepancy > 0.5 -> "Excédent de ${Formatters.fcfa(closed.discrepancy)}"
                        closed.discrepancy < -0.5 -> "Déficit de ${Formatters.fcfa(-closed.discrepancy)}"
                        else -> "Aucune différence"
                    }
                    postMessage("Caisse fermée : $status")
                }
                .onFailure { postMessage(it.message ?: "Erreur") }
        }
    }

    // --- Finances, Expenses & Reports ---
    fun addExpense(category: String, amount: Double, description: String, receiptRef: String) {
        val actor = requirePerm(AppPermission.VIEW_FINANCES, "Enregistrer dépense $category", "Finances") ?: return
        viewModelScope.launch {
            repository.addExpense(actor, category, amount, description, receiptRef)
            postMessage("Dépense enregistrée.")
        }
    }

    fun recordReport(type: String, title: String, period: String, summary: String) {
        val actor = requirePerm(AppPermission.VIEW_REPORTS, "Générer rapport $title", "Rapports") ?: return
        viewModelScope.launch {
            repository.recordGeneratedReport(actor, type, title, period, summary)
            postMessage("Rapport enregistré dans l'historique.")
        }
    }

    // --- Users, Employees HR, Permissions, Settings, Backups & Cloud Sync ---
    fun createUser(
        username: String,
        fullName: String,
        password: String,
        roleCode: String,
        phone: String = "",
        whatsappNumber: String = "",
        email: String = "",
        positionTitle: String = "",
        salaryFcfa: Double = 0.0
    ) {
        val actor = requirePerm(AppPermission.MANAGE_USERS, "Créer employé $username", "Utilisateurs") ?: return
        viewModelScope.launch {
            repository.createStaffUser(
                actor, username, fullName, password, roleCode,
                phone, whatsappNumber, email, positionTitle, salaryFcfa
            )
                .onSuccess { postMessage("Employé $fullName créé avec succès.") }
                .onFailure { postMessage(it.message ?: "Erreur") }
        }
    }

    fun updateUserProfile(
        target: UserEntity,
        fullName: String,
        phone: String,
        whatsappNumber: String,
        email: String,
        positionTitle: String,
        salaryFcfa: Double,
        roleCode: String,
        newPassword: String?
    ) {
        val actor = requirePerm(AppPermission.MANAGE_USERS, "Modifier fiche employé ${target.fullName}", "Utilisateurs") ?: return
        viewModelScope.launch {
            repository.updateStaffMember(
                actor, target, fullName, phone, whatsappNumber, email, positionTitle, salaryFcfa, roleCode, newPassword
            )
                .onSuccess { postMessage("Fiche de $fullName mise à jour.") }
                .onFailure { postMessage(it.message ?: "Erreur") }
        }
    }

    fun resetUserPasswordByAdmin(
        target: UserEntity,
        newPassword: String,
        confirmPassword: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val actor = requirePerm(AppPermission.MANAGE_USERS, "Changer mot de passe de ${target.fullName}", "Sécurité")
        if (actor == null) {
            onResult(Result.failure(IllegalStateException("Permission refusée.")))
            return
        }
        if (newPassword != confirmPassword) {
            onResult(Result.failure(IllegalArgumentException("Les mots de passe ne correspondent pas.")))
            return
        }
        viewModelScope.launch {
            repository.resetStaffPasswordByAdmin(actor, target, newPassword)
                .onSuccess {
                    postMessage("Mot de passe de ${target.fullName} mis à jour.")
                    onResult(Result.success(Unit))
                }
                .onFailure { err ->
                    onResult(Result.failure(err))
                }
        }
    }

    fun toggleUserActive(target: UserEntity) {
        val actor = requirePerm(AppPermission.MANAGE_USERS, "Activer/Révoquer ${target.username}", "Utilisateurs") ?: return
        viewModelScope.launch {
            repository.toggleUserActive(actor, target)
            postMessage("Statut de ${target.fullName} mis à jour.")
        }
    }

    fun setPermission(target: UserEntity, permCode: String, granted: Boolean) {
        val actor = requirePerm(AppPermission.MANAGE_USERS, "Modifier permission $permCode", "Permissions") ?: return
        viewModelScope.launch {
            repository.updateUserRoleAndPermission(actor, target, permCode, granted)
        }
    }

    fun saveSettings(newSettings: StoreSettingsEntity) {
        val actor = requirePerm(AppPermission.MANAGE_SETTINGS, "Modifier paramètres magasin", "Paramètres") ?: return
        viewModelScope.launch {
            repository.updateStoreSettings(
                actor,
                newSettings,
                if (newSettings.offlineModeEnabled) null else currentCloudUid
            )
            postMessage("Paramètres enregistrés et synchronisés.")
        }
    }

    fun toggleDarkTheme(dark: Boolean) {
        val actor = _uiState.value.currentUser
        viewModelScope.launch {
            val updated = settings.value.copy(darkThemeEnabled = dark)
            if (actor != null) {
                repository.updateStoreSettings(actor, updated)
            }
        }
    }

    fun toggleOfflineMode(offline: Boolean) {
        val actor = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            repository.updateStoreSettings(actor, settings.value.copy(offlineModeEnabled = offline))
            if (!offline) {
                syncNow()
            } else {
                postMessage("Mode hors ligne activé : les ventes seront mises en file d'attente locale.")
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            val res = repository.syncPendingToCloud(currentCloudUid)
            _uiState.update { it.copy(isSyncing = false) }
            res.onSuccess { count ->
                postMessage("Synchronisation cloud terminée ($count élément(s) synchronisé(s)).")
            }.onFailure { err ->
                postMessage("Erreur de synchro : ${err.localizedMessage ?: "réseau indisponible"}")
            }
        }
    }

    fun createBackup(label: String) {
        val actor = requirePerm(AppPermission.MANAGE_BACKUPS, "Créer sauvegarde", "Sauvegardes") ?: return
        viewModelScope.launch {
            repository.createBackupSnapshot(
                actor,
                label,
                "MANUELLE",
                if (settings.value.offlineModeEnabled) null else currentCloudUid
            )
            postMessage("Sauvegarde créée et synchronisée.")
        }
    }

    fun restoreBackup(jsonPayload: String) {
        val actor = requirePerm(AppPermission.MANAGE_BACKUPS, "Restaurer sauvegarde JSON", "Sauvegardes") ?: return
        viewModelScope.launch {
            repository.restoreFromBackupJson(actor, jsonPayload)
                .onSuccess { count -> postMessage("Restauration réussie ($count produits traités).") }
                .onFailure { postMessage("Fichier JSON invalide : ${it.message}") }
        }
    }

    fun importAnyFile(context: Context, uri: Uri, category: String, notes: String) {
        val actor = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            repository.importAnyDocumentFile(context, actor, uri, category, notes)
                .onSuccess { (_, summary) -> postMessage("Document importé : $summary") }
                .onFailure { postMessage("Erreur import : ${it.message}") }
        }
    }

    fun registerExportedDoc(
        context: Context,
        fileName: String,
        mimeType: String,
        ext: String,
        category: String,
        bytes: ByteArray,
        notes: String
    ) {
        val actor = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            repository.registerExportedDocument(context, actor, fileName, mimeType, ext, category, bytes, notes)
        }
    }

    fun deleteDocument(doc: DocumentFileEntity) {
        val actor = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            repository.deleteDocumentRecord(actor, doc)
            postMessage("Document supprimé.")
        }
    }

    fun markNotificationRead(id: Long) {
        viewModelScope.launch { repository.markNotificationRead(id) }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch { repository.markAllNotificationsRead() }
    }

    // --- Multi-company Admin Online Payment Gateway Configuration & Payments ---
    fun isCinetPayConfigured(): Boolean {
        val configs = onlinePaymentConfigs.value.filter { it.category.equals("CINETPAY", ignoreCase = true) }
        return if (configs.isNotEmpty()) {
            configs.any { OnlinePaymentService.isCinetPayConfigured(it) }
        } else {
            OnlinePaymentService.isCinetPayConfigured(null)
        }
    }

    fun isNotchPayConfigured(): Boolean {
        val configs = onlinePaymentConfigs.value.filter { it.category.equals("NOTCHPAY", ignoreCase = true) }
        return if (configs.isNotEmpty()) {
            configs.any { OnlinePaymentService.isNotchPayConfigured(it) }
        } else {
            OnlinePaymentService.isNotchPayConfigured(null)
        }
    }

    fun saveOnlinePaymentConfig(
        config: OnlinePaymentConfigEntity,
        onResult: (Result<OnlinePaymentConfigEntity>) -> Unit
    ) {
        val actor = requirePerm(AppPermission.MANAGE_SETTINGS, "Configurer mode de paiement ${config.displayName}", "Paiements en ligne")
        if (actor == null) {
            onResult(Result.failure(IllegalStateException("Permission d'administration requise.")))
            return
        }
        viewModelScope.launch {
            repository.saveOnlinePaymentConfig(actor, config)
                .onSuccess { saved ->
                    postMessage("Mode de paiement « ${saved.displayName} » enregistré.")
                    onResult(Result.success(saved))
                }
                .onFailure { err ->
                    onResult(Result.failure(err))
                }
        }
    }

    fun toggleOnlinePaymentConfig(config: OnlinePaymentConfigEntity, enabled: Boolean) {
        val actor = requirePerm(AppPermission.MANAGE_SETTINGS, "Activer/désactiver ${config.displayName}", "Paiements en ligne") ?: return
        viewModelScope.launch {
            repository.toggleOnlinePaymentConfig(actor, config, enabled)
                .onSuccess {
                    postMessage("« ${config.displayName} » ${if (enabled) "activé" else "désactivé"}.")
                }
                .onFailure { postMessage(it.message ?: "Erreur lors de la mise à jour.") }
        }
    }

    fun deleteOnlinePaymentConfig(config: OnlinePaymentConfigEntity) {
        val actor = requirePerm(AppPermission.MANAGE_SETTINGS, "Supprimer ${config.displayName}", "Paiements en ligne") ?: return
        viewModelScope.launch {
            repository.deleteOnlinePaymentConfig(actor, config)
                .onSuccess { postMessage("Mode de paiement « ${config.displayName} » supprimé.") }
                .onFailure { postMessage(it.message ?: "Erreur lors de la suppression.") }
        }
    }

    fun selectActiveOnlinePayment(payment: OnlinePaymentEntity?) {
        _uiState.update { it.copy(activeOnlinePayment = payment) }
    }

    fun initiateOnlinePayment(
        provider: String,
        channel: String,
        amountXaf: Double,
        customerName: String,
        customerPhone: String,
        customerEmail: String = "",
        invoiceNumber: String = "",
        description: String = "",
        configId: Long = 0L,
        onCreated: ((OnlinePaymentEntity) -> Unit)? = null
    ) {
        val actor = requirePerm(AppPermission.CREATE_SALE, "Initier paiement en ligne", "Paiements en ligne") ?: return
        if (amountXaf <= 0.0) {
            postMessage("Veuillez saisir un montant valide en FCFA.")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingOnlinePayment = true) }
            val created = repository.initiateOnlinePayment(
                actor = actor,
                provider = provider,
                channel = channel,
                amountXaf = amountXaf,
                customerName = customerName,
                customerPhone = customerPhone,
                customerEmail = customerEmail,
                invoiceNumber = invoiceNumber,
                description = description,
                configId = configId
            )
            _uiState.update {
                it.copy(
                    isProcessingOnlinePayment = false,
                    activeOnlinePayment = created,
                    snackbarMessage = created.gatewayMessage
                )
            }
            onCreated?.invoke(created)
        }
    }

    fun verifyOnlinePayment(
        payment: OnlinePaymentEntity,
        onPaid: ((OnlinePaymentEntity) -> Unit)? = null
    ) {
        val actor = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingOnlinePayment = true) }
            val updated = repository.verifyOnlinePayment(actor, payment)
            _uiState.update {
                it.copy(
                    isProcessingOnlinePayment = false,
                    activeOnlinePayment = if (it.activeOnlinePayment?.id == updated.id) updated else it.activeOnlinePayment,
                    snackbarMessage = updated.gatewayMessage
                )
            }
            if (updated.status == "CONFIRME") {
                onPaid?.invoke(updated)
            }
        }
    }

    fun confirmOnlinePaymentManually(
        payment: OnlinePaymentEntity,
        operatorRef: String,
        onPaid: ((OnlinePaymentEntity) -> Unit)? = null
    ) {
        val actor = requirePerm(AppPermission.CREATE_SALE, "Confirmer paiement comptoir", "Paiements en ligne") ?: return
        viewModelScope.launch {
            val updated = repository.confirmOnlinePaymentManually(actor, payment, operatorRef)
            _uiState.update {
                it.copy(
                    activeOnlinePayment = if (it.activeOnlinePayment?.id == updated.id) updated else it.activeOnlinePayment,
                    snackbarMessage = "Paiement ${updated.transactionId} confirmé (${Formatters.fcfa(updated.amountXaf)})"
                )
            }
            onPaid?.invoke(updated)
        }
    }

    fun cancelOnlinePayment(payment: OnlinePaymentEntity) {
        val actor = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            val updated = repository.cancelOnlinePayment(actor, payment)
            _uiState.update {
                it.copy(
                    activeOnlinePayment = if (it.activeOnlinePayment?.id == updated.id) null else it.activeOnlinePayment,
                    snackbarMessage = "Paiement ${updated.transactionId} annulé."
                )
            }
        }
    }
}
