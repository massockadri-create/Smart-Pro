package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.*
import com.example.ui.components.*
import com.example.ui.theme.LocalSmartStoreColors
import com.example.ui.viewmodel.DashboardMetrics
import com.example.ui.viewmodel.StoreRoute
import com.example.util.Formatters

@Composable
fun DashboardScreen(
    metrics: DashboardMetrics,
    openRegister: CashRegisterEntity?,
    recentSales: List<SaleEntity>,
    alerts: List<AlertNotificationEntity>,
    cashMovements: List<CashMovementEntity>,
    currentTimeMs: Long,
    offlineModeEnabled: Boolean,
    canViewProfits: Boolean,
    isSyncing: Boolean,
    onNavigate: (StoreRoute) -> Unit,
    onSyncNow: () -> Unit,
    onMarkAlertRead: (Long) -> Unit
) {
    val ext = LocalSmartStoreColors.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            FintechHeroBanner(
                title = "SMART STORE PRO · Pilotage Live",
                subtitle = "${Formatters.liveClockDate(currentTimeMs)} · ${Formatters.timeWithSeconds(currentTimeMs)}",
                badgeText = if (offlineModeEnabled) "HORS LIGNE" else "CLOUD LIVE",
                primaryMetricLabel = "Chiffre d'affaires du jour",
                primaryMetricValue = Formatters.fcfa(metrics.todaySales),
                secondaryMetricLabel = if (canViewProfits) "Bénéfice brut" else "Caisse active",
                secondaryMetricValue = if (canViewProfits) Formatters.fcfa(metrics.totalProfit) else Formatters.fcfa(openRegister?.theoreticalBalance ?: 0.0),
                icon = Icons.Rounded.Storefront
            ) {
                LiveClockBadge(epochMs = currentTimeMs, isOnlineSync = !offlineModeEnabled, showDate = false)
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onSyncNow,
                    enabled = !isSyncing,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.16f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        if (isSyncing) Icons.Rounded.Sync else Icons.Rounded.CloudSync,
                        contentDescription = "Synchroniser"
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (metrics.pendingSyncCount > 0) "Sync (${metrics.pendingSyncCount})" else "Sync OK",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Quick Actions Row (Violet & Midnight Blue buttons, clean 14dp geometry)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onNavigate(StoreRoute.POS) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Icon(Icons.Rounded.PointOfSale, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ouvrir Caisse POS", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { onNavigate(StoreRoute.ONLINE_PAYMENTS) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ext.midnightSecondary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Icon(Icons.Rounded.QrCode2, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Encaisser QR", fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onNavigate(StoreRoute.INVOICES) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Rounded.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Factures", maxLines = 1, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { onNavigate(StoreRoute.CALCULATOR) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Rounded.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Calculatrice", maxLines = 1, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { onNavigate(StoreRoute.DESKTOP_SYNC) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Rounded.Computer, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("PC Sync", maxLines = 1, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Primary KPI Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KpiMetricCard(
                        title = "CA du jour",
                        value = Formatters.fcfa(metrics.todaySales),
                        subtitle = "${metrics.salesCount} ventes validées",
                        icon = Icons.Rounded.Today,
                        accentColor = ext.violetPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        title = "CA semaine",
                        value = Formatters.fcfa(metrics.weekSales),
                        subtitle = "Mois : ${Formatters.fcfaCompact(metrics.monthSales)}",
                        icon = Icons.Rounded.DateRange,
                        accentColor = ext.successGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KpiMetricCard(
                        title = if (canViewProfits) "Bénéfice & Marge" else "Caisse active",
                        value = if (canViewProfits) Formatters.fcfa(metrics.totalProfit) else Formatters.fcfa(openRegister?.theoreticalBalance ?: 0.0),
                        subtitle = if (canViewProfits) "Marge brute : ${Formatters.percent(metrics.marginPercent)}" else if (openRegister != null) "Caisse ouverte" else "Caisse fermée",
                        icon = Icons.Rounded.TrendingUp,
                        accentColor = ext.successGreen,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        title = "Dépenses",
                        value = Formatters.fcfa(metrics.totalExpenses),
                        subtitle = "Valeur stock : ${Formatters.fcfaCompact(metrics.totalStockValue)}",
                        icon = Icons.Rounded.Payments,
                        accentColor = ext.midnightSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KpiMetricCard(
                        title = "Produits & Alertes",
                        value = "${metrics.productsCount} articles",
                        subtitle = "${metrics.lowStockCount} faibles · ${metrics.outOfStockCount} ruptures",
                        icon = Icons.Rounded.Inventory2,
                        accentColor = if (metrics.outOfStockCount > 0) MaterialTheme.colorScheme.error else ext.violetPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        title = "Tiers",
                        value = "${metrics.customersCount} clients",
                        subtitle = "${metrics.suppliersCount} fournisseurs actifs",
                        icon = Icons.Rounded.Groups,
                        accentColor = ext.midnightSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Charts
        item {
            SalesLineChart(
                series = metrics.dailySeries,
                lineColor = ext.violetPrimary
            )
        }

        item {
            HorizontalBarChart(
                title = "Produits les plus vendus (quantités)",
                items = metrics.topProducts.map { it.first to it.second.toDouble() },
                barColor = ext.midnightSecondary,
                valueFormatter = { "${it.toInt()} u." }
            )
        }

        if (canViewProfits) {
            item {
                HorizontalBarChart(
                    title = "Synthèse Rentabilité (Mois)",
                    items = listOf(
                        "Chiffre d'affaires" to metrics.monthSales,
                        "Bénéfice brut" to metrics.totalProfit,
                        "Dépenses" to metrics.totalExpenses,
                        "Résultat net estimé" to (metrics.totalProfit - metrics.totalExpenses).coerceAtLeast(0.0)
                    ),
                    barColor = ext.successGreen
                )
            }
        }

        // Important Alerts
        item {
            SectionHeader(
                title = "Alertes importantes",
                subtitle = "${alerts.count { !it.isRead }} non lue(s)",
                action = {
                    TextButton(onClick = { onNavigate(StoreRoute.AUDIT) }) {
                        Text("Tout voir", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }

        if (alerts.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "Aucune alerte active",
                    message = "Tous les niveaux de stock et opérations sont conformes."
                )
            }
        } else {
            items(alerts.take(4), key = { "dash_alert_${it.id}" }) { alert ->
                val sevColor = when (alert.severity) {
                    "CRITICAL" -> MaterialTheme.colorScheme.error
                    "WARNING" -> ext.goldOnSoft
                    else -> ext.violetPrimary
                }
                PremiumSurfaceCard(
                    modifier = Modifier.animateItem(),
                    onClick = { onMarkAlertRead(alert.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatusBadge(alert.type, sevColor)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(alert.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text(alert.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!alert.isRead) {
                            Icon(Icons.Rounded.MarkEmailUnread, contentDescription = "Non lu", tint = sevColor, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Cash Movements & Recent Transactions
        item {
            SectionHeader(
                title = "Dernières transactions & caisse",
                subtitle = "${cashMovements.size} mouvement(s) de caisse enregistrés",
                action = {
                    TextButton(onClick = { onNavigate(StoreRoute.SALES_HISTORY) }) {
                        Text("Historique", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }

        items(recentSales.take(5), key = { "dash_sale_${it.id}" }) { sale ->
            PremiumSurfaceCard(modifier = Modifier.animateItem()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${sale.invoiceNumber} · ${sale.customerName}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text(
                            "${sale.paymentMethod} · ${Formatters.dateTime(sale.createdAt)} · ${sale.userName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            Formatters.fcfa(sale.totalAmount),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = ext.violetPrimary
                        )
                        StatusBadge(
                            sale.status,
                            if (sale.status == "VALIDEE") ext.successGreen else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

