package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.*
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalSmartStoreColors
import com.example.util.Formatters

@Composable
fun DesktopSyncScreen(
    isServerRunning: Boolean,
    serverUrl: String,
    localIps: List<String>,
    port: Int,
    lastSyncAt: Long,
    pendingSyncCount: Int,
    offlineModeEnabled: Boolean,
    isSyncing: Boolean,
    currentTimeMs: Long,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onSyncCloudNow: () -> Unit,
    onPostMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val ext = LocalSmartStoreColors.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            FintechHeroBanner(
                title = "Version Ordinateur (PC / Mac)",
                subtitle = "Synchro temps réel · ${Formatters.timeWithSeconds(currentTimeMs)}",
                badgeText = if (isServerRunning) "PORTAIL PC ACTIF" else "PORTAIL ARRÊTÉ",
                primaryMetricLabel = "Adresse d'accès ordinateur",
                primaryMetricValue = if (isServerRunning) serverUrl else "Serveur hors tension",
                secondaryMetricLabel = "Synchro Cloud",
                secondaryMetricValue = if (offlineModeEnabled) "File locale" else "$pendingSyncCount en attente",
                icon = Icons.Rounded.Computer
            ) {
                Button(
                    onClick = { if (isServerRunning) onStopServer() else onStartServer() },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.violetPrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("desktop_server_toggle_btn")
                ) {
                    Icon(
                        if (isServerRunning) Icons.Rounded.StopCircle else Icons.Rounded.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isServerRunning) "Arrêter Serveur PC" else "Démarrer Serveur PC", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onSyncCloudNow,
                    enabled = !isSyncing,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.16f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Rounded.CloudSync, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isSyncing) "Synchro..." else "Sync Cloud", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (isServerRunning) {
            item {
                PremiumSurfaceCard {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Ouvrir la version ordinateur sur PC / Mac / Linux",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Connectez l'ordinateur au même réseau Wi-Fi puis scannez ce QR Code ou ouvrez l'URL dans Chrome / Edge / Safari :",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        RealQrCodeView(
                            content = serverUrl,
                            caption = "Scanner pour ouvrir la version ordinateur",
                            accentColor = ext.violetPrimary,
                            modifier = Modifier.padding(4.dp)
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                serverUrl,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = ext.violetPrimary,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("Smart Store PC URL", serverUrl))
                                    onPostMessage("Lien ordinateur copié : $serverUrl")
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copier URL", fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl)))
                                    } catch (_: Exception) {
                                        onPostMessage("Impossible d'ouvrir le navigateur local")
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Tester ici", fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = {
                                    val shareIntent = Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "SMART STORE PRO — Version Ordinateur")
                                            putExtra(Intent.EXTRA_TEXT, "Accès poste ordinateur synchronisé : $serverUrl")
                                        },
                                        "Partager l'adresse PC"
                                    )
                                    runCatching { context.startActivity(shareIntent) }
                                        .onFailure { onPostMessage("Aucune application de partage trouvée") }
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ext.midnightSecondary,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Partager", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        item {
            PremiumSurfaceCard {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Architecture de synchronisation double (PC & Cloud)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "1. Poste Web Ordinateur (Port $port) : rafraîchissement automatique toutes les 2,5 secondes des ventes, factures à code catégorie, catalogue produits, paiements en ligne et alertes de stock.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "2. Grand Écran Android / Chromebook / Windows : dès 1000 dp de largeur, l'application bascule automatiquement en interface Bureau avec barre latérale permanente complète.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "3. Cloud Firestore : synchronisation multi-appareils · Dernière synchro : ${if (lastSyncAt > 0) Formatters.dateTimeWithSeconds(lastSyncAt) else "À effectuer"}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (localIps.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = ext.cardBorder)
                        Text("Adresses réseau détectées :", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                        localIps.forEach { ip ->
                            Text("• http://$ip:$port", style = MaterialTheme.typography.bodySmall.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold), color = ext.violetPrimary)
                        }
                    }
                }
            }
        }
    }
}

