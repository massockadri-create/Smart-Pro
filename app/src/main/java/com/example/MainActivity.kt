package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.AppDatabase
import com.example.data.remote.CloudSyncRepository
import com.example.data.repository.StoreRepository
import com.example.security.AppPermission
import com.example.security.GoogleAuthHelper
import com.example.ui.components.GoldPremiumBadge
import com.example.ui.components.LiveClockBadge
import com.example.ui.screens.*
import com.example.ui.theme.LocalSmartStoreColors
import com.example.ui.theme.SmartStoreProTheme
import com.example.ui.viewmodel.SmartStoreViewModel
import com.example.ui.viewmodel.StoreRoute
import com.example.util.StructureLogoBadge
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartStoreRoot()
        }
    }
}

@Composable
fun SmartStoreRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var firebaseUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            firebaseUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    if (firebaseUser == null) {
        SmartStoreProTheme(darkTheme = false) {
            CloudGoogleAuthScreen(
                onAuthSuccess = { firebaseUser = Firebase.auth.currentUser }
            )
        }
    } else {
        val uid = firebaseUser!!.uid
        val cloudEmail = firebaseUser!!.email ?: "compte-cloud"
        key(uid) {
            val viewModel: SmartStoreViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val db = AppDatabase.getInstance(context)
                        val cloud = CloudSyncRepository(context)
                        val repo = StoreRepository(db, cloud)
                        SmartStoreViewModel(repo, cloud, uid)
                    }
                }
            )
            SmartStoreAuthenticatedContent(
                viewModel = viewModel,
                cloudEmail = cloudEmail,
                onSignOutGoogle = {
                    GoogleAuthHelper.signOut(credentialManager, scope) {
                        firebaseUser = null
                    }
                }
            )
        }
    }
}

@Composable
private fun SmartStoreAuthenticatedContent(
    viewModel: SmartStoreViewModel,
    cloudEmail: String,
    onSignOutGoogle: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()

    SmartStoreProTheme(darkTheme = settings.darkThemeEnabled) {
        val activeUser = uiState.currentUser
        if (activeUser == null) {
            StaffLoginScreen(
                cloudEmail = cloudEmail,
                isDatabaseReady = uiState.isDatabaseReady,
                hasExistingUsers = users.isNotEmpty(),
                storeName = settings.storeName,
                storeCity = settings.city,
                storeLocation = settings.location,
                logoKey = settings.logoKey,
                loginError = uiState.loginError,
                onLogin = { u, p -> viewModel.loginStaff(u, p) },
                onInitialSetup = viewModel::setupInitialStructureAndAdmin,
                onSignOutGoogle = onSignOutGoogle
            )
        } else {
            SmartStoreMainShell(
                viewModel = viewModel,
                cloudEmail = cloudEmail,
                onSignOutGoogle = onSignOutGoogle
            )
        }
    }
}

