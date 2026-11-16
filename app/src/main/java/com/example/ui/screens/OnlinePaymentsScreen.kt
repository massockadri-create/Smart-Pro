package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.CustomerEntity
import com.example.data.local.OnlinePaymentConfigEntity
import com.example.data.local.OnlinePaymentEntity
import com.example.data.remote.OnlinePaymentGatewayType
import com.example.data.remote.OnlinePaymentService
import com.example.ui.components.*
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalSmartStoreColors
import com.example.util.Formatters

enum class PaymentProviderOption(val code: String, val label: String) {
    CINETPAY("CinetPay", "CinetPay (MoMo / OM / Carte)"),
    NOTCHPAY("NotchPay", "NotchPay (Mobile Money & Visa)"),
    ORANGE_MONEY("Orange Money", "Orange Money Direct"),
    MTN_MOMO("MTN MoMo", "MTN Mobile Money Direct")
}

@Composable
fun OnlinePaymentsScreen(
    payments: List<OnlinePaymentEntity>,
    customers: List<CustomerEntity>,
    configuredGateways: List<OnlinePaymentConfigEntity> = emptyList(),
    canConfigureGateways: Boolean = false,
    activePayment: OnlinePaymentEntity?,
    isProcessing: Boolean,
    isCinetPayConfigured: Boolean,
    isNotchPayConfigured: Boolean,
    onOpenGatewaySettings: () -> Unit = {},
    onSelectPayment: (OnlinePaymentEntity?) -> Unit,
    onInitiatePayment: (
        provider: String,
        channel: String,
        amountXaf: Double,
        customerName: String,
        customerPhone: String,
        customerEmail: String,
        description: String,
        configId: Long
    ) -> Unit,
    onVerifyPayment: (OnlinePaymentEntity) -> Unit,
    onConfirmManual: (OnlinePaymentEntity, String) -> Unit,
    onCancelPayment: (OnlinePaymentEntity) -> Unit
) {
    val ext = LocalSmartStoreColors.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var filterStatus by remember { mutableStateOf("TOUS") }

    val activeGateways = remember(configuredGateways) {
        configuredGateways.filter { it.isEnabled }
    }
    val hasAnyAdminGateway = activeGateways.isNotEmpty() || isCinetPayConfigured || isNotchPayConfigured

    val paidTotal = remember(payments) { payments.filter { it.status == "CONFIRME" }.sumOf { it.amountXaf } }
    val pendingCount = remember(payments) {
        payments.count { it.status == "EN_ATTENTE_PAIEMENT" || it.status == "EN_ATTENTE_VALIDATION" }
    }
    val paidCount = remember(payments) { payments.count { it.status == "CONFIRME" } }

    val filtered = remember(payments, filterStatus) {
        payments.filter {
            when (filterStatus) {
                "TOUS" -> true
                "EN_ATTENTE" -> it.status == "EN_ATTENTE_PAIEMENT" || it.status == "EN_ATTENTE_VALIDATION"
                else -> it.status == filterStatus
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            FintechHeroBanner(
                title = "Paiements en ligne & Code-barres",
                subtitle = if (activeGateways.isNotEmpty()) {
                    activeGateways.joinToString(" · ") { it.displayName }
                } else {
                    "Mobile Money · Passerelles API · Carte/Virement · Liens de paiement"
                },
                badgeText = if (hasAnyAdminGateway) "${activeGateways.size.coerceAtLeast(1)} ACTIF(S)" else "À CONFIGURER",
                primaryMetricLabel = "Encaissements confirmés",
                primaryMetricValue = Formatters.fcfa(paidTotal),
                secondaryMetricLabel = "Transactions",
                secondaryMetricValue = "$paidCount confirmée(s) · $pendingCount att.",
                icon = Icons.Rounded.QrCode2
            ) {
                Button(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.violetPrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("new_online_payment_button")
                ) {
                    Icon(Icons.Rounded.AddLink, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Encaisser en ligne", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            PremiumSurfaceCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Passerelles & Comptes de l'entreprise",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f)
                        )
                        if (canConfigureGateways) {
                            TextButton(
                                onClick = onOpenGatewaySettings,
                                modifier = Modifier.testTag("open_payment_settings_button")
                            ) {
                                Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Configurer", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    if (activeGateways.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            activeGateways.forEach { gw ->
                                val isApi = gw.category == OnlinePaymentGatewayType.CINETPAY.code ||
                                    gw.category == OnlinePaymentGatewayType.NOTCHPAY.code ||
                                    gw.category == OnlinePaymentGatewayType.CUSTOM_API.code
                                if (isApi) {
                                    GoldPremiumBadge("${gw.displayName} ✓")
                                } else {
                                    StatusBadge("${gw.displayName} ✓", ext.successGreen)
                                }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            activeGateways.forEach { gw ->
                                val summary = buildList {
                                    add(OnlinePaymentGatewayType.fromCode(gw.category).label)
                                    if (gw.receiverPhone.isNotBlank()) add("Tél: ${gw.receiverPhone}")
                                    if (gw.siteOrMerchantId.isNotBlank()) add("ID: ${gw.siteOrMerchantId}")
                                    if (gw.accountHolderName.isNotBlank()) add(gw.accountHolderName)
                                }.joinToString(" · ")
                                Text(
                                    "• ${gw.displayName} — $summary",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Text(
                            if (canConfigureGateways) {
                                "Aucune passerelle de paiement n'est encore configurée pour votre entreprise. Ajoutez vos propres comptes (CinetPay, NotchPay, Orange Money, MTN MoMo, Compte Bancaire, Lien Wave/PayPal/Stripe ou API personnalisée) dans Paramètres."
                            } else {
                                "Demandez à l'administrateur de votre entreprise d'intégrer vos comptes et clés de paiement en ligne dans Paramètres. Le mode QR Code & Code-barres reste disponible."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("TOUS" to "Tous", "EN_ATTENTE" to "En attente", "CONFIRME" to "Confirmés", "ECHOUE" to "Échoués / Annulés").forEach { (code, label) ->
                    FilterChip(
                        selected = filterStatus == code,
                        onClick = { filterStatus = code },
                        shape = RoundedCornerShape(14.dp),
                        label = { Text(label, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "Aucun paiement en ligne",
                    message = "Générez un paiement via la passerelle de votre entreprise avec QR Code et Code-barres scannable.",
                    icon = Icons.Rounded.QrCodeScanner
                )
            }
        } else {
            items(filtered, key = { "opay_${it.id}" }) { pay ->
                val statusColor = when (pay.status) {
                    "CONFIRME" -> ext.successGreen
                    "EN_ATTENTE_PAIEMENT", "EN_ATTENTE_VALIDATION" -> ext.violetPrimary
                    else -> MaterialTheme.colorScheme.error
                }
                PremiumSurfaceCard(
                    modifier = Modifier.animateItem(),
                    onClick = { onSelectPayment(pay) }
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "${pay.transactionId} · ${pay.customerName}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    "${pay.provider} · ${Formatters.dateTimeWithSeconds(pay.createdAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusBadge(pay.status, statusColor)
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                Formatters.fcfa(pay.amountXaf),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = ext.violetPrimary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (pay.invoiceNumber.isNotBlank()) {
                                    StatusBadge(pay.invoiceNumber, ext.midnightSecondary)
                                }
                                FilledTonalButton(
                                    onClick = { onSelectPayment(pay) },
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("QR & Code-barres", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        if (pay.gatewayMessage.isNotBlank()) {
                            Text(
                                pay.gatewayMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateOnlinePaymentDialog(
            customers = customers,
            configuredGateways = activeGateways,
            onDismiss = { showCreateDialog = false },
            onCreate = { prov, ch, amt, cust, phone, email, desc, cfgId ->
                showCreateDialog = false
                onInitiatePayment(prov, ch, amt, cust, phone, email, desc, cfgId)
            }
        )
    }

    if (activePayment != null) {
        OnlinePaymentTicketDialog(
            payment = activePayment,
            isProcessing = isProcessing,
            onDismiss = { onSelectPayment(null) },
            onVerify = { onVerifyPayment(activePayment) },
            onConfirmManual = { ref -> onConfirmManual(activePayment, ref) },
            onCancel = { onCancelPayment(activePayment) }
        )
    }
}

@Composable
private fun CreateOnlinePaymentDialog(
    customers: List<CustomerEntity>,
    configuredGateways: List<OnlinePaymentConfigEntity>,
    onDismiss: () -> Unit,
    onCreate: (String, String, Double, String, String, String, String, Long) -> Unit
) {
    var selectedConfig by remember(configuredGateways) {
        mutableStateOf(configuredGateways.firstOrNull())
    }
    var fallbackProvider by remember { mutableStateOf(PaymentProviderOption.ORANGE_MONEY) }
    var channel by remember { mutableStateOf("MOBILE_MONEY") }
    var amountStr by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var customerEmail by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvel encaissement en ligne", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    if (configuredGateways.isNotEmpty()) "Mode configuré par l'entreprise :" else "Mode de paiement :",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (configuredGateways.isNotEmpty()) {
                        configuredGateways.forEach { gw ->
                            val subtitle = buildList {
                                add(OnlinePaymentGatewayType.fromCode(gw.category).label)
                                if (gw.receiverPhone.isNotBlank()) add(gw.receiverPhone)
                            }.joinToString(" · ")
                            FilterChip(
                                selected = selectedConfig?.id == gw.id,
                                onClick = { selectedConfig = gw },
                                label = { Text("${gw.displayName} ($subtitle)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        PaymentProviderOption.entries.forEach { gw ->
                            FilterChip(
                                selected = fallbackProvider == gw,
                                onClick = { fallbackProvider = gw },
                                label = { Text(gw.label) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Text("Canal :", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("MOBILE_MONEY" to "Mobile Money", "ALL" to "Carte & Tout").forEach { (code, label) ->
                        FilterChip(selected = channel == code, onClick = { channel = code }, label = { Text(label) })
                    }
                }

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Montant (FCFA / XAF)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("online_pay_amount_input")
                )
                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text("Nom du client") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (customers.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        customers.take(6).forEach { cust ->
                            AssistChip(
                                onClick = {
                                    customerName = cust.name
                                    if (cust.phone.isNotBlank()) customerPhone = cust.phone
                                    if (cust.email.isNotBlank()) customerEmail = cust.email
                                },
                                label = { Text(cust.name) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = { customerPhone = it },
                    placeholder = { Text("Ex: +237 6...") },
                    label = { Text("Téléphone") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = customerEmail,
                    onValueChange = { customerEmail = it },
                    label = { Text("Email client (optionnel)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Motif / Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                shape = RoundedCornerShape(14.dp),
                onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    if (amt >= 100.0) {
                        val chosenName = selectedConfig?.displayName ?: fallbackProvider.code
                        val chosenId = selectedConfig?.id ?: 0L
                        onCreate(
                            chosenName,
                            channel,
                            amt,
                            customerName.ifBlank { "Client Comptant" },
                            customerPhone,
                            customerEmail,
                            description,
                            chosenId
                        )
                    }
                }
            ) {
                Text("Générer Lien & Code-barres", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
fun OnlinePaymentTicketDialog(
    payment: OnlinePaymentEntity,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onVerify: () -> Unit,
    onConfirmManual: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val ext = LocalSmartStoreColors.current
    var operatorRefInput by remember(payment.id) { mutableStateOf("") }
    val isPending = payment.status == "EN_ATTENTE_PAIEMENT" || payment.status == "EN_ATTENTE_VALIDATION"
    val statusColor = when (payment.status) {
        "CONFIRME" -> ext.successGreen
        "EN_ATTENTE_PAIEMENT", "EN_ATTENTE_VALIDATION" -> ext.violetPrimary
        else -> MaterialTheme.colorScheme.error
    }
    val qrPayload = remember(payment) {
        payment.paymentUrl.ifBlank {
            OnlinePaymentService.buildQrPaymentUri(
                payment.provider.uppercase(),
                payment.transactionId,
                payment.amountXaf,
                payment.customerPhone
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Paiement en ligne", fontWeight = FontWeight.Bold)
                StatusBadge(payment.status, statusColor)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, ext.violetPrimary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            Formatters.fcfa(payment.amountXaf),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = ext.violetPrimary
                        )
                        Text(
                            "${payment.provider} · ${payment.customerName}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (payment.invoiceNumber.isNotBlank()) {
                            Text("Facture : ${payment.invoiceNumber}", style = MaterialTheme.typography.labelSmall, color = ext.midnightSecondary)
                        }
                    }
                }

                RealQrCodeView(
                    content = qrPayload,
                    caption = if (payment.isLiveGateway) "SCANNEZ POUR PAYER EN LIGNE" else "QR TRANSACTION · ${payment.transactionId}",
                    accentColor = statusColor
                )

                VisualBarcodeView(
                    code = payment.transactionId,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    payment.gatewayMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (payment.paymentUrl.startsWith("http")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(payment.paymentUrl))
                                runCatching { context.startActivity(intent) }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Ouvrir paiement")
                        }
                        OutlinedButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Paiement (${Formatters.fcfa(payment.amountXaf)})\nRéf: ${payment.transactionId}\nLien: ${payment.paymentUrl}"
                                    )
                                }
                                runCatching { context.startActivity(Intent.createChooser(shareIntent, "Partager le lien de paiement")) }
                            }
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = "Partager")
                        }
                    }
                }

                if (isPending) {
                    OutlinedTextField(
                        value = operatorRefInput,
                        onValueChange = { operatorRefInput = it },
                        label = { Text("Référence SMS / ID Transaction opérateur") },
                        placeholder = { Text("Ex: ID opérateur ou reçu") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (isPending) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = onVerify,
                            enabled = !isProcessing
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Text("Vérifier API")
                        }
                        Button(
                            onClick = { onConfirmManual(operatorRefInput) },
                            enabled = !isProcessing
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Confirmer reçu")
                        }
                    }
                }
            } else {
                Button(onClick = onDismiss) { Text("Terminé") }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isPending) {
                    TextButton(onClick = onCancel) {
                        Text("Annuler", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Fermer") }
            }
        }
    )
}
