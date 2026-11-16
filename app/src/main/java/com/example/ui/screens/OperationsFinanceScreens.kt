package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.*
import com.example.data.repository.PurchaseDraftLine
import com.example.ui.components.*
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalSmartStoreColors
import com.example.util.Formatters

@Composable
fun PurchasesScreen(
    purchases: List<PurchaseEntity>,
    purchaseItems: List<PurchaseItemEntity>,
    suppliers: List<SupplierEntity>,
    products: List<ProductEntity>,
    onCreatePurchase: (SupplierEntity, List<PurchaseDraftLine>, String, String) -> Unit,
    onReceivePurchase: (PurchaseEntity, Boolean) -> Unit
) {
    val ext = LocalSmartStoreColors.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var filterStatus by remember { mutableStateOf("TOUS") }

    val filtered = remember(purchases, filterStatus) {
        purchases.filter { filterStatus == "TOUS" || it.status == filterStatus }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Achats & Réceptions fournisseurs",
                subtitle = "La réception met automatiquement à jour le stock et le coût d'achat",
                action = {
                    Button(
                        onClick = { showCreateDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("new_purchase_button")
                    ) {
                        Icon(Icons.Rounded.AddShoppingCart, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Commander", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("TOUS", "COMMANDE", "PARTIELLEMENT_RECU", "RECU", "BROUILLON").forEach { st ->
                    FilterChip(
                        selected = filterStatus == st,
                        onClick = { filterStatus = st },
                        shape = RoundedCornerShape(14.dp),
                        label = { Text(st, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "Aucune commande fournisseur",
                    message = "Créez une commande pour approvisionner automatiquement vos stocks."
                )
            }
        } else {
            items(filtered, key = { "pur_${it.id}" }) { purchase ->
                val items = purchaseItems.filter { it.purchaseId == purchase.id }
                val badgeColor = when (purchase.status) {
                    "RECU" -> ext.successGreen
                    "PARTIELLEMENT_RECU" -> ext.goldOnSoft
                    "ANNULE" -> MaterialTheme.colorScheme.error
                    else -> ext.violetPrimary
                }
                PremiumSurfaceCard(modifier = Modifier.animateItem()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${purchase.orderNumber} · ${purchase.supplierName}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text("${Formatters.dateTime(purchase.createdAt)} · Par ${purchase.userName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusBadge(purchase.status, badgeColor)
                        }
                        if (items.isNotEmpty()) {
                            items.forEach { line ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("• ${line.productName} (${line.receivedQty}/${line.orderedQty})", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        Formatters.fcfa(line.lineTotal),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = ext.cardBorder)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Total : ${Formatters.fcfa(purchase.totalAmount)}",
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold),
                                color = ext.violetPrimary
                            )
                            if (purchase.status in setOf("BROUILLON", "COMMANDE", "PARTIELLEMENT_RECU")) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (purchase.status != "PARTIELLEMENT_RECU") {
                                        OutlinedButton(onClick = { onReceivePurchase(purchase, true) }, shape = RoundedCornerShape(14.dp)) {
                                            Text("Réception partielle", fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    Button(onClick = { onReceivePurchase(purchase, false) }, shape = RoundedCornerShape(14.dp)) {
                                        Text("Réception totale", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        if (suppliers.isNotEmpty() && products.isNotEmpty()) {
            CreatePurchaseDialog(
                suppliers = suppliers,
                products = products,
                onDismiss = { showCreateDialog = false },
                onConfirm = { supplier, lines, status, notes ->
                    onCreatePurchase(supplier, lines, status, notes)
                    showCreateDialog = false
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                shape = RoundedCornerShape(18.dp),
                title = { Text("Prérequis manquants", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Veuillez d'abord enregistrer au moins un fournisseur et un produit dans votre catalogue avant de créer une commande d'achat."
                    )
                },
                confirmButton = {
                    Button(onClick = { showCreateDialog = false }, shape = RoundedCornerShape(14.dp)) { Text("Compris", fontWeight = FontWeight.SemiBold) }
                }
            )
        }
    }
}

@Composable
private fun CreatePurchaseDialog(
    suppliers: List<SupplierEntity>,
    products: List<ProductEntity>,
    onDismiss: () -> Unit,
    onConfirm: (SupplierEntity, List<PurchaseDraftLine>, String, String) -> Unit
) {
    val ext = LocalSmartStoreColors.current
    var selectedSupplier by remember(suppliers) { mutableStateOf(suppliers.firstOrNull()) }
    var selectedProduct by remember(products) { mutableStateOf(products.firstOrNull()) }
    if (selectedSupplier == null || selectedProduct == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var qtyStr by remember { mutableStateOf("") }
    var costStr by remember(selectedProduct) {
        mutableStateOf(
            selectedProduct?.purchasePrice?.takeIf { it > 0 }?.toInt()?.toString().orEmpty()
        )
    }
    var draftLines by remember { mutableStateOf<List<PurchaseDraftLine>>(emptyList()) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        title = { Text("Nouvelle commande fournisseur", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Fournisseur :", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    suppliers.forEach { sup ->
                        FilterChip(
                            selected = selectedSupplier?.id == sup.id,
                            onClick = { selectedSupplier = sup },
                            shape = RoundedCornerShape(14.dp),
                            label = { Text(sup.company.ifBlank { sup.name }, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
                Text("Ajouter un produit :", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    products.take(12).forEach { prod ->
                        FilterChip(
                            selected = selectedProduct?.id == prod.id,
                            onClick = {
                                selectedProduct = prod
                                costStr = if (prod.purchasePrice > 0) prod.purchasePrice.toInt().toString() else ""
                            },
                            shape = RoundedCornerShape(14.dp),
                            label = { Text(prod.name, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyStr,
                        onValueChange = { qtyStr = it },
                        placeholder = { Text("1") },
                        label = { Text("Qté") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = costStr,
                        onValueChange = { costStr = it },
                        label = { Text("Coût U. FCFA") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1.4f)
                    )
                }
                FilledTonalButton(
                    onClick = {
                        val prod = selectedProduct ?: return@FilledTonalButton
                        val q = qtyStr.toIntOrNull() ?: 0
                        val c = costStr.toDoubleOrNull() ?: 0.0
                        if (q > 0 && c >= 0.0) {
                            draftLines = draftLines + PurchaseDraftLine(prod, q, c)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Ajouter ligne", fontWeight = FontWeight.SemiBold)
                }
                if (draftLines.isNotEmpty()) {
                    HorizontalDivider(color = ext.cardBorder)
                    draftLines.forEach { line ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${line.product.name} x${line.quantity}", style = MaterialTheme.typography.bodySmall)
                            Text(Formatters.fcfa(line.quantity * line.unitCost), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                    Text(
                        "Total commande : ${Formatters.fcfa(draftLines.sumOf { it.quantity * it.unitCost })}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = ext.violetPrimary
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Livraison") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedSupplier?.let { sup ->
                        onConfirm(sup, draftLines, "COMMANDE", notes)
                    }
                },
                enabled = draftLines.isNotEmpty() && selectedSupplier != null,
                shape = RoundedCornerShape(14.dp)
            ) { Text("Créer commande", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun SuppliersScreen(
    suppliers: List<SupplierEntity>,
    onSaveSupplier: (SupplierEntity) -> Unit
) {
    val ext = LocalSmartStoreColors.current
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<SupplierEntity?>(null) }

    val filtered = remember(suppliers, search) {
        suppliers.filter {
            search.isBlank() ||
                it.name.contains(search, ignoreCase = true) ||
                it.company.contains(search, ignoreCase = true) ||
                it.city.contains(search, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Fournisseurs & Partenaires",
                subtitle = "${filtered.size} fournisseur(s) · Suivi achats et dettes FCFA",
                action = {
                    Button(
                        onClick = { editing = SupplierEntity(name = "", company = "", phone = "") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("add_supplier_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Ajouter", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Rechercher fournisseur, entreprise, ville...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (filtered.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "Aucun fournisseur",
                    message = "Ajoutez vos fournisseurs réels pour suivre les achats et dettes."
                )
            }
        }
        items(filtered, key = { "sup_${it.id}" }) { sup ->
            PremiumSurfaceCard(
                modifier = Modifier.animateItem(),
                onClick = { editing = sup }
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sup.company.ifBlank { sup.name }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text("Contact : ${sup.name} · ${sup.phone} · ${sup.city}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (sup.debtAmount > 0) {
                            StatusBadge("Dette ${Formatters.fcfaCompact(sup.debtAmount)}", MaterialTheme.colorScheme.error)
                        } else {
                            StatusBadge("À jour", ext.successGreen)
                        }
                    }
                    if (sup.suppliedProductsNote.isNotBlank()) {
                        Text("Produits fournis : ${sup.suppliedProductsNote}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "Total achats : ${Formatters.fcfa(sup.totalPurchased)}",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold),
                        color = ext.violetPrimary
                    )
                }
            }
        }
    }

    editing?.let { sup ->
        var name by remember(sup) { mutableStateOf(sup.name) }
        var company by remember(sup) { mutableStateOf(sup.company) }
        var phone by remember(sup) { mutableStateOf(sup.phone) }
        var city by remember(sup) { mutableStateOf(sup.city) }
        var productsNote by remember(sup) { mutableStateOf(sup.suppliedProductsNote) }
        var debtStr by remember(sup) { mutableStateOf(if (sup.debtAmount > 0) sup.debtAmount.toInt().toString() else "") }

        AlertDialog(
            onDismissRequest = { editing = null },
            shape = RoundedCornerShape(18.dp),
            title = { Text(if (sup.id == 0L) "Nouveau fournisseur" else "Modifier fournisseur", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(value = company, onValueChange = { company = it }, label = { Text("Entreprise") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom du contact") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Téléphone (+237)") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("Ville (Douala, Yaoundé...)") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(value = productsNote, onValueChange = { productsNote = it }, label = { Text("Produits fournis") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(
                        value = debtStr,
                        onValueChange = { debtStr = it },
                        label = { Text("Dette / Solde dû (FCFA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() || company.isNotBlank()) {
                            onSaveSupplier(
                                sup.copy(
                                    name = name.ifBlank { company },
                                    company = company,
                                    phone = phone,
                                    city = city,
                                    suppliedProductsNote = productsNote,
                                    debtAmount = debtStr.toDoubleOrNull() ?: sup.debtAmount
                                )
                            )
                            editing = null
                        }
                    },
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Enregistrer", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Annuler") } }
        )
    }
}

@Composable
fun CustomersScreen(
    customers: List<CustomerEntity>,
    onSaveCustomer: (CustomerEntity) -> Unit,
    onSettleCredit: (CustomerEntity, Double) -> Unit
) {
    val ext = LocalSmartStoreColors.current
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<CustomerEntity?>(null) }
    var settling by remember { mutableStateOf<CustomerEntity?>(null) }

    val filtered = remember(customers, search) {
        customers.filter {
            search.isBlank() ||
                it.name.contains(search, ignoreCase = true) ||
                it.phone.contains(search, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Clients & Crédits",
                subtitle = "${filtered.size} client(s) · Total créances : ${Formatters.fcfa(customers.sumOf { it.balance })}",
                action = {
                    Button(
                        onClick = { editing = CustomerEntity(name = "", phone = "") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("add_customer_button")
                    ) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Nouveau", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Rechercher client par nom ou téléphone...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (filtered.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "Aucun client enregistré",
                    message = "Ajoutez vos clients réguliers pour suivre leurs achats et crédits."
                )
            }
        }
        items(filtered, key = { "cust_${it.id}" }) { cust ->
            PremiumSurfaceCard(
                modifier = Modifier.animateItem(),
                onClick = { editing = cust }
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cust.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text("${cust.phone} · ${cust.address.ifBlank { "Cameroun" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (cust.balance > 0) {
                            StatusBadge("Crédit ${Formatters.fcfa(cust.balance)}", ext.goldOnSoft)
                        } else {
                            StatusBadge("Solde 0 FCFA", ext.successGreen)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Total acheté : ${Formatters.fcfa(cust.totalPurchased)}",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold),
                            color = ext.violetPrimary
                        )
                        if (cust.balance > 0) {
                            FilledTonalButton(onClick = { settling = cust }, shape = RoundedCornerShape(14.dp)) {
                                Text("Encaisser crédit", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { cust ->
        var name by remember(cust) { mutableStateOf(cust.name) }
        var phone by remember(cust) { mutableStateOf(cust.phone) }
        var email by remember(cust) { mutableStateOf(cust.email) }
        var address by remember(cust) { mutableStateOf(cust.address) }
        var notes by remember(cust) { mutableStateOf(cust.notes) }

        AlertDialog(
            onDismissRequest = { editing = null },
            shape = RoundedCornerShape(18.dp),
            title = { Text(if (cust.id == 0L) "Nouveau client" else "Fiche client", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom complet / Société") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Téléphone (+237)") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Adresse / Quartier") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onSaveCustomer(cust.copy(name = name, phone = phone, email = email, address = address, notes = notes))
                            editing = null
                        }
                    },
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Enregistrer", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Annuler") } }
        )
    }

    settling?.let { cust ->
        var payStr by remember(cust) { mutableStateOf(cust.balance.toInt().toString()) }
        AlertDialog(
            onDismissRequest = { settling = null },
            shape = RoundedCornerShape(18.dp),
            title = { Text("Encaisser crédit : ${cust.name}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Solde dû : ${Formatters.fcfa(cust.balance)}", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = payStr,
                        onValueChange = { payStr = it },
                        label = { Text("Montant reçu (FCFA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = payStr.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            onSettleCredit(cust, amt)
                            settling = null
                        }
                    },
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Valider encaissement", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { settling = null }) { Text("Annuler") } }
        )
    }
}

@Composable
fun CashRegisterScreen(
    openRegister: CashRegisterEntity?,
    registersHistory: List<CashRegisterEntity>,
    movements: List<CashMovementEntity>,
    currentTimeMs: Long,
    onOpenRegister: (Double) -> Unit,
    onAddMovement: (String, Double, String) -> Unit,
    onCloseRegister: (Double, String) -> Unit
) {
    val ext = LocalSmartStoreColors.current
    var showOpenDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showCloseDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Gestion de Caisse & Écarts",
                subtitle = "Temps réel · ${Formatters.dateTimeWithSeconds(currentTimeMs)}",
                action = {
                    if (openRegister == null) {
                        Button(
                            onClick = { showOpenDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("open_cash_register_button")
                        ) {
                            Icon(Icons.Rounded.LockOpen, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Ouvrir caisse", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { showMoveDialog = true }, shape = RoundedCornerShape(14.dp)) {
                                Text("Mouvement", fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = { showCloseDialog = true },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ext.midnightSecondary,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.testTag("close_cash_register_button")
                            ) {
                                Text("Fermer caisse", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            )
        }

        item {
            if (openRegister == null) {
                EmptyStateCard(
                    title = "Caisse actuellement fermée",
                    message = "Ouvrez la caisse avec le fonds initial avant d'encaisser les opérations en espèces.",
                    icon = Icons.Rounded.PointOfSale
                )
            } else {
                PremiumSurfaceCard {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Session active · ${openRegister.openedByName}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Text("Ouverte à : ${Formatters.dateTimeWithSeconds(openRegister.openedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusBadge("OUVERTE", ext.successGreen)
                        }
                        HorizontalDivider(color = ext.cardBorder)
                        CashSummaryRow("Fonds initial", openRegister.openingBalance)
                        CashSummaryRow("Ventes Espèces", openRegister.totalSalesCash)
                        CashSummaryRow("Ventes Mobile / Carte", openRegister.totalSalesMobile)
                        CashSummaryRow("Entrées caisse (+)", openRegister.totalCashIn)
                        CashSummaryRow("Sorties / Retraits (-)", openRegister.totalCashOut)
                        CashSummaryRow("Dépenses caisse (-)", openRegister.totalExpenses)
                        HorizontalDivider(color = ext.cardBorder)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Solde théorique en caisse :", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                Formatters.fcfa(openRegister.theoreticalBalance),
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold),
                                color = ext.violetPrimary
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Mouvements de caisse", subtitle = "${movements.size} opération(s)")
        }

        items(movements.take(20), key = { "cash_mv_${it.id}" }) { mv ->
            val col = if (mv.type in setOf("ENTREE", "VENTE")) ext.successGreen else ext.midnightSecondary
            PremiumSurfaceCard(modifier = Modifier.animateItem()) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${mv.type} · ${mv.reason}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text("${mv.userName} · ${Formatters.dateTimeWithSeconds(mv.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        Formatters.fcfa(mv.amount),
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold),
                        color = col
                    )
                }
            }
        }

        item {
            SectionHeader(title = "Historique des clôtures", subtitle = "Comparaison théorique vs réel compté")
        }

        items(registersHistory.filter { it.status == "FERMEE" }, key = { "cash_reg_${it.id}" }) { reg ->
            val discLabel = when {
                reg.discrepancy > 0.5 -> "Excédent +${Formatters.fcfa(reg.discrepancy)}"
                reg.discrepancy < -0.5 -> "Déficit ${Formatters.fcfa(reg.discrepancy)}"
                else -> "Aucune différence"
            }
            val discColor = when {
                kotlin.math.abs(reg.discrepancy) > 0.5 -> MaterialTheme.colorScheme.error
                else -> ext.successGreen
            }
            PremiumSurfaceCard(modifier = Modifier.animateItem()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Fermée le ${Formatters.dateTime(reg.closedAt)} par ${reg.closedByName}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                        StatusBadge(discLabel, discColor)
                    }
                    Text(
                        "Théorique : ${Formatters.fcfa(reg.theoreticalBalance)} · Compté réel : ${Formatters.fcfa(reg.actualCountedBalance)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (reg.discrepancyReason.isNotBlank()) {
                        Text("Justification : ${reg.discrepancyReason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showOpenDialog) {
        var amountStr by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showOpenDialog = false },
            shape = RoundedCornerShape(18.dp),
            title = { Text("Ouverture de caisse", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    placeholder = { Text("0") },
                    label = { Text("Fonds initial en caisse (FCFA)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountStr.toDoubleOrNull() ?: 0.0
                        onOpenRegister(amt.coerceAtLeast(0.0))
                        showOpenDialog = false
                    },
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Ouvrir", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { showOpenDialog = false }) { Text("Annuler") } }
        )
    }

    if (showMoveDialog) {
        var type by remember { mutableStateOf("ENTREE") }
        var amountStr by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            shape = RoundedCornerShape(18.dp),
            title = { Text("Nouveau mouvement de caisse", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ENTREE", "SORTIE", "RETRAIT", "DEPENSE").forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = t }, shape = RoundedCornerShape(14.dp), label = { Text(t, fontWeight = FontWeight.SemiBold) })
                        }
                    }
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Montant (FCFA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Motif") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountStr.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            onAddMovement(type, amt, reason.ifBlank { type })
                            showMoveDialog = false
                        }
                    },
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Valider", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { showMoveDialog = false }) { Text("Annuler") } }
        )
    }

    if (showCloseDialog && openRegister != null) {
        var countedStr by remember { mutableStateOf(openRegister.theoreticalBalance.toInt().toString()) }
        var reason by remember { mutableStateOf("") }
        val counted = countedStr.toDoubleOrNull() ?: 0.0
        val diff = counted - openRegister.theoreticalBalance
        AlertDialog(
            onDismissRequest = { showCloseDialog = false },
            shape = RoundedCornerShape(18.dp),
            title = { Text("Fermeture de caisse", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Solde théorique attendu : ${Formatters.fcfa(openRegister.theoreticalBalance)}", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = countedStr,
                        onValueChange = { countedStr = it },
                        label = { Text("Montant réel compté (FCFA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    val diffMsg = when {
                        diff > 0.5 -> "Écart détecté : Excédent de ${Formatters.fcfa(diff)}"
                        diff < -0.5 -> "Écart détecté : Déficit de ${Formatters.fcfa(-diff)}"
                        else -> "Aucune différence"
                    }
                    Text(
                        diffMsg,
                        color = if (kotlin.math.abs(diff) > 0.5) MaterialTheme.colorScheme.error else ext.successGreen,
                        fontWeight = FontWeight.Bold
                    )
                    if (kotlin.math.abs(diff) > 0.5) {
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text("Motif de l'écart (obligatoire)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCloseRegister(counted, reason)
                        showCloseDialog = false
                    },
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Clôturer", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { showCloseDialog = false }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun CashSummaryRow(label: String, amount: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Formatters.fcfa(amount), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold))
    }
}