private data class NavMeta(
    val route: StoreRoute,
    val icon: ImageVector,
    val requiredPerm: AppPermission? = null,
    val altRequiredPerm: AppPermission? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartStoreMainShell(
    viewModel: SmartStoreViewModel,
    cloudEmail: String,
    onSignOutGoogle: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var showChangePasswordDialog by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currentTimeMs by viewModel.currentTimeMs.collectAsStateWithLifecycle()
    val metrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val saleItems by viewModel.saleItems.collectAsStateWithLifecycle()
    val invoices by viewModel.invoices.collectAsStateWithLifecycle()
    val openRegister by viewModel.openRegister.collectAsStateWithLifecycle()
    val cashRegisters by viewModel.cashRegisters.collectAsStateWithLifecycle()
    val cashMovements by viewModel.cashMovements.collectAsStateWithLifecycle()
    val stockMovements by viewModel.stockMovements.collectAsStateWithLifecycle()
    val inventories by viewModel.inventories.collectAsStateWithLifecycle()
    val inventoryItems by viewModel.inventoryItems.collectAsStateWithLifecycle()
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val purchaseItems by viewModel.purchaseItems.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val userPermissions by viewModel.userPermissions.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val cloudAuditLogs by viewModel.cloudAuditLogs.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val backups by viewModel.backups.collectAsStateWithLifecycle()
    val cloudBackups by viewModel.cloudBackups.collectAsStateWithLifecycle()
    val onlinePayments by viewModel.onlinePayments.collectAsStateWithLifecycle()
    val onlinePaymentConfigs by viewModel.onlinePaymentConfigs.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessage()
    }

    val homeRoute = if (uiState.currentUser?.roleCode == "CASHIER") StoreRoute.POS else StoreRoute.DASHBOARD
    BackHandler(enabled = uiState.currentRoute != homeRoute) {
        viewModel.navigateTo(homeRoute)
    }

    val allNavItems = remember {
        listOf(
            NavMeta(StoreRoute.DASHBOARD, Icons.Rounded.Dashboard),
            NavMeta(StoreRoute.POS, Icons.Rounded.PointOfSale, AppPermission.CREATE_SALE),
            NavMeta(StoreRoute.ONLINE_PAYMENTS, Icons.Rounded.QrCode2, AppPermission.CREATE_SALE),
            NavMeta(StoreRoute.SALES_HISTORY, Icons.Rounded.Receipt, AppPermission.VIEW_SALES),
            NavMeta(StoreRoute.INVOICES, Icons.Rounded.ReceiptLong, AppPermission.VIEW_SALES),
            NavMeta(StoreRoute.PRODUCTS, Icons.Rounded.Inventory2, AppPermission.VIEW_PRODUCTS),
            NavMeta(StoreRoute.STOCKS, Icons.Rounded.Warehouse, AppPermission.VIEW_STOCK),
            NavMeta(StoreRoute.INVENTORIES, Icons.Rounded.FactCheck, AppPermission.PERFORM_INVENTORY),
            NavMeta(StoreRoute.PURCHASES, Icons.Rounded.LocalShipping, AppPermission.VIEW_PURCHASES),
            NavMeta(StoreRoute.SUPPLIERS, Icons.Rounded.Storefront, AppPermission.MANAGE_SUPPLIERS),
            NavMeta(StoreRoute.CUSTOMERS, Icons.Rounded.Groups, AppPermission.MANAGE_CUSTOMERS),
            NavMeta(StoreRoute.CASH_REGISTER, Icons.Rounded.AccountBalanceWallet, AppPermission.VIEW_CASH_REGISTER),
            NavMeta(StoreRoute.FINANCES, Icons.Rounded.TrendingUp, AppPermission.VIEW_FINANCES),
            NavMeta(StoreRoute.REPORTS, Icons.Rounded.Assessment, AppPermission.VIEW_REPORTS),
            NavMeta(StoreRoute.DOCUMENTS, Icons.Rounded.FolderCopy),
            NavMeta(StoreRoute.CALCULATOR, Icons.Rounded.Calculate),
            NavMeta(StoreRoute.DESKTOP_SYNC, Icons.Rounded.Computer),
            NavMeta(StoreRoute.USERS, Icons.Rounded.ManageAccounts, AppPermission.MANAGE_USERS),
            NavMeta(StoreRoute.SETTINGS, Icons.Rounded.Settings, AppPermission.MANAGE_SETTINGS, AppPermission.RESET_DATA),
            NavMeta(StoreRoute.AUDIT, Icons.Rounded.NotificationsActive)
        )
    }

    val allowedNavItems = remember(uiState.currentUser, uiState.currentUserPermissions) {
        allNavItems.filter {
            it.requiredPerm == null ||
                viewModel.hasPermission(it.requiredPerm) ||
                (it.altRequiredPerm != null && viewModel.hasPermission(it.altRequiredPerm))
        }
    }

    val bottomTabs = remember(allowedNavItems) {
        val preferred = listOf(
            StoreRoute.DASHBOARD,
            StoreRoute.POS,
            StoreRoute.PRODUCTS,
            StoreRoute.CASH_REGISTER,
            StoreRoute.DOCUMENTS
        )
        preferred.mapNotNull { r -> allowedNavItems.find { it.route == r } }.take(5)
    }

    val ext = LocalSmartStoreColors.current
    val unreadCount = notifications.count { !it.isRead }
    val drawerItemColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = ext.violetPrimary,
        selectedIconColor = Color.White,
        selectedTextColor = Color.White,
        unselectedContainerColor = Color.Transparent,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurface
    )
    val darkSidebarItemColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = ext.violetPrimary,
        selectedIconColor = Color.White,
        selectedTextColor = Color.White,
        unselectedContainerColor = Color.Transparent,
        unselectedIconColor = Color.White.copy(alpha = 0.72f),
        unselectedTextColor = Color.White.copy(alpha = 0.88f)
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.widthIn(max = 316.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ext.midnightSecondary
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StructureLogoBadge(logoKey = settings.logoKey, size = 40.dp)
                                Column {
                                    Text(
                                        settings.storeName.ifBlank { "SMART STORE PRO" },
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                    Text(
                                        "${uiState.currentUser?.fullName} (${uiState.currentUser?.roleCode})",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = ext.goldPremium
                                    )
                                }
                            }
                            LiveClockBadge(
                                epochMs = currentTimeMs,
                                isOnlineSync = !settings.offlineModeEnabled,
                                showDate = true
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    allowedNavItems.forEach { item ->
                        NavigationDrawerItem(
                            icon = { Icon(item.icon, contentDescription = item.route.title) },
                            label = { Text(item.route.title, fontWeight = FontWeight.SemiBold) },
                            selected = uiState.currentRoute == item.route,
                            shape = RoundedCornerShape(14.dp),
                            colors = drawerItemColors,
                            onClick = {
                                viewModel.navigateTo(item.route)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = ext.cardBorder)
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Rounded.Key, contentDescription = "Changer mon mot de passe") },
                        label = { Text("Changer mon mot de passe", fontWeight = FontWeight.SemiBold) },
                        selected = false,
                        shape = RoundedCornerShape(14.dp),
                        colors = drawerItemColors,
                        onClick = {
                            scope.launch { drawerState.close() }
                            showChangePasswordDialog = true
                        },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("drawer_change_password_item")
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Rounded.Lock, contentDescription = "Verrouiller session") },
                        label = { Text("Changer d'utilisateur", fontWeight = FontWeight.SemiBold) },
                        selected = false,
                        shape = RoundedCornerShape(14.dp),
                        colors = drawerItemColors,
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.logoutStaff()
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isDesktop = maxWidth >= 1000.dp
            val isTabletRail = maxWidth >= 840.dp && !isDesktop
            Row(modifier = Modifier.fillMaxSize()) {
                if (isDesktop) {
                    Surface(
                        modifier = Modifier
                            .width(268.dp)
                            .fillMaxHeight(),
                        color = ext.midnightSecondary,
                        shadowElevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StructureLogoBadge(logoKey = settings.logoKey, size = 38.dp)
                                Column {
                                    Text(
                                        settings.storeName.ifBlank { "SMART STORE PRO" },
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                    Text(
                                        "${settings.city}${if (settings.location.isNotBlank()) " · ${settings.location}" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.72f),
                                        maxLines = 1
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            LiveClockBadge(
                                epochMs = currentTimeMs,
                                isOnlineSync = !settings.offlineModeEnabled,
                                showDate = true
                            )
                            if (uiState.isDesktopServerRunning) {
                                Spacer(Modifier.height(4.dp))
                                GoldPremiumBadge("Web PC · ${uiState.desktopServerUrl.removePrefix("http://")}")
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.14f))
                            allowedNavItems.forEach { item ->
                                NavigationDrawerItem(
                                    icon = { Icon(item.icon, contentDescription = item.route.title) },
                                    label = { Text(item.route.title, maxLines = 1, fontWeight = FontWeight.SemiBold) },
                                    selected = uiState.currentRoute == item.route,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = darkSidebarItemColors,
                                    onClick = { viewModel.navigateTo(item.route) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.14f))
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Rounded.Key, contentDescription = "Changer mon mot de passe") },
                                label = { Text("Changer mot de passe", maxLines = 1, fontWeight = FontWeight.SemiBold) },
                                selected = false,
                                shape = RoundedCornerShape(14.dp),
                                colors = darkSidebarItemColors,
                                onClick = { showChangePasswordDialog = true },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                } else if (isTabletRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = ext.midnightSecondary
                    ) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Rounded.Menu, contentDescription = "Menu complet", tint = Color.White)
                            }
                            allowedNavItems.take(8).forEach { item ->
                                NavigationRailItem(
                                    selected = uiState.currentRoute == item.route,
                                    onClick = { viewModel.navigateTo(item.route) },
                                    icon = { Icon(item.icon, contentDescription = item.route.title) },
                                    label = { Text(item.route.title.substringBefore(" "), maxLines = 1) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = Color.White,
                                        selectedTextColor = Color.White,
                                        indicatorColor = ext.violetPrimary,
                                        unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                        unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        Column {
                            TopAppBar(
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                title = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        StructureLogoBadge(logoKey = settings.logoKey, size = 34.dp)
                                        Column {
                                            Text(
                                                uiState.currentRoute.title,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                maxLines = 1
                                            )
                                            val locLabel = listOf(settings.city, settings.location)
                                                .filter { it.isNotBlank() }
                                                .joinToString(" · ")
                                            Text(
                                                if (locLabel.isNotBlank()) "${settings.storeName} · $locLabel" else settings.storeName,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                },
                                navigationIcon = {
                                    IconButton(
                                        onClick = { scope.launch { drawerState.open() } },
                                        modifier = Modifier.testTag("drawer_menu_button")
                                    ) {
                                        Icon(Icons.Rounded.Menu, contentDescription = "Ouvrir le menu")
                                    }
                                },
                                actions = {
                                    LiveClockBadge(
                                        epochMs = currentTimeMs,
                                        isOnlineSync = !settings.offlineModeEnabled,
                                        showDate = false,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                    IconButton(
                                        onClick = { viewModel.navigateTo(StoreRoute.CALCULATOR) },
                                        modifier = Modifier.testTag("topbar_calculator_button")
                                    ) {
                                        Icon(Icons.Rounded.Calculate, contentDescription = "Calculatrice scientifique")
                                    }
                                    IconButton(onClick = { viewModel.navigateTo(StoreRoute.AUDIT) }) {
                                        BadgedBox(
                                            badge = {
                                                if (unreadCount > 0) {
                                                    Badge { Text("$unreadCount") }
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Rounded.Notifications, contentDescription = "Alertes")
                                        }
                                    }
                                    IconButton(
                                        onClick = { showChangePasswordDialog = true },
                                        modifier = Modifier.testTag("topbar_change_password_button")
                                    ) {
                                        Icon(Icons.Rounded.Key, contentDescription = "Changer mon mot de passe")
                                    }
                                    IconButton(onClick = { viewModel.toggleDarkTheme(!settings.darkThemeEnabled) }) {
                                        Icon(
                                            if (settings.darkThemeEnabled) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                            contentDescription = "Changer de thème"
                                        )
                                    }
                                    IconButton(onClick = { viewModel.logoutStaff() }) {
                                        Icon(Icons.Rounded.Logout, contentDescription = "Verrouiller session")
                                    }
                                }
                            )
                            HorizontalDivider(color = ext.cardBorder)
                        }
                    },
                    bottomBar = {
                        if (!isDesktop && !isTabletRail && bottomTabs.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    shadowElevation = 4.dp,
                                    tonalElevation = 0.dp,
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, ext.cardBorder)
                                ) {
                                    NavigationBar(
                                        windowInsets = WindowInsets(0),
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ) {
                                        bottomTabs.forEach { item ->
                                            val isSelected = uiState.currentRoute == item.route
                                            NavigationBarItem(
                                                selected = isSelected,
                                                onClick = { viewModel.navigateTo(item.route) },
                                                icon = { Icon(item.icon, contentDescription = item.route.title) },
                                                colors = NavigationBarItemDefaults.colors(
                                                    selectedIconColor = ext.violetPrimary,
                                                    selectedTextColor = ext.violetPrimary,
                                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                label = {
                                                    Text(
                                                        when (item.route) {
                                                            StoreRoute.DASHBOARD -> "Accueil"
                                                            StoreRoute.POS -> "POS"
                                                            StoreRoute.PRODUCTS -> "Produits"
                                                            StoreRoute.CASH_REGISTER -> "Caisse"
                                                            StoreRoute.DOCUMENTS -> "Docs"
                                                            else -> item.route.title
                                                        },
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        AnimatedContent(
                            targetState = uiState.currentRoute,
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(max = 1240.dp)
                                .fillMaxWidth(),
                            transitionSpec = {
                                (fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 32 })
                                    .togetherWith(fadeOut(tween(140)))
                            },
                            label = "route_transition"
                        ) { targetRoute ->
                            when (targetRoute) {
                            StoreRoute.DASHBOARD -> DashboardScreen(
                                metrics = metrics,
                                openRegister = openRegister,
                                recentSales = sales,
                                alerts = notifications,
                                cashMovements = cashMovements,
                                currentTimeMs = currentTimeMs,
                                offlineModeEnabled = settings.offlineModeEnabled,
                                canViewProfits = viewModel.hasPermission(AppPermission.VIEW_PROFITS),
                                isSyncing = uiState.isSyncing,
                                onNavigate = viewModel::navigateTo,
                                onSyncNow = viewModel::syncNow,
                                onMarkAlertRead = viewModel::markNotificationRead
                            )
                            StoreRoute.POS -> PosScreen(
                                products = products,
                                categories = categories,
                                customers = customers,
                                settings = settings,
                                cart = uiState.cart,
                                selectedCustomer = uiState.selectedCustomer,
                                discountFcfa = uiState.cartDiscountFcfa,
                                openRegister = openRegister,
                                lastValidatedSale = uiState.lastValidatedSale,
                                lastValidatedInvoice = uiState.lastValidatedInvoice,
                                saleItems = saleItems,
                                activeOnlinePayment = uiState.activeOnlinePayment,
                                isProcessingOnlinePayment = uiState.isProcessingOnlinePayment,
                                configuredOnlineGateways = onlinePaymentConfigs,
                                onAddToCart = viewModel::addToCart,
                                onScanCode = viewModel::scanOrLookupCode,
                                onUpdateQty = viewModel::updateCartQty,
                                onRemoveFromCart = viewModel::removeFromCart,
                                onSetDiscount = viewModel::setCartDiscount,
                                onSelectCustomer = viewModel::selectCustomer,
                                onValidateSale = viewModel::validateSale,
                                onDismissReceipt = viewModel::dismissReceiptDialog,
                                onOpenCashRegisterQuick = { viewModel.navigateTo(StoreRoute.CASH_REGISTER) },
                                onInitiateOnlinePayment = { provider, ch, amt, cname, cphone, cfgId ->
                                    viewModel.initiateOnlinePayment(
                                        provider = provider,
                                        channel = ch,
                                        amountXaf = amt,
                                        customerName = cname,
                                        customerPhone = cphone,
                                        configId = cfgId
                                    )
                                },
                                onVerifyOnlinePayment = { p, cb -> viewModel.verifyOnlinePayment(p, cb) },
                                onConfirmOnlinePaymentManual = { p, ref, cb -> viewModel.confirmOnlinePaymentManually(p, ref, cb) },
                                onCancelOnlinePayment = viewModel::cancelOnlinePayment,
                                onDismissOnlinePayment = { viewModel.selectActiveOnlinePayment(null) }
                            )
                            StoreRoute.ONLINE_PAYMENTS -> OnlinePaymentsScreen(
                                payments = onlinePayments,
                                customers = customers,
                                configuredGateways = onlinePaymentConfigs,
                                canConfigureGateways = viewModel.hasPermission(AppPermission.MANAGE_SETTINGS),
                                activePayment = uiState.activeOnlinePayment,
                                isProcessing = uiState.isProcessingOnlinePayment,
                                isCinetPayConfigured = viewModel.isCinetPayConfigured(),
                                isNotchPayConfigured = viewModel.isNotchPayConfigured(),
                                onOpenGatewaySettings = { viewModel.navigateTo(StoreRoute.SETTINGS) },
                                onSelectPayment = viewModel::selectActiveOnlinePayment,
                                onInitiatePayment = { provider, ch, amt, cname, cphone, cemail, desc, cfgId ->
                                    viewModel.initiateOnlinePayment(
                                        provider = provider,
                                        channel = ch,
                                        amountXaf = amt,
                                        customerName = cname,
                                        customerPhone = cphone,
                                        customerEmail = cemail,
                                        description = desc,
                                        configId = cfgId
                                    )
                                },
                                onVerifyPayment = { p -> viewModel.verifyOnlinePayment(p) },
                                onConfirmManual = { p, ref -> viewModel.confirmOnlinePaymentManually(p, ref) },
                                onCancelPayment = viewModel::cancelOnlinePayment
                            )
                            StoreRoute.SALES_HISTORY -> SalesHistoryScreen(
                                sales = sales,
                                saleItems = saleItems,
                                invoices = invoices,
                                settings = settings,
                                canCancel = viewModel.hasPermission(AppPermission.CANCEL_SALE),
                                onCancelSale = viewModel::cancelSale
                            )
                            StoreRoute.INVOICES -> InvoicesScreen(
                                invoices = invoices,
                                saleItems = saleItems,
                                products = products,
                                customers = customers,
                                settings = settings,
                                onCreateManualInvoice = viewModel::createManualInvoice,
                                onSettleInvoice = viewModel::settleInvoice,
                                onConvertProforma = viewModel::convertProformaToInvoice,
                                onPayInvoiceOnline = { inv ->
                                    val defaultGateway = onlinePaymentConfigs.firstOrNull { it.isEnabled }
                                    viewModel.initiateOnlinePayment(
                                        provider = defaultGateway?.displayName ?: "CinetPay",
                                        channel = "ALL",
                                        amountXaf = inv.balanceDue,
                                        customerName = inv.customerName,
                                        customerPhone = inv.customerPhone,
                                        invoiceNumber = inv.invoiceNumber,
                                        description = "Facture ${inv.invoiceNumber}",
                                        configId = defaultGateway?.id ?: 0L
                                    )
                                }
                            )
                            StoreRoute.PRODUCTS -> ProductsScreen(
                                products = products,
                                categories = categories,
                                suppliers = suppliers,
                                settings = settings,
                                onSaveProduct = viewModel::saveProduct,
                                onDeleteProduct = viewModel::deleteProduct,
                                onAddCategory = viewModel::addCategory,
                                onImportCsvUri = { uri -> viewModel.importAnyFile(context, uri, "CATALOGUE", "Import CSV produits") }
                            )
                            StoreRoute.STOCKS -> StocksScreen(
                                products = products,
                                movements = stockMovements,
                                onAdjustStock = viewModel::adjustStock
                            )
                            StoreRoute.INVENTORIES -> InventoriesScreen(
                                inventories = inventories,
                                inventoryItems = inventoryItems,
                                canValidate = viewModel.hasPermission(AppPermission.VALIDATE_INVENTORY),
                                onScheduleInventory = viewModel::scheduleInventory,
                                onUpdateCount = viewModel::updateInventoryCount,
                                onSubmitInventory = viewModel::submitInventory,
                                onValidateInventory = viewModel::validateInventory
                            )
                            StoreRoute.PURCHASES -> PurchasesScreen(
                                purchases = purchases,
                                purchaseItems = purchaseItems,
                                suppliers = suppliers,
                                products = products,
                                onCreatePurchase = viewModel::createPurchase,
                                onReceivePurchase = viewModel::receivePurchase
                            )
                            StoreRoute.SUPPLIERS -> SuppliersScreen(
                                suppliers = suppliers,
                                onSaveSupplier = viewModel::saveSupplier
                            )
                            StoreRoute.CUSTOMERS -> CustomersScreen(
                                customers = customers,
                                onSaveCustomer = viewModel::saveCustomer,
                                onSettleCredit = viewModel::settleCustomerCredit
                            )
                            StoreRoute.CASH_REGISTER -> CashRegisterScreen(
                                openRegister = openRegister,
                                registersHistory = cashRegisters,
                                movements = cashMovements,
                                currentTimeMs = currentTimeMs,
                                onOpenRegister = viewModel::openCashRegister,
                                onAddMovement = viewModel::addCashMovement,
                                onCloseRegister = viewModel::closeCashRegister
                            )
                            StoreRoute.FINANCES -> FinancesScreen(
                                sales = sales,
                                expenses = expenses,
                                canViewProfits = viewModel.hasPermission(AppPermission.VIEW_PROFITS),
                                currentTimeMs = currentTimeMs,
                                onAddExpense = viewModel::addExpense
                            )
                            StoreRoute.REPORTS -> ReportsScreen(
                                sales = sales,
                                products = products,
                                inventories = inventories,
                                purchases = purchases,
                                expenses = expenses,
                                customers = customers,
                                suppliers = suppliers,
                                cashRegisters = cashRegisters,
                                auditLogs = auditLogs,
                                reportsHistory = reports,
                                onRecordReport = viewModel::recordReport,
                                onRegisterExportedDoc = { fn, mime, ext, cat, bytes, notes ->
                                    viewModel.registerExportedDoc(context, fn, mime, ext, cat, bytes, notes)
                                }
                            )
                            StoreRoute.DOCUMENTS -> DocumentsScreen(
                                documents = documents,
                                onImportAnyUri = { uri, cat, notes -> viewModel.importAnyFile(context, uri, cat, notes) },
                                onDeleteDocument = viewModel::deleteDocument,
                                onPostMessage = viewModel::postMessage
                            )
                            StoreRoute.CALCULATOR -> ScientificCalculatorScreen(
                                vatRate = settings.vatRate,
                                onPostMessage = viewModel::postMessage
                            )
                            StoreRoute.DESKTOP_SYNC -> DesktopSyncScreen(
                                isServerRunning = uiState.isDesktopServerRunning,
                                serverUrl = uiState.desktopServerUrl,
                                localIps = uiState.desktopLocalIps,
                                port = uiState.desktopServerPort,
                                lastSyncAt = settings.lastSyncAt,
                                pendingSyncCount = metrics.pendingSyncCount,
                                offlineModeEnabled = settings.offlineModeEnabled,
                                isSyncing = uiState.isSyncing,
                                currentTimeMs = currentTimeMs,
                                onStartServer = { viewModel.startDesktopServer(silent = false) },
                                onStopServer = viewModel::stopDesktopServer,
                                onSyncCloudNow = viewModel::syncNow,
                                onPostMessage = viewModel::postMessage
                            )
                            StoreRoute.USERS -> UsersPermissionsScreen(
                                users = users,
                                userPermissions = userPermissions,
                                currentUser = uiState.currentUser,
                                onCreateUser = viewModel::createUser,
                                onUpdateUserProfile = viewModel::updateUserProfile,
                                onResetUserPassword = viewModel::resetUserPasswordByAdmin,
                                onChangeOwnPassword = viewModel::changeOwnPassword,
                                onToggleActive = viewModel::toggleUserActive,
                                onSetPermission = viewModel::setPermission
                            )
                            StoreRoute.SETTINGS -> SettingsBackupsScreen(
                                settings = settings,
                                currentUser = uiState.currentUser,
                                canManageSettings = viewModel.hasPermission(AppPermission.MANAGE_SETTINGS),
                                canResetData = viewModel.hasPermission(AppPermission.RESET_DATA),
                                onlinePaymentConfigs = onlinePaymentConfigs,
                                backups = backups,
                                cloudBackups = cloudBackups,
                                isSyncing = uiState.isSyncing,
                                cloudEmail = cloudEmail,
                                salesCount = sales.size,
                                invoicesCount = invoices.size,
                                productsCount = products.size,
                                customersCount = customers.size,
                                suppliersCount = suppliers.size,
                                usersCount = users.size,
                                onSaveSettings = viewModel::saveSettings,
                                onSaveOnlinePaymentConfig = viewModel::saveOnlinePaymentConfig,
                                onToggleOnlinePaymentConfig = viewModel::toggleOnlinePaymentConfig,
                                onDeleteOnlinePaymentConfig = viewModel::deleteOnlinePaymentConfig,
                                onToggleOfflineMode = viewModel::toggleOfflineMode,
                                onSyncNow = viewModel::syncNow,
                                onCreateBackup = viewModel::createBackup,
                                onRestoreBackupJson = viewModel::restoreBackup,
                                onChangeOwnPassword = viewModel::changeOwnPassword,
                                onAdminReset = viewModel::performAdminReset,
                                onSignOutGoogle = onSignOutGoogle
                            )
                            StoreRoute.AUDIT -> AuditAlertsScreen(
                                auditLogs = auditLogs,
                                cloudAuditLogs = cloudAuditLogs,
                                notifications = notifications,
                                onMarkRead = viewModel::markNotificationRead,
                                onMarkAllRead = viewModel::markAllNotificationsRead
                            )
                            }
                        }

                        // Global Online Payment QR & Barcode dialog (e.g. triggered from InvoicesScreen)
                        val activePay = uiState.activeOnlinePayment
                        if (activePay != null &&
                            uiState.currentRoute != StoreRoute.ONLINE_PAYMENTS &&
                            uiState.currentRoute != StoreRoute.POS
                        ) {
                            OnlinePaymentTicketDialog(
                                payment = activePay,
                                isProcessing = uiState.isProcessingOnlinePayment,
                                onDismiss = { viewModel.selectActiveOnlinePayment(null) },
                                onVerify = { viewModel.verifyOnlinePayment(activePay) },
                                onConfirmManual = { ref -> viewModel.confirmOnlinePaymentManually(activePay, ref) },
                                onCancel = { viewModel.cancelOnlinePayment(activePay) }
                            )
                        }

                        val me = uiState.currentUser
                        if (showChangePasswordDialog && me != null) {
                            ChangeOwnPasswordDialog(
                                fullName = me.fullName,
                                username = me.username,
                                onDismiss = { showChangePasswordDialog = false },
                                onSubmit = viewModel::changeOwnPassword
                            )
                        }
                    }
                }
            }
        }
    }
}
