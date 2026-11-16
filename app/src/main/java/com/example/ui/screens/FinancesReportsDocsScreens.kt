package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.*
import com.example.ui.components.*
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalSmartStoreColors
import com.example.util.DocumentExporter
import com.example.util.Formatters
import java.io.ByteArrayOutputStream

@Composable
fun FinancesScreen(
    sales: List<SaleEntity>,
    expenses: List<ExpenseEntity>,
    canViewProfits: Boolean,
    currentTimeMs: Long,
    onAddExpense: (String, Double, String, String) -> Unit
) {
    val ext = LocalSmartStoreColors.current
    var period by remember { mutableStateOf("Ce mois") }
    var showExpenseDialog by remember { mutableStateOf(false) }

    val cutoffMs = remember(period, currentTimeMs) {
        val dayMs = 86_400_000L
        when (period) {
            "Aujourd'hui" -> currentTimeMs - dayMs
            "Cette semaine" -> currentTimeMs - 7 * dayMs
            "Ce mois" -> currentTimeMs - 30 * dayMs
            "Cette année" -> currentTimeMs - 365 * dayMs
            else -> 0L
        }
    }

    val periodSales = remember(sales, cutoffMs) {
        sales.filter { it.status == "VALIDEE" && it.createdAt >= cutoffMs }
    }
    val periodExpenses = remember(expenses, cutoffMs) {
        expenses.filter { it.expenseDate >= cutoffMs }
    }

    val recettes = periodSales.sumOf { it.totalAmount }
    val grossProfit = periodSales.sumOf { it.profit }
    val totalExp = periodExpenses.sumOf { it.amount }
    val netResult = grossProfit - totalExp
    val marginPct = if (recettes > 0) (grossProfit / recettes) * 100.0 else 0.0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Finances, Dépenses & Bénéfices",
                subtitle = "Suivi temps réel en FCFA (XAF)",
                action = {
                    Button(
                        onClick = { showExpenseDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("add_expense_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Dépense", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Aujourd'hui", "Cette semaine", "Ce mois", "Cette année", "Tout").forEach { p ->
                    FilterChip(selected = period == p, onClick = { period = p }, shape = RoundedCornerShape(14.dp), label = { Text(p, fontWeight = FontWeight.SemiBold) })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiMetricCard(
                    title = "Recettes ($period)",
                    value = Formatters.fcfa(recettes),
                    subtitle = "${periodSales.size} ventes validées",
                    icon = Icons.Rounded.AccountBalanceWallet,
                    accentColor = ext.violetPrimary,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = "Dépenses",
                    value = Formatters.fcfa(totalExp),
                    subtitle = "${periodExpenses.size} charge(s)",
                    icon = Icons.Rounded.Receipt,
                    accentColor = ext.midnightSecondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (canViewProfits) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiMetricCard(
                        title = "Bénéfice brut",
                        value = Formatters.fcfa(grossProfit),
                        subtitle = "Marge : ${Formatters.percent(marginPct)}",
                        icon = Icons.Rounded.TrendingUp,
                        accentColor = ext.successGreen,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        title = "Résultat net",
                        value = Formatters.fcfa(netResult),
                        subtitle = if (netResult >= 0) "Excédentaire" else "Déficitaire",
                        icon = Icons.Rounded.Insights,
                        accentColor = if (netResult >= 0) ext.successGreen else MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        item {
            val byCategory = periodExpenses.groupBy { it.category }
                .mapValues { (_, list) -> list.sumOf { it.amount } }
                .entries.sortedByDescending { it.value }
                .map { it.key to it.value }
            HorizontalBarChart(
                title = "Répartition des dépenses par catégorie",
                items = byCategory,
                barColor = ext.midnightSecondary
            )
        }
        item { SectionHeader(title = "Journal des dépenses", subtitle = "${periodExpenses.size} écriture(s)") }
        items(periodExpenses, key = { "exp_${it.id}" }) { exp ->
            PremiumSurfaceCard(modifier = Modifier.animateItem()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${exp.category} · ${exp.description}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text("${exp.userName} · ${Formatters.dateTime(exp.expenseDate)} ${if (exp.receiptRef.isNotBlank()) "· Réf ${exp.receiptRef}" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        Formatters.fcfa(exp.amount),
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showExpenseDialog) {
        var category by remember { mutableStateOf("Loyer") }
        var amountStr by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var ref by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showExpenseDialog = false },
            title = { Text("Nouvelle dépense") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Loyer", "Électricité", "Transport", "Salaires", "Internet", "Entretien", "Fournitures", "Autres").forEach { cat ->
                            FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
                        }
                    }
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Montant (FCFA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, singleLine = true)
                    OutlinedTextField(value = ref, onValueChange = { ref = it }, label = { Text("Référence justificatif") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    if (amt > 0) {
                        onAddExpense(category, amt, desc.ifBlank { category }, ref)
                        showExpenseDialog = false
                    }
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { showExpenseDialog = false }) { Text("Annuler") } }
        )
    }
}

@Composable
fun ReportsScreen(
    sales: List<SaleEntity>,
    products: List<ProductEntity>,
    inventories: List<InventoryEntity>,
    purchases: List<PurchaseEntity>,
    expenses: List<ExpenseEntity>,
    customers: List<CustomerEntity>,
    suppliers: List<SupplierEntity>,
    cashRegisters: List<CashRegisterEntity>,
    auditLogs: List<AuditLogEntity>,
    reportsHistory: List<ReportRecordEntity>,
    onRecordReport: (String, String, String, String) -> Unit,
    onRegisterExportedDoc: (String, String, String, String, ByteArray, String) -> Unit
) {
    val context = LocalContext.current
    val reportCatalog = remember {
        listOf(
            "VENTES" to "Rapport des ventes globales",
            "VENTES_PRODUIT" to "Ventes par produit & marge",
            "VENTES_CAISSIER" to "Ventes par caissier",
            "PAIEMENTS" to "Ventilation modes de paiement",
            "BENEFICES" to "Rapport bénéfices & marges nettes",
            "STOCK" to "État et valorisation du stock",
            "RUPTURES" to "Produits faibles et en rupture",
            "INVENTAIRES" to "Synthèse des inventaires périodiques",
            "ACHATS" to "Commandes & réceptions achats",
            "FOURNISSEURS" to "Situation et dettes fournisseurs",
            "CLIENTS" to "Activité et crédits clients",
            "DEPENSES" to "Journal détaillé des dépenses",
            "CAISSE" to "Clôtures de caisse et écarts",
            "ACTIVITE" to "Audit complet des utilisateurs"
        )
    }

    var selectedType by remember { mutableStateOf("VENTES") }
    var selectedTitle by remember { mutableStateOf("Rapport des ventes globales") }

    val reportLines: List<String> = remember(
        selectedType, sales, products, inventories, purchases, expenses, customers, suppliers, cashRegisters, auditLogs
    ) {
        when (selectedType) {
            "VENTES" -> sales.map { "${it.invoiceNumber} | ${Formatters.dateTime(it.createdAt)} | ${it.customerName} | ${Formatters.fcfa(it.totalAmount)} | ${it.status}" }
            "VENTES_PRODUIT" -> products.map { "${it.sku} | ${it.name} | Achat ${Formatters.fcfa(it.purchasePrice)} | Vente ${Formatters.fcfa(it.sellingPrice)} | Marge ${Formatters.percent(Formatters.marginPercent(it.sellingPrice, it.purchasePrice))}" }
            "VENTES_CAISSIER" -> sales.filter { it.status == "VALIDEE" }.groupBy { it.userName }.map { (name, list) -> "$name : ${list.size} vente(s) | CA ${Formatters.fcfa(list.sumOf { it.totalAmount })}" }
            "PAIEMENTS" -> sales.filter { it.status == "VALIDEE" }.groupBy { it.paymentMethod }.map { (m, list) -> "$m : ${list.size} tx | Total ${Formatters.fcfa(list.sumOf { it.totalAmount })}" }
            "BENEFICES" -> listOf(
                "CA Validé : ${Formatters.fcfa(sales.filter { it.status == "VALIDEE" }.sumOf { it.totalAmount })}",
                "Bénéfice brut : ${Formatters.fcfa(sales.filter { it.status == "VALIDEE" }.sumOf { it.profit })}",
                "Total dépenses : ${Formatters.fcfa(expenses.sumOf { it.amount })}",
                "Résultat net : ${Formatters.fcfa(sales.filter { it.status == "VALIDEE" }.sumOf { it.profit } - expenses.sumOf { it.amount })}"
            )
            "STOCK" -> products.map { "${it.sku} | ${it.name} | Qté ${it.stockQuantity} ${it.unit} | Valeur ${Formatters.fcfa(it.stockQuantity * it.purchasePrice)}" }
            "RUPTURES" -> products.filter { it.stockQuantity <= it.minThreshold }.map { "${it.name} (${it.sku}) : Stock ${it.stockQuantity} / Seuil ${it.minThreshold}" }
            "INVENTAIRES" -> inventories.map { "${it.title} (${it.frequency}) | ${it.status} | Écart ${Formatters.fcfa(it.totalDiscrepancyValue)}" }
            "ACHATS" -> purchases.map { "${it.orderNumber} | ${it.supplierName} | ${it.status} | ${Formatters.fcfa(it.totalAmount)}" }
            "FOURNISSEURS" -> suppliers.map { "${it.company.ifBlank { it.name }} | Achats ${Formatters.fcfa(it.totalPurchased)} | Dette ${Formatters.fcfa(it.debtAmount)}" }
            "CLIENTS" -> customers.map { "${it.name} (${it.phone}) | Achats ${Formatters.fcfa(it.totalPurchased)} | Crédit ${Formatters.fcfa(it.balance)}" }
            "DEPENSES" -> expenses.map { "${Formatters.date(it.expenseDate)} | ${expLine(it)}" }
            "CAISSE" -> cashRegisters.map { "Session #${it.id} (${it.status}) | Théorique ${Formatters.fcfa(it.theoreticalBalance)} | Écart ${Formatters.fcfa(it.discrepancy)}" }
            else -> auditLogs.take(40).map { "${Formatters.dateTime(it.createdAt)} | ${it.userName} | ${it.module} : ${it.action}" }
        }
    }

    val pdfExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val baos = ByteArrayOutputStream()
                DocumentExporter.writePdfReport(baos, selectedTitle, "Généré le ${Formatters.dateTimeWithSeconds(System.currentTimeMillis())}", reportLines)
                val bytes = baos.toByteArray()
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                onRegisterExportedDoc("rapport_${selectedType.lowercase()}.pdf", "application/pdf", "pdf", "RAPPORT", bytes, selectedTitle)
                onRecordReport(selectedType, selectedTitle, "Temps réel", "${reportLines.size} lignes exportées PDF")
            }
        }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val baos = ByteArrayOutputStream()
                DocumentExporter.writeCsvBytes(
                    baos,
                    listOf("Type", "Détail"),
                    reportLines.map { listOf(selectedType, it) }
                )
                val bytes = baos.toByteArray()
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                onRegisterExportedDoc("rapport_${selectedType.lowercase()}.csv", "text/csv", "csv", "RAPPORT", bytes, selectedTitle)
                onRecordReport(selectedType, selectedTitle, "Temps réel", "${reportLines.size} lignes exportées Excel CSV")
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Rapports & Statistiques (14 modules)",
                subtitle = "Aperçu en direct · PDF, Excel (CSV) et Impression"
            )
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                reportCatalog.forEach { (code, title) ->
                    FilterChip(
                        selected = selectedType == code,
                        onClick = { selectedType = code; selectedTitle = title },
                        shape = RoundedCornerShape(14.dp),
                        label = { Text(code, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        }
        item {
            PremiumSurfaceCard {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(selectedTitle, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text("${reportLines.size} ligne(s) prête(s) à l'exportation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { pdfExportLauncher.launch("smartstore_${selectedType.lowercase()}.pdf") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("PDF", fontWeight = FontWeight.SemiBold)
                        }
                        FilledTonalButton(
                            onClick = { csvExportLauncher.launch("smartstore_${selectedType.lowercase()}.csv") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Rounded.TableChart, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Excel CSV", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(48.dp),
                            onClick = {
                                val html = buildString {
                                    append("<html><body><h2>SMART STORE PRO — $selectedTitle</h2><ul>")
                                    reportLines.forEach {
                                        val safeLine = it.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                                        append("<li>$safeLine</li>")
                                    }
                                    append("</ul></body></html>")
                                }
                                DocumentExporter.printHtmlDocument(context, selectedTitle, html)
                                onRecordReport(selectedType, selectedTitle, "Temps réel", "${reportLines.size} lignes imprimées")
                            }
                        ) {
                            Icon(Icons.Rounded.Print, contentDescription = "Imprimer")
                        }
                    }
                }
            }
        }
        itemsIndexed(reportLines, key = { idx, _ -> "rep_line_$idx" }) { _, line ->
            PremiumSurfaceCard(modifier = Modifier.animateItem()) {
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.fillMaxWidth().padding(14.dp)
                )
            }
        }
        if (reportsHistory.isNotEmpty()) {
            item { SectionHeader(title = "Rapports archivés", subtitle = "${reportsHistory.size} génération(s)") }
            items(reportsHistory.take(8), key = { "rep_${it.id}" }) { rep ->
                PremiumSurfaceCard(modifier = Modifier.animateItem()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(rep.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text("${rep.generatedByName} · ${Formatters.dateTime(rep.createdAt)} · ${rep.summaryText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun expLine(exp: ExpenseEntity): String = "${exp.category} | ${exp.description} | ${Formatters.fcfa(exp.amount)}"

@Composable
fun DocumentsScreen(
    documents: List<DocumentFileEntity>,
    onImportAnyUri: (Uri, String, String) -> Unit,
    onDeleteDocument: (DocumentFileEntity) -> Unit,
    onPostMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("AUTRE") }
    var importNotes by remember { mutableStateOf("") }
    var filterDir by remember { mutableStateOf("TOUS") }

    val anyFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportAnyUri(uri, selectedCategory, importNotes)
            importNotes = ""
        }
    }

    val filtered = remember(documents, filterDir) {
        documents.filter { filterDir == "TOUS" || it.direction == filterDir }
    }

    val ext = LocalSmartStoreColors.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Centre Documentaire & Import/Export",
                subtitle = "Importez tout fichier (PDF, Excel/CSV, JSON, Images, Word...) sans permission intrusive",
                action = {
                    Button(
                        onClick = { anyFilePicker.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("import_any_file_button")
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Importer fichier", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
        item {
            PremiumSurfaceCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Catégorie d'archivage pour le prochain import :", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("FACTURE", "JUSTIFICATIF", "CATALOGUE", "RAPPORT", "SAUVEGARDE", "AUTRE").forEach { cat ->
                            FilterChip(selected = selectedCategory == cat, onClick = { selectedCategory = cat }, shape = RoundedCornerShape(14.dp), label = { Text(cat) })
                        }
                    }
                    OutlinedTextField(
                        value = importNotes,
                        onValueChange = { importNotes = it },
                        label = { Text("Note optionnelle (auto-parse CSV produits / JSON sauvegarde)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("TOUS", "IMPORT", "EXPORT").forEach { dir ->
                    FilterChip(selected = filterDir == dir, onClick = { filterDir = dir }, shape = RoundedCornerShape(14.dp), label = { Text(dir) })
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "Aucun document archivé",
                    message = "Importez des factures fournisseurs, catalogues CSV, sauvegardes JSON ou exportez des rapports PDF/Excel.",
                    icon = Icons.Rounded.FolderOpen
                )
            }
        } else {
            items(filtered, key = { "doc_${it.id}" }) { doc ->
                PremiumSurfaceCard(modifier = Modifier.animateItem()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(doc.fileName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    "${doc.category} · ${doc.extension.uppercase()} · ${(doc.sizeBytes / 1024).coerceAtLeast(1)} Ko · ${Formatters.dateTime(doc.createdAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusBadge(
                                doc.direction,
                                if (doc.direction == "IMPORT") ext.successGreen else ext.violetPrimary
                            )
                        }
                        if (doc.notes.isNotBlank()) {
                            Text(doc.notes, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                shape = RoundedCornerShape(14.dp),
                                onClick = {
                                    val intent = DocumentExporter.openDocumentIntent(context, doc.localPath, doc.mimeType)
                                    if (intent != null) {
                                        try { context.startActivity(intent) } catch (_: Exception) { onPostMessage("Aucune application trouvée pour ouvrir ${doc.extension}") }
                                    } else {
                                        onPostMessage("Fichier introuvable localement")
                                    }
                                }
                            ) {
                                Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Ouvrir", fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                shape = RoundedCornerShape(14.dp),
                                onClick = {
                                    val intent = DocumentExporter.shareDocumentIntent(context, doc.localPath, doc.mimeType, doc.fileName)
                                    if (intent != null) {
                                        try { context.startActivity(intent) } catch (_: Exception) { onPostMessage("Impossible de partager ce fichier") }
                                    } else {
                                        onPostMessage("Fichier introuvable localement")
                                    }
                                }
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = "Partager")
                            }
                            IconButton(onClick = { onDeleteDocument(doc) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
