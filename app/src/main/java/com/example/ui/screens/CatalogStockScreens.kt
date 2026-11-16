package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.*
import com.example.ui.components.*
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalSmartStoreColors
import com.example.util.DocumentExporter
import com.example.util.Formatters

@Composable
fun ProductsScreen(
    products: List<ProductEntity>,
    categories: List<CategoryEntity>,
    suppliers: List<SupplierEntity>,
    settings: StoreSettingsEntity,
    onSaveProduct: (ProductEntity, Boolean) -> Unit,
    onDeleteProduct: (ProductEntity) -> Unit,
    onAddCategory: (String, String, String) -> Unit,
    onImportCsvUri: (Uri) -> Unit
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("Nom") }
    var filterCat by remember { mutableStateOf("Toutes") }
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var isNewProduct by remember { mutableStateOf(false) }
    var barcodePreview by remember { mutableStateOf<ProductEntity?>(null) }
    var showCatDialog by remember { mutableStateOf(false) }

    val csvImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportCsvUri(uri)
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    DocumentExporter.writeCsvBytes(
                        out,
                        listOf("Nom", "SKU", "CodeBarres", "Catégorie", "PrixAchat", "PrixVente", "Stock", "SeuilMin"),
                        products.map {
                            listOf(
                                it.name, it.sku, it.barcode, it.categoryName,
                                it.purchasePrice.toInt().toString(), it.sellingPrice.toInt().toString(),
                                it.stockQuantity.toString(), it.minThreshold.toString()
                            )
                        }
                    )
                }
            }
        }
    }

    val filtered = remember(products, search, filterCat, sortBy) {
        val base = products.filter { p ->
            val okCat = filterCat == "Toutes" || p.categoryName == filterCat
            val okQ = search.isBlank() ||
                p.name.contains(search, ignoreCase = true) ||
                p.sku.contains(search, ignoreCase = true) ||
                p.barcode.contains(search, ignoreCase = true)
            okCat && okQ
        }
        when (sortBy) {
            "Prix" -> base.sortedByDescending { it.sellingPrice }
            "Stock" -> base.sortedBy { it.stockQuantity }
            "Marge" -> base.sortedByDescending { Formatters.marginPercent(it.sellingPrice, it.purchasePrice) }
            else -> base.sortedBy { it.name }
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
                title = "Produits, Prix & Codes-barres",
                subtitle = "${filtered.size} produit(s) · Calcul automatique des marges",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showCatDialog = true }, shape = RoundedCornerShape(14.dp)) {
                            Text("Catégories", fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                isNewProduct = true
                                val defaultCat = categories.firstOrNull()
                                val defaultCatName = defaultCat?.name ?: "Général"
                                val baseInv = "${settings.invoicePrefix.ifBlank { "FAC" }}-${settings.nextInvoiceNumber}"
                                editingProduct = ProductEntity(
                                    name = "",
                                    reference = Formatters.autoProductReference(baseInv, defaultCatName),
                                    sku = "${Formatters.categoryCodePrefix(defaultCatName)}-${products.size + 101}",
                                    barcode = "617900${100000 + products.size}",
                                    categoryId = defaultCat?.id ?: 0L,
                                    categoryName = defaultCatName,
                                    purchasePrice = 0.0,
                                    sellingPrice = 0.0,
                                    stockQuantity = 0,
                                    minThreshold = 10
                                )
                            },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Produit", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Importer CSV", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = { csvExportLauncher.launch("catalogue_smartstore.csv") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Exporter CSV", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Rechercher produit, SKU, code-barres...") },
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
                FilterChip(selected = filterCat == "Toutes", onClick = { filterCat = "Toutes" }, shape = RoundedCornerShape(14.dp), label = { Text("Toutes", fontWeight = FontWeight.SemiBold) })
                categories.forEach { c ->
                    FilterChip(selected = filterCat == c.name, onClick = { filterCat = c.name }, shape = RoundedCornerShape(14.dp), label = { Text(c.name, fontWeight = FontWeight.SemiBold) })
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Trier :", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf("Nom", "Prix", "Stock", "Marge").forEach { s ->
                    FilterChip(selected = sortBy == s, onClick = { sortBy = s }, shape = RoundedCornerShape(14.dp), label = { Text(s, fontWeight = FontWeight.SemiBold) })
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "Aucun produit enregistré",
                    message = "Appuyez sur « + Produit » ou importez un CSV pour créer votre catalogue réel."
                )
            }
        }
        items(filtered, key = { "prod_${it.id}" }) { prod ->
            val marginFcfa = Formatters.marginAmount(prod.sellingPrice, prod.purchasePrice)
            val marginPct = Formatters.marginPercent(prod.sellingPrice, prod.purchasePrice)
            val stockColor = when {
                prod.stockQuantity <= 0 -> MaterialTheme.colorScheme.error
                prod.stockQuantity <= prod.minThreshold -> ext.goldOnSoft
                else -> ext.successGreen
            }

            PremiumSurfaceCard(modifier = Modifier.animateItem()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(prod.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                "${prod.sku} · ${prod.categoryName} · ${prod.supplierName.ifBlank { "Fournisseur standard" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Code Facture / Cat. : ${prod.reference.ifBlank { Formatters.autoProductReference("${settings.invoicePrefix.ifBlank { "FAC" }}-${settings.nextInvoiceNumber}", prod.categoryName) }}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = ext.violetPrimary
                            )
                        }
                        StatusBadge("Stock ${prod.stockQuantity}/${prod.minThreshold}", stockColor)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Achat : ${Formatters.fcfa(prod.purchasePrice)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Vente : ${Formatters.fcfa(prod.sellingPrice)}",
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold),
                            color = ext.violetPrimary
                        )
                        Text(
                            "Marge : ${Formatters.fcfa(marginFcfa)} (${Formatters.percent(marginPct)})",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = ext.successGreen
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { barcodePreview = prod }, shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Code-barres", fontWeight = FontWeight.SemiBold)
                        }
                        FilledTonalButton(
                            onClick = {
                                isNewProduct = false
                                editingProduct = prod
                            },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Modifier", fontWeight = FontWeight.SemiBold)
                        }
                        IconButton(onClick = { onDeleteProduct(prod) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    editingProduct?.let { prod ->
        ProductEditorDialog(
            initial = prod,
            isNew = isNewProduct,
            categories = categories,
            suppliers = suppliers,
            settings = settings,
            onDismiss = { editingProduct = null },
            onSave = { updated ->
                onSaveProduct(updated, isNewProduct)
                editingProduct = null
            }
        )
    }

    barcodePreview?.let { prod ->
        AlertDialog(
            onDismissRequest = { barcodePreview = null },
            shape = RoundedCornerShape(18.dp),
            title = { Text(prod.name, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SKU : ${prod.sku} · Réf : ${prod.reference}", style = MaterialTheme.typography.bodySmall)
                    VisualBarcodeView(code = prod.barcode.ifBlank { prod.sku }, modifier = Modifier.fillMaxWidth())
                    Text("Prix de vente : ${Formatters.fcfa(prod.sellingPrice)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ext.violetPrimary)
                }
            },
            confirmButton = {
                TextButton(onClick = { barcodePreview = null }) { Text("Fermer") }
            }
        )
    }

    if (showCatDialog) {
        var catName by remember { mutableStateOf("") }
        var subCat by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCatDialog = false },
            shape = RoundedCornerShape(18.dp),
            title = { Text("Gérer les catégories", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach {
                        Text("• ${it.name} (${it.subCategory})", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider(color = ext.cardBorder)
                    OutlinedTextField(value = catName, onValueChange = { catName = it }, label = { Text("Nouvelle catégorie") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(value = subCat, onValueChange = { subCat = it }, label = { Text("Sous-catégorie") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (catName.isNotBlank()) {
                            onAddCategory(catName.trim(), subCat.trim(), "#5B3FD3")
                            showCatDialog = false
                        }
                    },
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Ajouter", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { showCatDialog = false }) { Text("Fermer") } }
        )
    }
}

@Composable
private fun ProductEditorDialog(
    initial: ProductEntity,
    isNew: Boolean,
    categories: List<CategoryEntity>,
    suppliers: List<SupplierEntity>,
    settings: StoreSettingsEntity,
    onDismiss: () -> Unit,
    onSave: (ProductEntity) -> Unit
) {
    val ext = LocalSmartStoreColors.current
    val baseInv = "${settings.invoicePrefix.ifBlank { "FAC" }}-${settings.nextInvoiceNumber}"
    var name by remember { mutableStateOf(initial.name) }
    var catName by remember { mutableStateOf(initial.categoryName) }
    var reference by remember {
        mutableStateOf(
            initial.reference.ifBlank {
                Formatters.autoProductReference(baseInv, initial.categoryName)
            }
        )
    }
    var sku by remember { mutableStateOf(initial.sku) }
    var barcode by remember { mutableStateOf(initial.barcode) }
    var buyStr by remember { mutableStateOf(if (initial.purchasePrice > 0) initial.purchasePrice.toInt().toString() else "") }
    var sellStr by remember { mutableStateOf(if (initial.sellingPrice > 0) initial.sellingPrice.toInt().toString() else "") }
    var stockStr by remember { mutableStateOf(if (!isNew || initial.stockQuantity > 0) initial.stockQuantity.toString() else "") }
    var minStr by remember { mutableStateOf(if (!isNew) initial.minThreshold.toString() else "") }
    var unit by remember { mutableStateOf(initial.unit) }

    val buyVal = buyStr.toDoubleOrNull() ?: 0.0
    val sellVal = sellStr.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        title = { Text(if (isNew) "Nouveau produit" else "Modifier ${initial.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom du produit") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                Text("Catégorie (génère automatiquement le code facture) :", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                if (categories.isNotEmpty()) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { c ->
                            FilterChip(
                                selected = catName == c.name,
                                onClick = {
                                    catName = c.name
                                    reference = Formatters.autoProductReference(baseInv, c.name)
                                },
                                shape = RoundedCornerShape(14.dp),
                                label = { Text(c.name, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = catName,
                    onValueChange = {
                        catName = it
                        reference = Formatters.autoProductReference(baseInv, it)
                    },
                    label = { Text("Nom de la catégorie") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = { Text("N° Facture + Début Catégorie (Auto)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(value = sku, onValueChange = { sku = it }, label = { Text("SKU") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("Code-barres") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = buyStr, onValueChange = { buyStr = it },
                        label = { Text("Prix achat") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(
                        value = sellStr, onValueChange = { sellStr = it },
                        label = { Text("Prix vente") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp)
                    )
                }
                Text(
                    "Marge automatique : ${Formatters.fcfa(sellVal - buyVal)} (${Formatters.percent(Formatters.marginPercent(sellVal, buyVal))})",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = ext.successGreen
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stockStr, onValueChange = { stockStr = it },
                        placeholder = { Text("0") },
                        label = { Text("Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(
                        value = minStr, onValueChange = { minStr = it },
                        placeholder = { Text("${settings.defaultMinStock}") },
                        label = { Text("Seuil min") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp)
                    )
                }
                OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unité (Bouteille, Sac, Pièce...)") }, singleLine = true, shape = RoundedCornerShape(14.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            initial.copy(
                                name = name.trim(),
                                reference = reference.trim(),
                                sku = sku.trim(),
                                barcode = barcode.trim(),
                                categoryName = catName,
                                purchasePrice = buyVal,
                                sellingPrice = sellVal,
                                stockQuantity = stockStr.toIntOrNull() ?: initial.stockQuantity,
                                minThreshold = minStr.toIntOrNull() ?: initial.minThreshold,
                                unit = unit.ifBlank { "Unité" }
                            )
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp)
            ) { Text("Enregistrer", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun StocksScreen(
    products: List<ProductEntity>,
    movements: List<StockMovementEntity>,
    onAdjustStock: (ProductEntity, String, Int, String) -> Unit
) {
    val ext = LocalSmartStoreColors.current
    var showAdjustDialog by remember { mutableStateOf<ProductEntity?>(null) }
    val totalValue = products.sumOf { it.stockQuantity * it.purchasePrice }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Gestion automatisée des stocks",
                subtitle = "Valeur totale du stock : ${Formatters.fcfa(totalValue)}"
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiMetricCard(
                    title = "Alertes Rupture",
                    value = "${products.count { it.stockQuantity == 0 }}",
                    subtitle = "Produits épuisés",
                    icon = Icons.Rounded.Warning,
                    accentColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = "Stock faible",
                    value = "${products.count { it.stockQuantity in 1..it.minThreshold }}",
                    subtitle = "Sous le seuil minimum",
                    icon = Icons.Rounded.NotificationsActive,
                    accentColor = ext.goldOnSoft,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item { Text("État du stock par article (appuyez pour entrée / sortie / perte / transfert)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) }
        if (products.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "Aucun produit en stock",
                    message = "Ajoutez vos produits dans « Produits & Prix » pour gérer vos entrées, sorties et alertes."
                )
            }
        }
        items(products, key = { "stk_prod_${it.id}" }) { prod ->
            val col = when {
                prod.stockQuantity == 0 -> MaterialTheme.colorScheme.error
                prod.stockQuantity <= prod.minThreshold -> ext.goldOnSoft
                else -> ext.successGreen
            }
            PremiumSurfaceCard(
                modifier = Modifier.animateItem(),
                onClick = { showAdjustDialog = prod }
            ) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(prod.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text("Valeur : ${Formatters.fcfa(prod.stockQuantity * prod.purchasePrice)} · Seuil min : ${prod.minThreshold}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusBadge("${prod.stockQuantity} ${prod.unit}", col)
                }
            }
        }
        item { SectionHeader(title = "Historique des mouvements", subtitle = "${movements.size} opération(s)") }
        items(movements.take(25), key = { "stk_mv_${it.id}" }) { mv ->
            PremiumSurfaceCard(modifier = Modifier.animateItem()) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${mv.productName} (${mv.type})", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text("${mv.reason} · ${mv.userName} · ${Formatters.dateTime(mv.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val deltaSign = if (mv.quantityDelta >= 0) "+${mv.quantityDelta}" else "${mv.quantityDelta}"
                    StatusBadge("$deltaSign (${mv.previousStock}→${mv.newStock})", if (mv.quantityDelta >= 0) ext.successGreen else ext.midnightSecondary)
                }
            }
        }
    }

    showAdjustDialog?.let { prod ->
        var type by remember { mutableStateOf("ENTREE") }
        var qtyStr by remember { mutableStateOf("1") }
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdjustDialog = null },
            shape = RoundedCornerShape(18.dp),
            title = { Text("Mouvement : ${prod.name}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ENTREE", "SORTIE", "PERTE", "ENDOMMAGE", "RETOUR_CLIENT", "TRANSFERT", "AJUSTEMENT").forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = t }, shape = RoundedCornerShape(14.dp), label = { Text(t, fontWeight = FontWeight.SemiBold) })
                        }
                    }
                    OutlinedTextField(
                        value = qtyStr, onValueChange = { qtyStr = it },
                        label = { Text("Quantité") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(
                        value = reason, onValueChange = { reason = it },
                        label = { Text("Motif obligatoire (perte, transfert, avarie...)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val q = qtyStr.toIntOrNull() ?: 0
                        if (q > 0) {
                            onAdjustStock(prod, type, q, reason.ifBlank { type })
                            showAdjustDialog = null
                        }
                    },
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Valider", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { showAdjustDialog = null }) { Text("Annuler") } }
        )
    }
}

@Composable
fun InventoriesScreen(
    inventories: List<InventoryEntity>,
    inventoryItems: List<InventoryItemEntity>,
    canValidate: Boolean,
    onScheduleInventory: (String, String, Long) -> Unit,
    onUpdateCount: (Long, InventoryItemEntity, Int, Int, String) -> Unit,
    onSubmitInventory: (InventoryEntity) -> Unit,
    onValidateInventory: (InventoryEntity) -> Unit
) {
    val ext = LocalSmartStoreColors.current
    var selectedInventoryId by remember { mutableStateOf(inventories.firstOrNull()?.id) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var countingItem by remember { mutableStateOf<InventoryItemEntity?>(null) }

    LaunchedEffect(inventories) {
        if (selectedInventoryId == null && inventories.isNotEmpty()) {
            selectedInventoryId = inventories.first().id
        }
    }

    val activeInv = inventories.find { it.id == selectedInventoryId } ?: inventories.firstOrNull()
    val items = activeInv?.let { inv -> inventoryItems.filter { it.inventoryId == inv.id } } ?: emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Inventaires périodiques",
                subtitle = "Préparation auto · Comptage physique confirmé · Validation Admin",
                action = {
                    Button(onClick = { showScheduleDialog = true }, shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Programmer", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }

        item {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                inventories.forEach { inv ->
                    FilterChip(
                        selected = inv.id == activeInv?.id,
                        onClick = { selectedInventoryId = inv.id },
                        shape = RoundedCornerShape(14.dp),
                        label = { Text("${inv.frequency} · ${inv.status}", fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        }

        if (activeInv == null) {
            item { EmptyStateCard("Aucun inventaire", "Programmez un inventaire quotidien, hebdomadaire, mensuel ou personnalisé.") }
        } else {
            item {
                PremiumSurfaceCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(activeInv.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                            StatusBadge(activeInv.status, if (activeInv.status == "VALIDE") ext.successGreen else ext.violetPrimary)
                        }
                        Text("Théorique : ${Formatters.fcfa(activeInv.totalTheoreticalValue)} · Réel : ${Formatters.fcfa(activeInv.totalActualValue)}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Écart financier : ${Formatters.fcfa(activeInv.totalDiscrepancyValue)} · Manquants : ${activeInv.missingCount} · Excédents : ${activeInv.surplusCount} · Endommagés : ${activeInv.damagedCount}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (activeInv.totalDiscrepancyValue < 0) MaterialTheme.colorScheme.error else ext.successGreen
                        )
                        if (activeInv.status != "VALIDE") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onSubmitInventory(activeInv) }, shape = RoundedCornerShape(14.dp)) {
                                    Text("Soumettre rapport", fontWeight = FontWeight.SemiBold)
                                }
                                if (canValidate) {
                                    Button(onClick = { onValidateInventory(activeInv) }, shape = RoundedCornerShape(14.dp)) {
                                        Icon(Icons.Rounded.Verified, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Valider & Corriger Stock", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            items(items, key = { "inv_item_${it.id}" }) { item ->
                val discColor = when {
                    item.discrepancyQty < 0 -> MaterialTheme.colorScheme.error
                    item.discrepancyQty > 0 -> ext.goldOnSoft
                    item.isCounted -> ext.successGreen
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                PremiumSurfaceCard(
                    modifier = Modifier.animateItem(),
                    onClick = if (activeInv.status != "VALIDE") ({ countingItem = item }) else null
                ) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.productName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text(
                                "Théorique : ${item.theoreticalQty} · Compté : ${item.actualQty} · Endommagés : ${item.damagedQty}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusBadge(
                            if (item.isCounted) "Écart ${item.discrepancyQty}" else "À compter",
                            discColor
                        )
                    }
                }
            }
        }
    }

    if (showScheduleDialog) {
        var title by remember { mutableStateOf("") }
        var freq by remember { mutableStateOf("MENSUEL") }
        AlertDialog(
            onDismissRequest = { showScheduleDialog = false },
            shape = RoundedCornerShape(18.dp),
            title = { Text("Programmer un inventaire", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Intitulé de l'inventaire") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("QUOTIDIEN", "HEBDOMADAIRE", "MENSUEL", "TRIMESTRIEL", "ANNUEL", "PERSONNALISE").forEach { f ->
                            FilterChip(selected = freq == f, onClick = { freq = f }, shape = RoundedCornerShape(14.dp), label = { Text(f, fontWeight = FontWeight.SemiBold) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onScheduleInventory(title.ifBlank { "Inventaire $freq" }, freq, System.currentTimeMillis())
                        showScheduleDialog = false
                    },
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Créer & Préparer", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { showScheduleDialog = false }) { Text("Annuler") } }
        )
    }

    countingItem?.let { item ->
        var actualStr by remember { mutableStateOf(item.actualQty.toString()) }
        var damagedStr by remember { mutableStateOf(item.damagedQty.toString()) }
        var note by remember { mutableStateOf(item.note) }
        AlertDialog(
            onDismissRequest = { countingItem = null },
            shape = RoundedCornerShape(18.dp),
            title = { Text("Comptage : ${item.productName}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Stock théorique système : ${item.theoreticalQty}", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = actualStr, onValueChange = { actualStr = it },
                        label = { Text("Quantité physique réelle comptée") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(
                        value = damagedStr, onValueChange = { damagedStr = it },
                        label = { Text("Produits endommagés") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Observation") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateCount(
                            item.inventoryId,
                            item,
                            actualStr.toIntOrNull() ?: item.theoreticalQty,
                            damagedStr.toIntOrNull() ?: 0,
                            note
                        )
                        countingItem = null
                    },
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Confirmer comptage", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { countingItem = null }) { Text("Annuler") } }
        )
    }
}
