package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.*
import com.example.data.repository.CartLine
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GoldPremiumBadge
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.PremiumSurfaceCard
import com.example.ui.components.RealQrCodeView
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.components.VisualBarcodeView
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalSmartStoreColors
import com.example.util.DocumentExporter
import com.example.util.Formatters
import com.example.util.StructureLogoBadge

@Composable
fun PosScreen(
    products: List<ProductEntity>,
    categories: List<CategoryEntity>,
    customers: List<CustomerEntity>,
    settings: StoreSettingsEntity,
    cart: List<CartLine>,
    selectedCustomer: CustomerEntity?,
    discountFcfa: Double,
    openRegister: CashRegisterEntity?,
    lastValidatedSale: SaleEntity?,
    lastValidatedInvoice: InvoiceEntity?,
    saleItems: List<SaleItemEntity>,
    activeOnlinePayment: OnlinePaymentEntity?,
    isProcessingOnlinePayment: Boolean,
    configuredOnlineGateways: List<OnlinePaymentConfigEntity> = emptyList(),
    onAddToCart: (ProductEntity) -> Unit,
    onScanCode: (String) -> Unit,
    onUpdateQty: (Long, Int) -> Unit,
    onRemoveFromCart: (Long) -> Unit,
    onSetDiscount: (Double) -> Unit,
    onSelectCustomer: (CustomerEntity?) -> Unit,
    onValidateSale: (String, Double, String) -> Unit,
    onDismissReceipt: () -> Unit,
    onOpenCashRegisterQuick: () -> Unit,
    onInitiateOnlinePayment: (provider: String, channel: String, amountXaf: Double, customerName: String, customerPhone: String, configId: Long) -> Unit,
    onVerifyOnlinePayment: (OnlinePaymentEntity, (OnlinePaymentEntity) -> Unit) -> Unit,
    onConfirmOnlinePaymentManual: (OnlinePaymentEntity, String, (OnlinePaymentEntity) -> Unit) -> Unit,
    onCancelOnlinePayment: (OnlinePaymentEntity) -> Unit,
    onDismissOnlinePayment: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Toutes") }
    var showBarcodeScannerDialog by remember { mutableStateOf(false) }
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var pendingOnlineMethod by remember { mutableStateOf<String?>(null) }
    var mobileTab by remember { mutableIntStateOf(0) } // 0 = Catalogue, 1 = Panier

    val filteredProducts = remember(products, searchQuery, selectedCategory) {
        products.filter { prod ->
            val matchCat = selectedCategory == "Toutes" || prod.categoryName == selectedCategory
            val q = searchQuery.trim()
            val matchQ = q.isBlank() ||
                prod.name.contains(q, ignoreCase = true) ||
                prod.sku.contains(q, ignoreCase = true) ||
                prod.barcode.contains(q, ignoreCase = true)
            prod.status == "ACTIF" && matchCat && matchQ
        }
    }

    val subtotal = cart.sumOf { it.product.sellingPrice * it.quantity }
    val netHt = (subtotal - discountFcfa).coerceAtLeast(0.0)
    val taxAmount = if (settings.taxEnabled) netHt * (settings.vatRate / 100.0) else 0.0
    val totalTtc = netHt + taxAmount

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 840.dp

        if (isWide) {
            Row(modifier = Modifier.fillMaxSize()) {
                PosCatalogPane(
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    onScanEnter = {
                        onScanCode(searchQuery)
                        searchQuery = ""
                    },
                    onOpenScanDialog = { showBarcodeScannerDialog = true },
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it },
                    products = filteredProducts,
                    openRegister = openRegister,
                    onOpenCashRegisterQuick = onOpenCashRegisterQuick,
                    onAddToCart = onAddToCart,
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PosCartPane(
                    cart = cart,
                    customers = customers,
                    selectedCustomer = selectedCustomer,
                    discountFcfa = discountFcfa,
                    subtotal = subtotal,
                    taxRate = if (settings.taxEnabled) settings.vatRate else 0.0,
                    taxAmount = taxAmount,
                    totalTtc = totalTtc,
                    onUpdateQty = onUpdateQty,
                    onRemoveFromCart = onRemoveFromCart,
                    onSetDiscount = onSetDiscount,
                    onSelectCustomer = onSelectCustomer,
                    onCheckout = { showCheckoutDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        } else {
            val ext = LocalSmartStoreColors.current
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(
                    selectedTabIndex = mobileTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = ext.violetPrimary
                ) {
                    Tab(
                        selected = mobileTab == 0,
                        onClick = { mobileTab = 0 },
                        text = { Text("Catalogue (${filteredProducts.size})", fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Rounded.GridView, contentDescription = null) }
                    )
                    Tab(
                        selected = mobileTab == 1,
                        onClick = { mobileTab = 1 },
                        text = { Text("Panier (${cart.sumOf { it.quantity }})", fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Rounded.ShoppingCart, contentDescription = null) }
                    )
                }
                if (mobileTab == 0) {
                    Box(modifier = Modifier.weight(1f)) {
                        PosCatalogPane(
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            onScanEnter = {
                                onScanCode(searchQuery)
                                searchQuery = ""
                            },
                            onOpenScanDialog = { showBarcodeScannerDialog = true },
                            categories = categories,
                            selectedCategory = selectedCategory,
                            onSelectCategory = { selectedCategory = it },
                            products = filteredProducts,
                            openRegister = openRegister,
                            onOpenCashRegisterQuick = onOpenCashRegisterQuick,
                            onAddToCart = onAddToCart,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (cart.isNotEmpty()) {
                            ExtendedFloatingActionButton(
                                onClick = { mobileTab = 1 },
                                shape = RoundedCornerShape(16.dp),
                                containerColor = ext.violetPrimary,
                                contentColor = Color.White,
                                icon = { Icon(Icons.Rounded.ShoppingCartCheckout, contentDescription = null) },
                                text = { Text("${cart.sumOf { it.quantity }} art. · ${Formatters.fcfa(totalTtc)}", fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                            )
                        }
                    }
                } else {
                    PosCartPane(
                        cart = cart,
                        customers = customers,
                        selectedCustomer = selectedCustomer,
                        discountFcfa = discountFcfa,
                        subtotal = subtotal,
                        taxRate = if (settings.taxEnabled) settings.vatRate else 0.0,
                        taxAmount = taxAmount,
                        totalTtc = totalTtc,
                        onUpdateQty = onUpdateQty,
                        onRemoveFromCart = onRemoveFromCart,
                        onSetDiscount = onSetDiscount,
                        onSelectCustomer = onSelectCustomer,
                        onCheckout = { showCheckoutDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showBarcodeScannerDialog) {
        var manualBarcode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBarcodeScannerDialog = false },
            shape = RoundedCornerShape(18.dp),
            title = { Text("Scanner / Saisir Code-barres ou SKU", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Utilisez un lecteur code-barres USB/Bluetooth ou sélectionnez un produit ci-dessous :",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = manualBarcode,
                        onValueChange = { manualBarcode = it },
                        label = { Text("Code-barres / SKU") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column(
                        modifier = Modifier
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        products.take(6).forEach { prod ->
                            OutlinedButton(
                                onClick = {
                                    onScanCode(prod.barcode.ifBlank { prod.sku })
                                    showBarcodeScannerDialog = false
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("${prod.name} (${prod.barcode.ifBlank { prod.sku }})", maxLines = 1, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onScanCode(manualBarcode)
                        showBarcodeScannerDialog = false
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Ajouter", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBarcodeScannerDialog = false }) { Text("Fermer") }
            }
        )
    }

    if (showCheckoutDialog && cart.isNotEmpty()) {
        PosCheckoutDialog(
            totalTtc = totalTtc,
            paymentMethods = settings.enabledPaymentMethods.split(",").map { it.trim() }.filter { it.isNotBlank() },
            configuredGateways = configuredOnlineGateways.filter { it.isEnabled },
            onDismiss = { showCheckoutDialog = false },
            onConfirm = { method, paid, ref ->
                showCheckoutDialog = false
                onValidateSale(method, paid, ref)
            },
            onPayOnline = { method, providerName, configId ->
                showCheckoutDialog = false
                pendingOnlineMethod = method
                onInitiateOnlinePayment(
                    providerName,
                    method,
                    totalTtc,
                    selectedCustomer?.name ?: "Client Comptant",
                    selectedCustomer?.phone ?: "",
                    configId
                )
            }
        )
    }

    if (activeOnlinePayment != null && pendingOnlineMethod != null) {
        OnlinePaymentTicketDialog(
            payment = activeOnlinePayment,
            isProcessing = isProcessingOnlinePayment,
            onDismiss = {
                pendingOnlineMethod = null
                onDismissOnlinePayment()
            },
            onVerify = {
                onVerifyOnlinePayment(activeOnlinePayment) { paidPay ->
                    val method = pendingOnlineMethod ?: paidPay.provider
                    pendingOnlineMethod = null
                    onDismissOnlinePayment()
                    onValidateSale(method, paidPay.amountXaf, paidPay.transactionId)
                }
            },
            onConfirmManual = { ref ->
                onConfirmOnlinePaymentManual(activeOnlinePayment, ref) { paidPay ->
                    val method = pendingOnlineMethod ?: paidPay.provider
                    pendingOnlineMethod = null
                    onDismissOnlinePayment()
                    onValidateSale(method, paidPay.amountXaf, ref.ifBlank { paidPay.transactionId })
                }
            },
            onCancel = {
                pendingOnlineMethod = null
                onCancelOnlinePayment(activeOnlinePayment)
            }
        )
    }

    if (lastValidatedSale != null && lastValidatedInvoice != null) {
        val items = saleItems.filter { it.saleId == lastValidatedSale.id }
        ReceiptAndInvoiceDialog(
            settings = settings,
            sale = lastValidatedSale,
            invoice = lastValidatedInvoice,
            items = items,
            onDismiss = onDismissReceipt
        )
    }
}

@Composable
private fun PosCatalogPane(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onScanEnter: () -> Unit,
    onOpenScanDialog: () -> Unit,
    categories: List<CategoryEntity>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    products: List<ProductEntity>,
    openRegister: CashRegisterEntity?,
    onOpenCashRegisterQuick: () -> Unit,
    onAddToCart: (ProductEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalSmartStoreColors.current
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (openRegister == null) {
            Surface(
                color = ext.goldSoftBg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, ext.goldPremium.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Aucune caisse ouverte. Ouvrez la caisse pour suivre les espèces.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.goldOnSoft,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onOpenCashRegisterQuick) {
                        Text("Ouvrir", fontWeight = FontWeight.SemiBold, color = ext.midnightSecondary)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Nom, SKU ou code-barres...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Effacer")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onScanEnter() }),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("pos_search_input")
            )
            FilledTonalIconButton(
                onClick = onOpenScanDialog,
                shape = RoundedCornerShape(14.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = ext.violetPrimary
                ),
                modifier = Modifier.size(52.dp)
            ) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scanner code-barres")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCategory == "Toutes",
                onClick = { onSelectCategory("Toutes") },
                shape = RoundedCornerShape(14.dp),
                label = { Text("Toutes", fontWeight = FontWeight.SemiBold) }
            )
            categories.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat.name,
                    onClick = { onSelectCategory(cat.name) },
                    shape = RoundedCornerShape(14.dp),
                    label = { Text(cat.name, fontWeight = FontWeight.SemiBold) }
                )
            }
        }

        if (products.isEmpty()) {
            EmptyStateCard("Aucun produit trouvé", "Modifiez votre recherche ou vérifiez le catalogue.")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 84.dp)
            ) {
                items(products, key = { "pos_prod_${it.id}" }) { prod ->
                    val stockColor = when {
                        prod.stockQuantity <= 0 -> MaterialTheme.colorScheme.error
                        prod.stockQuantity <= prod.minThreshold -> ext.goldOnSoft
                        else -> ext.successGreen
                    }
                    PremiumSurfaceCard(
                        modifier = Modifier.animateItem(),
                        onClick = if (prod.stockQuantity > 0) ({ onAddToCart(prod) }) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(prod.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    "${prod.sku} · ${prod.categoryName} · ${prod.unit}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (prod.reference.isNotBlank()) {
                                    Text(
                                        prod.reference,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = ext.violetPrimary
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(
                                    Formatters.fcfa(prod.sellingPrice),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = ext.violetPrimary
                                )
                                StatusBadge("Stock : ${prod.stockQuantity}", stockColor)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosCartPane(
    cart: List<CartLine>,
    customers: List<CustomerEntity>,
    selectedCustomer: CustomerEntity?,
    discountFcfa: Double,
    subtotal: Double,
    taxRate: Double,
    taxAmount: Double,
    totalTtc: Double,
    onUpdateQty: (Long, Int) -> Unit,
    onRemoveFromCart: (Long) -> Unit,
    onSetDiscount: (Double) -> Unit,
    onSelectCustomer: (CustomerEntity?) -> Unit,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalSmartStoreColors.current
    var showCustomerPicker by remember { mutableStateOf(false) }
    var discountInput by remember(discountFcfa) { mutableStateOf(if (discountFcfa > 0) discountFcfa.toInt().toString() else "") }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Panier en cours", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            OutlinedButton(
                onClick = { showCustomerPicker = true },
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(selectedCustomer?.name ?: "Client Comptant", maxLines = 1, fontWeight = FontWeight.SemiBold)
            }
        }

        if (cart.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyStateCard("Panier vide", "Touchez un produit du catalogue ou scannez un code-barres.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(cart, key = { "pos_cart_${it.product.id}" }) { line ->
                    PremiumSurfaceCard(modifier = Modifier.animateItem()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(line.product.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    "${Formatters.fcfa(line.product.sellingPrice)} × ${line.quantity} = ${Formatters.fcfa(line.product.sellingPrice * line.quantity)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = ext.violetPrimary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onUpdateQty(line.product.id, -1) }) {
                                    Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = "Diminuer")
                                }
                                Text("${line.quantity}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                IconButton(onClick = { onUpdateQty(line.product.id, 1) }) {
                                    Icon(Icons.Rounded.AddCircleOutline, contentDescription = "Augmenter")
                                }
                                IconButton(onClick = { onRemoveFromCart(line.product.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Retirer", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = discountInput,
            onValueChange = {
                discountInput = it
                onSetDiscount(it.toDoubleOrNull() ?: 0.0)
            },
            label = { Text("Remise globale (FCFA)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        PremiumSurfaceCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Sous-total :", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Formatters.fcfa(subtotal), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                }
                if (discountFcfa > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Remise :", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("- ${Formatters.fcfa(discountFcfa)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = ext.midnightSecondary)
                    }
                }
                if (taxRate > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TVA (${Formatters.percent(taxRate)}) :", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Formatters.fcfa(taxAmount), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = ext.cardBorder)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("TOTAL À PAYER :", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        Formatters.fcfa(totalTtc),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = ext.violetPrimary
                    )
                }
            }
        }

        Button(
            onClick = onCheckout,
            enabled = cart.isNotEmpty(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("pos_checkout_button")
        ) {
            Icon(Icons.Rounded.Payments, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Encaisser & Générer Facture", fontWeight = FontWeight.SemiBold)
        }
    }

    if (showCustomerPicker) {
        AlertDialog(
            onDismissRequest = { showCustomerPicker = false },
            shape = RoundedCornerShape(18.dp),
            title = { Text("Sélectionner un client", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = {
                        onSelectCustomer(null)
                        showCustomerPicker = false
                    }) {
                        Text("Client Comptant (Anonyme)", fontWeight = FontWeight.SemiBold)
                    }
                    customers.forEach { cust ->
                        OutlinedButton(
                            onClick = {
                                onSelectCustomer(cust)
                                showCustomerPicker = false
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${cust.name} · ${cust.phone}", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomerPicker = false }) { Text("Fermer") }
            }
        )
    }
}

@Composable
private fun PosCheckoutDialog(
    totalTtc: Double,
    paymentMethods: List<String>,
    configuredGateways: List<OnlinePaymentConfigEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String) -> Unit,
    onPayOnline: (String, String, Long) -> Unit
) {
    val ext = LocalSmartStoreColors.current
    val customGatewayNames = configuredGateways.map { it.displayName }.filter { it.isNotBlank() }
    val baseMethods = paymentMethods.ifEmpty {
        listOf("Espèces", "Mobile Money", "Orange Money", "MTN MoMo", "Carte", "Virement", "Autre")
    }
    val methods = (baseMethods + customGatewayNames).distinct()
    var selectedMethod by remember { mutableStateOf(methods.first()) }
    var selectedConfig by remember(configuredGateways) { mutableStateOf(configuredGateways.firstOrNull()) }
    var selectedFallbackGateway by remember { mutableStateOf(PaymentProviderOption.CINETPAY) }
    var paidText by remember { mutableStateOf(totalTtc.toInt().toString()) }
    var refCode by remember { mutableStateOf("") }
    val paid = paidText.toDoubleOrNull() ?: 0.0
    val change = (paid - totalTtc).coerceAtLeast(0.0)
    val remaining = (totalTtc - paid).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        title = { Text("Encaissement POS", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Total à encaisser : ${Formatters.fcfa(totalTtc)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ext.violetPrimary
                )
                Text("Mode de paiement :", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    methods.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { m ->
                                FilterChip(
                                    selected = selectedMethod == m,
                                    onClick = {
                                        selectedMethod = m
                                        configuredGateways.firstOrNull { it.displayName == m }?.let {
                                            selectedConfig = it
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    label = { Text(m, maxLines = 1, fontWeight = FontWeight.SemiBold) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = paidText,
                    onValueChange = { paidText = it },
                    label = { Text("Montant reçu (FCFA)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (!selectedMethod.equals("Espèces", ignoreCase = true)) {
                    OutlinedTextField(
                        value = refCode,
                        onValueChange = { refCode = it },
                        label = { Text("Référence transaction (OM / MoMo / TPE)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Ou encaissez en ligne avec QR & Code-barres :", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (configuredGateways.isNotEmpty()) {
                            configuredGateways.forEach { gw ->
                                FilterChip(
                                    selected = selectedConfig?.id == gw.id,
                                    onClick = { selectedConfig = gw },
                                    shape = RoundedCornerShape(14.dp),
                                    label = { Text(gw.displayName, fontWeight = FontWeight.SemiBold) }
                                )
                            }
                        } else {
                            PaymentProviderOption.entries.forEach { gw ->
                                FilterChip(
                                    selected = selectedFallbackGateway == gw,
                                    onClick = { selectedFallbackGateway = gw },
                                    shape = RoundedCornerShape(14.dp),
                                    label = { Text(gw.code, fontWeight = FontWeight.SemiBold) }
                                )
                            }
                        }
                    }
                    val activeGatewayLabel = selectedConfig?.displayName ?: selectedFallbackGateway.code
                    val activeGatewayId = selectedConfig?.id ?: 0L
                    FilledTonalButton(
                        onClick = { onPayOnline(selectedMethod, activeGatewayLabel, activeGatewayId) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.QrCode2, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Payer en ligne ($activeGatewayLabel) + Code-barres", fontWeight = FontWeight.SemiBold)
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ext.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Monnaie à rendre : ${Formatters.fcfa(change)}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = ext.successGreen
                        )
                        if (remaining > 0.5) {
                            Text(
                                "Crédit restant client : ${Formatters.fcfa(remaining)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = ext.midnightSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedMethod, paid, refCode) },
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Valider la vente", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
fun ReceiptAndInvoiceDialog(
    settings: StoreSettingsEntity,
    sale: SaleEntity,
    invoice: InvoiceEntity,
    items: List<SaleItemEntity>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                DocumentExporter.writeInvoicePdf(out, settings, invoice, items, sale.changeGiven)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ticket & Facture ${invoice.invoiceNumber}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StructureLogoBadge(logoKey = settings.logoKey, size = 36.dp)
                    Column {
                        Text(settings.storeName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("${settings.city}${if (settings.location.isNotBlank()) " · ${settings.location}" else ""}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text("${settings.address} · ${settings.phone}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider()
                Text("Client : ${invoice.customerName}", style = MaterialTheme.typography.bodySmall)
                Text("Caissier : ${sale.userName}", style = MaterialTheme.typography.bodySmall)
                Text("Paiement : ${sale.paymentMethod}", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                items.forEach { item ->
                    val code = item.productInvoiceCode.ifBlank {
                        Formatters.productInvoiceCode(invoice.invoiceNumber, item.categoryName)
                    }
                    val itemBarcode = item.productBarcode.ifBlank { item.sku }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${item.productName} ×${item.quantity}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "Code : $code · CB : $itemBarcode",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Text(Formatters.fcfa(item.lineTotal), style = MaterialTheme.typography.bodySmall)
                        }
                        VisualBarcodeView(code = itemBarcode, modifier = Modifier.fillMaxWidth())
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TOTAL TTC :", style = MaterialTheme.typography.titleMedium)
                    Text(Formatters.fcfa(invoice.totalTtc), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Monnaie rendue :", style = MaterialTheme.typography.bodySmall)
                    Text(Formatters.fcfa(sale.changeGiven), style = MaterialTheme.typography.bodySmall)
                }
                VisualBarcodeView(code = invoice.invoiceNumber, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                val html = DocumentExporter.buildInvoiceHtml(settings, invoice, items)
                DocumentExporter.printHtmlDocument(context, "Facture_${invoice.invoiceNumber}", html)
            }) {
                Icon(Icons.Rounded.Print, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Imprimer")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { pdfLauncher.launch("${invoice.invoiceNumber}.pdf") }) {
                    Text("PDF")
                }
                TextButton(onClick = onDismiss) { Text("Fermer") }
            }
        }
    )
}

@Composable
fun SalesHistoryScreen(
    sales: List<SaleEntity>,
    saleItems: List<SaleItemEntity>,
    invoices: List<InvoiceEntity>,
    settings: StoreSettingsEntity,
    canCancel: Boolean,
    onCancelSale: (SaleEntity) -> Unit
) {
    var filterStatus by remember { mutableStateOf("Toutes") }
    var search by remember { mutableStateOf("") }
    var inspectedSale by remember { mutableStateOf<SaleEntity?>(null) }

    val filtered = remember(sales, filterStatus, search) {
        sales.filter { s ->
            val okStatus = filterStatus == "Toutes" || s.status == filterStatus
            val okQuery = search.isBlank() ||
                s.invoiceNumber.contains(search, ignoreCase = true) ||
                s.customerName.contains(search, ignoreCase = true) ||
                s.paymentMethod.contains(search, ignoreCase = true)
            okStatus && okQuery
        }
    }

    val ext = LocalSmartStoreColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Historique des ventes & Tickets",
                subtitle = "${filtered.size} transaction(s)"
            )
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("N° ticket, client, mode de paiement...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Toutes", "VALIDEE", "ANNULEE").forEach { st ->
                    FilterChip(
                        selected = filterStatus == st,
                        onClick = { filterStatus = st },
                        shape = RoundedCornerShape(14.dp),
                        label = { Text(st, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        }
        items(filtered, key = { "sale_${it.id}" }) { sale ->
            PremiumSurfaceCard(
                modifier = Modifier.animateItem(),
                onClick = { inspectedSale = sale }
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${sale.invoiceNumber} · ${sale.customerName}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                        StatusBadge(
                            sale.status,
                            if (sale.status == "VALIDEE") ext.successGreen else MaterialTheme.colorScheme.error
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${sale.paymentMethod} · ${Formatters.dateTime(sale.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            Formatters.fcfa(sale.totalAmount),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = ext.violetPrimary
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Caissier : ${sale.userName} · Sync : ${sale.syncStatus}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (canCancel && sale.status == "VALIDEE") {
                            TextButton(onClick = { onCancelSale(sale) }) {
                                Text("Annuler", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    inspectedSale?.let { sale ->
        val fallbackNumber = if (sale.invoiceNumber.contains("-")) sale.invoiceNumber else "${settings.invoicePrefix.ifBlank { "FAC" }}-${sale.invoiceNumber}"
        val inv = invoices.find { it.saleId == sale.id } ?: InvoiceEntity(
            invoiceNumber = fallbackNumber,
            saleId = sale.id,
            customerName = sale.customerName,
            subtotalHt = sale.subtotal,
            discountAmount = sale.discountAmount,
            taxRate = sale.taxRate,
            taxAmount = sale.taxAmount,
            totalTtc = sale.totalAmount,
            amountPaid = sale.amountPaid,
            paymentMethod = sale.paymentMethod,
            status = if (sale.status == "VALIDEE") "PAYEE" else "ANNULEE",
            issuedByName = sale.userName,
            createdAt = sale.createdAt
        )
        ReceiptAndInvoiceDialog(
            settings = settings,
            sale = sale,
            invoice = inv,
            items = saleItems.filter { it.saleId == sale.id },
            onDismiss = { inspectedSale = null }
        )
    }
}

private fun resolveInvoiceItems(inv: InvoiceEntity, allItems: List<SaleItemEntity>): List<SaleItemEntity> =
    allItems.filter { (inv.saleId > 0L && it.saleId == inv.saleId) || (inv.saleId == 0L && it.saleId == -inv.id) }

@Composable
fun InvoicesScreen(
    invoices: List<InvoiceEntity>,
    saleItems: List<SaleItemEntity>,
    products: List<ProductEntity>,
    customers: List<CustomerEntity>,
    settings: StoreSettingsEntity,
    onCreateManualInvoice: (String, CustomerEntity?, String, Double, Double, String, Double, String, List<CartLine>) -> Unit,
    onSettleInvoice: (InvoiceEntity, Double) -> Unit,
    onConvertProforma: (InvoiceEntity) -> Unit,
    onPayInvoiceOnline: (InvoiceEntity) -> Unit
) {
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var inspectedInvoice by remember { mutableStateOf<InvoiceEntity?>(null) }
    var filterType by remember { mutableStateOf("TOUTES") }
    var searchQuery by remember { mutableStateOf("") }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                DocumentExporter.writeCsvBytes(
                    out,
                    listOf("Numéro", "Type", "Client", "Codes Produits", "Total TTC", "Payé", "Reste", "Statut", "Date"),
                    invoices.map { inv ->
                        val itemCodes = resolveInvoiceItems(inv, saleItems)
                            .map { it.productInvoiceCode.ifBlank { Formatters.productInvoiceCode(inv.invoiceNumber, it.categoryName) } }
                            .distinct()
                            .joinToString(" | ")
                        listOf(
                            inv.invoiceNumber, inv.invoiceType, inv.customerName, itemCodes,
                            inv.totalTtc.toInt().toString(), inv.amountPaid.toInt().toString(),
                            inv.balanceDue.toInt().toString(), inv.status, Formatters.dateTime(inv.createdAt)
                        )
                    }
                )
            }
        }
    }

    val activeInvoices = invoices.filter { it.status != "ANNULEE" && it.invoiceType != "PROFORMA" }
    val totalBilled = activeInvoices.sumOf { it.totalTtc }
    val totalCollected = activeInvoices.sumOf { it.amountPaid }
    val totalReceivable = activeInvoices.sumOf { it.balanceDue }

    val filtered = remember(invoices, saleItems, filterType, searchQuery) {
        val q = searchQuery.trim()
        invoices.filter { inv ->
            val okType = filterType == "TOUTES" || inv.status == filterType || inv.invoiceType == filterType
            val items = resolveInvoiceItems(inv, saleItems)
            val okQuery = q.isBlank() ||
                inv.invoiceNumber.contains(q, ignoreCase = true) ||
                inv.customerName.contains(q, ignoreCase = true) ||
                items.any {
                    it.productName.contains(q, ignoreCase = true) ||
                        it.productInvoiceCode.contains(q, ignoreCase = true) ||
                        it.categoryName.contains(q, ignoreCase = true)
                }
            okType && okQuery
        }
    }

    val ext = LocalSmartStoreColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Facturation & Codes Catégories",
                subtitle = "Chaque produit porte automatiquement le N° facture + début de sa catégorie",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { csvLauncher.launch("factures_smartstore.csv") },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.FileDownload, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("CSV", fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = { showCreateDialog = true },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Facture", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiMetricCard(
                    title = "Total facturé",
                    value = Formatters.fcfaCompact(totalBilled),
                    subtitle = "${activeInvoices.size} factures",
                    icon = Icons.Rounded.ReceiptLong,
                    accentColor = ext.violetPrimary,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = "Encaissé",
                    value = Formatters.fcfaCompact(totalCollected),
                    subtitle = "Reste : ${Formatters.fcfaCompact(totalReceivable)}",
                    icon = Icons.Rounded.CheckCircle,
                    accentColor = ext.successGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Rechercher N° facture, client, code FAC-...-CAT...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("TOUTES", "PAYEE", "PARTIELLE", "IMPAYEE", "PROFORMA", "AVOIR").forEach { f ->
                    FilterChip(
                        selected = filterType == f,
                        onClick = { filterType = f },
                        shape = RoundedCornerShape(14.dp),
                        label = { Text(f, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "Aucune facture trouvée",
                    message = "Créez une facture ou ajustez votre filtre."
                )
            }
        }

        items(filtered, key = { "inv_${it.id}" }) { inv ->
            val items = resolveInvoiceItems(inv, saleItems)
            val codes = items.map {
                it.productInvoiceCode.ifBlank { Formatters.productInvoiceCode(inv.invoiceNumber, it.categoryName) }
            }.distinct()
            val stColor = when (inv.status) {
                "PAYEE" -> ext.successGreen
                "PARTIELLE", "PROFORMA" -> ext.goldOnSoft
                else -> MaterialTheme.colorScheme.error
            }
            PremiumSurfaceCard(
                modifier = Modifier.animateItem(),
                onClick = { inspectedInvoice = inv }
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${inv.invoiceNumber} (${inv.invoiceType})", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text("Client : ${inv.customerName} · ${Formatters.dateTime(inv.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (inv.status == "PAYEE" && inv.totalTtc >= 100000.0) {
                            GoldPremiumBadge(inv.status)
                        } else {
                            StatusBadge(inv.status, stColor)
                        }
                    }
                    if (codes.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            codes.forEach { code ->
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        code,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = ext.violetPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Total TTC : ${Formatters.fcfa(inv.totalTtc)}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = ext.violetPrimary
                        )
                        if (inv.balanceDue > 0.5) {
                            Text("Reste : ${Formatters.fcfa(inv.balanceDue)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { inspectedInvoice = inv },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Détails", fontWeight = FontWeight.SemiBold)
                        }
                        FilledTonalButton(
                            onClick = {
                                val html = DocumentExporter.buildInvoiceHtml(settings, inv, items)
                                DocumentExporter.printHtmlDocument(context, inv.invoiceNumber, html)
                            },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Imprimer", fontWeight = FontWeight.SemiBold)
                        }
                        if (inv.invoiceType == "PROFORMA") {
                            Button(
                                onClick = { onConvertProforma(inv) },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Transform, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Convertir Facture", fontWeight = FontWeight.SemiBold)
                            }
                        } else if (inv.balanceDue > 0.5 && inv.status != "ANNULEE") {
                            Button(
                                onClick = { onPayInvoiceOnline(inv) },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Payer QR", fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = { onSettleInvoice(inv, inv.balanceDue) },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Solder", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }

    inspectedInvoice?.let { inv ->
        val items = resolveInvoiceItems(inv, saleItems)
        InvoiceDetailDialog(
            settings = settings,
            invoice = inv,
            items = items,
            onDismiss = { inspectedInvoice = null },
            onConvertProforma = {
                onConvertProforma(inv)
                inspectedInvoice = null
            },
            onPayOnline = {
                inspectedInvoice = null
                onPayInvoiceOnline(inv)
            },
            onSettle = {
                onSettleInvoice(inv, inv.balanceDue)
                inspectedInvoice = null
            }
        )
    }

    if (showCreateDialog) {
        var type by remember { mutableStateOf("FACTURE") }
        var custName by remember { mutableStateOf("") }
        var selectedCust by remember { mutableStateOf<CustomerEntity?>(null) }
        var draftLines by remember { mutableStateOf<List<CartLine>>(emptyList()) }
        var productSearch by remember { mutableStateOf("") }
        var amountHt by remember { mutableStateOf("") }
        var discount by remember { mutableStateOf("") }
        var paid by remember { mutableStateOf("") }
        var method by remember { mutableStateOf("Orange Money") }
        var notes by remember { mutableStateOf("") }

        val previewPrefix = when (type) {
            "PROFORMA" -> "PRO"
            "AVOIR" -> "AVR"
            else -> settings.invoicePrefix.ifBlank { "FAC" }
        }
        val previewInvoiceNum = "$previewPrefix-${settings.nextInvoiceNumber}"
        val itemsSubtotal = draftLines.sumOf { it.product.sellingPrice * it.quantity }
        val effectiveHt = if (draftLines.isNotEmpty()) itemsSubtotal else (amountHt.toDoubleOrNull() ?: 0.0)

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            shape = RoundedCornerShape(18.dp),
            title = { Text("Émettre $type · $previewInvoiceNum", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("FACTURE", "PROFORMA", "AVOIR").forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = t }, shape = RoundedCornerShape(14.dp), label = { Text(t, fontWeight = FontWeight.SemiBold) })
                        }
                    }
                    OutlinedTextField(
                        value = selectedCust?.name ?: custName,
                        onValueChange = { selectedCust = null; custName = it },
                        label = { Text("Nom du client") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        customers.forEach { c ->
                            AssistChip(onClick = { selectedCust = c; custName = c.name }, shape = RoundedCornerShape(14.dp), label = { Text(c.name) })
                        }
                    }
                    Text("Ajouter des produits (code facture + catégorie auto) :", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                    OutlinedTextField(
                        value = productSearch,
                        onValueChange = { productSearch = it },
                        placeholder = { Text("Filtrer produit...") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    val matchingProds = remember(products, productSearch) {
                        products.filter {
                            productSearch.isBlank() || it.name.contains(productSearch, ignoreCase = true)
                        }.take(5)
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        matchingProds.forEach { p ->
                            AssistChip(
                                onClick = {
                                    val existing = draftLines.find { it.product.id == p.id }
                                    draftLines = if (existing != null) {
                                        draftLines.map { if (it.product.id == p.id) it.copy(quantity = it.quantity + 1) else it }
                                    } else {
                                        draftLines + CartLine(p, 1)
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                label = { Text("+ ${p.name}") }
                            )
                        }
                    }
                    if (draftLines.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, ext.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                draftLines.forEach { line ->
                                    val autoCode = Formatters.productInvoiceCode(previewInvoiceNum, line.product.categoryName)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${line.product.name} ×${line.quantity}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                            Text(autoCode, style = MaterialTheme.typography.labelSmall, color = ext.violetPrimary)
                                        }
                                        IconButton(onClick = {
                                            draftLines = draftLines.filterNot { it.product.id == line.product.id }
                                        }) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Retirer", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                Text(
                                    "Total produits : ${Formatters.fcfa(itemsSubtotal)}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = ext.violetPrimary
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = amountHt,
                            onValueChange = { amountHt = it },
                            label = { Text("Ou Montant HT direct (FCFA)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = discount,
                            onValueChange = { discount = it },
                            placeholder = { Text("0") },
                            label = { Text("Remise (FCFA)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        )
                        if (type != "PROFORMA") {
                            OutlinedTextField(
                                value = paid,
                                onValueChange = { paid = it },
                                label = { Text("Montant versé") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Espèces", "Orange Money", "MTN MoMo", "Virement", "Carte").forEach { m ->
                            FilterChip(selected = method == m, onClick = { method = m }, shape = RoundedCornerShape(14.dp), label = { Text(m, fontWeight = FontWeight.SemiBold) })
                        }
                    }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Objet / Notes") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (effectiveHt > 0) {
                            val paidVal = if (type == "PROFORMA") 0.0 else (paid.toDoubleOrNull() ?: effectiveHt)
                            onCreateManualInvoice(
                                type, selectedCust, custName, effectiveHt,
                                discount.toDoubleOrNull() ?: 0.0,
                                method, paidVal, notes, draftLines
                            )
                            showCreateDialog = false
                        }
                    },
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Générer", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun InvoiceDetailDialog(
    settings: StoreSettingsEntity,
    invoice: InvoiceEntity,
    items: List<SaleItemEntity>,
    onDismiss: () -> Unit,
    onConvertProforma: () -> Unit,
    onPayOnline: () -> Unit,
    onSettle: () -> Unit
) {
    val context = LocalContext.current
    val ext = LocalSmartStoreColors.current
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    DocumentExporter.writeInvoicePdf(out, settings, invoice, items)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        title = { Text("${invoice.invoiceType} ${invoice.invoiceNumber}", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StructureLogoBadge(logoKey = settings.logoKey, size = 36.dp)
                    Column {
                        Text(settings.storeName, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = ext.violetPrimary)
                        Text("${settings.city}${if (settings.location.isNotBlank()) " · ${settings.location}" else ""} — ${settings.address}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = ext.cardBorder)
                Text("Client : ${invoice.customerName}", fontWeight = FontWeight.SemiBold)
                Text("Date : ${Formatters.dateTimeWithSeconds(invoice.createdAt)}", style = MaterialTheme.typography.bodySmall)
                Text("Statut : ${invoice.status} · Paiement : ${invoice.paymentMethod}", style = MaterialTheme.typography.bodySmall)
                if (invoice.notes.isNotBlank()) {
                    Text(invoice.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = ext.cardBorder)
                if (items.isEmpty()) {
                    Text("Prestation / Facturation directe", style = MaterialTheme.typography.bodySmall)
                } else {
                    items.forEach { item ->
                        val code = item.productInvoiceCode.ifBlank { Formatters.productInvoiceCode(invoice.invoiceNumber, item.categoryName) }
                        val itemBarcode = item.productBarcode.ifBlank { item.sku }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${item.productName} ×${item.quantity}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                    Text("$code · ${item.categoryName} · CB: $itemBarcode", style = MaterialTheme.typography.labelSmall, color = ext.violetPrimary)
                                }
                                Text(
                                    Formatters.fcfa(item.lineTotal),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold)
                                )
                            }
                            VisualBarcodeView(code = itemBarcode, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                HorizontalDivider(color = ext.cardBorder)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total TTC :", fontWeight = FontWeight.Bold)
                    Text(Formatters.fcfa(invoice.totalTtc), fontWeight = FontWeight.Bold, color = ext.violetPrimary)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Payé : ${Formatters.fcfa(invoice.amountPaid)}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Reste : ${Formatters.fcfa(invoice.balanceDue)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (invoice.balanceDue > 0.5) MaterialTheme.colorScheme.error else ext.successGreen
                    )
                }
                VisualBarcodeView(code = invoice.invoiceNumber, modifier = Modifier.fillMaxWidth())
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RealQrCodeView(
                        content = "SMARTSTORE|${invoice.invoiceNumber}|${invoice.totalTtc.toInt()}XAF|${invoice.customerName}",
                        caption = invoice.invoiceNumber
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (invoice.invoiceType == "PROFORMA") {
                    Button(onClick = onConvertProforma, shape = RoundedCornerShape(14.dp)) { Text("Convertir", fontWeight = FontWeight.SemiBold) }
                } else if (invoice.balanceDue > 0.5 && invoice.status != "ANNULEE") {
                    Button(onClick = onPayOnline, shape = RoundedCornerShape(14.dp)) { Text("Payer QR", fontWeight = FontWeight.SemiBold) }
                    OutlinedButton(onClick = onSettle, shape = RoundedCornerShape(14.dp)) { Text("Solder", fontWeight = FontWeight.SemiBold) }
                }
                FilledTonalButton(
                    onClick = {
                        val html = DocumentExporter.buildInvoiceHtml(settings, invoice, items)
                        DocumentExporter.printHtmlDocument(context, invoice.invoiceNumber, html)
                    },
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Imprimer", fontWeight = FontWeight.SemiBold) }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { pdfLauncher.launch("${invoice.invoiceNumber}.pdf") }, shape = RoundedCornerShape(14.dp)) { Text("PDF", fontWeight = FontWeight.SemiBold) }
                TextButton(onClick = onDismiss) { Text("Fermer") }
            }
        }
    )
}
