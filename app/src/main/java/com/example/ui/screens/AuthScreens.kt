package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.security.GoogleAuthHelper
import com.example.ui.components.GoldPremiumBadge
import com.example.ui.components.LiveClockBadge
import com.example.ui.theme.LocalSmartStoreColors
import com.example.util.Formatters
import com.example.util.StructureCatalog
import com.example.util.StructureLogoBadge
import kotlinx.coroutines.delay

@Composable
fun CloudGoogleAuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = LocalSmartStoreColors.current
    val credentialManager = remember { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        GoogleAuthHelper.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 520.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, ext.cardBorder)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(186.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_store_banner_1794766846510),
                        contentDescription = "Bannière Smart Store Pro",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        ext.midnightSecondary.copy(alpha = 0.58f),
                                        ext.midnightSecondary.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GoldPremiumBadge("SUITE CLOUD PRO · CAMEROUN")
                        Text(
                            "SMART STORE PRO",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            "Un seul système pour gérer tout le magasin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Espace Cloud du Magasin",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Connectez votre compte Google pour activer la synchronisation temps réel (Firestore), la sauvegarde multi-appareils et la caisse en FCFA (XAF).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text("Synchro Cloud", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = ext.successSoftBg
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = ext.successGreen, modifier = Modifier.size(18.dp))
                                Text("Zéro démo", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = ext.successGreen)
                            }
                        }
                    }

                    if (!errorMsg.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = errorMsg!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            isLoading = true
                            errorMsg = null
                            GoogleAuthHelper.onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    isLoading = false
                                    onAuthSuccess()
                                },
                                onAuthError = { msg ->
                                    isLoading = false
                                    errorMsg = msg
                                },
                                scope = scope,
                                onAuthCancelled = { isLoading = false }
                            )
                        },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("google_signin_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Rounded.CloudDone, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text("Se connecter avec Google", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StaffLoginScreen(
    cloudEmail: String,
    isDatabaseReady: Boolean,
    hasExistingUsers: Boolean,
    storeName: String,
    storeCity: String,
    storeLocation: String,
    logoKey: String,
    loginError: String?,
    onLogin: (String, String) -> Unit,
    onInitialSetup: (
        storeName: String,
        city: String,
        location: String,
        address: String,
        storePhone: String,
        storeEmail: String,
        structureType: String,
        logoKey: String,
        adminFullName: String,
        adminUsername: String,
        adminPassword: String,
        adminPhone: String,
        adminWhatsapp: String,
        adminEmail: String,
        createManager: Boolean,
        managerFullName: String,
        managerUsername: String,
        managerPassword: String,
        managerPhone: String,
        managerWhatsapp: String,
        managerEmail: String
    ) -> Unit,
    onSignOutGoogle: () -> Unit
) {
    val ext = LocalSmartStoreColors.current
    val liveClockMs by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000L)
        }
    }
    var selectedPresetKey by remember(logoKey) { mutableStateOf(logoKey.ifBlank { "GENERAL" }) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!isDatabaseReady) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            return@Box
        }

        Surface(
            modifier = Modifier.widthIn(max = 580.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, ext.cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ext.midnightSecondary
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            StructureLogoBadge(
                                logoKey = if (!hasExistingUsers) selectedPresetKey else logoKey,
                                size = 48.dp
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    storeName.ifBlank { "SMART STORE PRO" },
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                val loc = listOf(storeCity, storeLocation).filter { it.isNotBlank() }.joinToString(" · ")
                                Text(
                                    if (loc.isNotBlank()) "$loc · $cloudEmail" else "Espace Cloud · $cloudEmail",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White.copy(alpha = 0.78f)
                                )
                            }
                        }
                        IconButton(onClick = onSignOutGoogle) {
                            Icon(
                                Icons.Rounded.Logout,
                                contentDescription = "Déconnecter le compte Google",
                                tint = Color.White
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (!hasExistingUsers) "Initialisation de votre structure" else "Connexion Employé / Caisse",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${Formatters.liveClockDate(liveClockMs)} · ${Formatters.timeWithSeconds(liveClockMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LiveClockBadge(
                        epochMs = liveClockMs,
                        isOnlineSync = true,
                        showDate = false
                    )
                }

                HorizontalDivider(color = ext.cardBorder)

                if (!hasExistingUsers) {
                    var initStoreName by remember { mutableStateOf("") }
                    var initCity by remember { mutableStateOf("") }
                    var initLocation by remember { mutableStateOf("") }
                    var initAddress by remember { mutableStateOf("") }
                    var initStorePhone by remember { mutableStateOf("") }
                    var initStoreEmail by remember { mutableStateOf("") }

                    var adminFullName by remember { mutableStateOf("") }
                    var adminUsername by remember { mutableStateOf("") }
                    var adminPhone by remember { mutableStateOf("") }
                    var adminWhatsapp by remember { mutableStateOf("") }
                    var adminEmail by remember { mutableStateOf("") }
                    var adminPassword by remember { mutableStateOf("") }
                    var showPass by remember { mutableStateOf(false) }

                    var createManager by remember { mutableStateOf(false) }
                    var managerFullName by remember { mutableStateOf("") }
                    var managerUsername by remember { mutableStateOf("") }
                    var managerPhone by remember { mutableStateOf("") }
                    var managerWhatsapp by remember { mutableStateOf("") }
                    var managerEmail by remember { mutableStateOf("") }
                    var managerPassword by remember { mutableStateOf("") }
                    var showManagerPass by remember { mutableStateOf(false) }

                    val selectedPreset = StructureCatalog.presetFor(selectedPresetKey)

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ) {
                        Text(
                            "Base 100% vierge (aucune donnée démo). Configurez votre structure, sélectionnez votre logo adapté et créez votre compte Administrateur.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Text("1. Type & Logo adapté de la structure", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StructureCatalog.presets.forEach { preset ->
                            val isSelected = preset.key == selectedPresetKey
                            Surface(
                                onClick = { selectedPresetKey = preset.key },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else ext.cardBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    StructureLogoBadge(logoKey = preset.key, size = 34.dp)
                                    Column {
                                        Text(
                                            preset.label,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            preset.tagline,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text("2. Identité & Localisation (${selectedPreset.label})", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(value = initStoreName, onValueChange = { initStoreName = it }, label = { Text("Nom de la structure / magasin *") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(value = initCity, onValueChange = { initCity = it }, label = { Text("Ville *") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = initLocation, onValueChange = { initLocation = it }, label = { Text("Lieu / Quartier") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = initAddress, onValueChange = { initAddress = it }, label = { Text("Adresse complète / Repère") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(value = initStorePhone, onValueChange = { initStorePhone = it }, label = { Text("Tél. structure") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = initStoreEmail, onValueChange = { initStoreEmail = it }, label = { Text("Email structure") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
                    }

                    HorizontalDivider(color = ext.cardBorder)
                    Text("3. Compte Administrateur principal", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(value = adminFullName, onValueChange = { adminFullName = it }, label = { Text("Nom complet *") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = adminUsername, onValueChange = { adminUsername = it }, label = { Text("Identifiant de connexion *") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = adminPhone, onValueChange = { adminPhone = it }, label = { Text("Téléphone personnel") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = adminWhatsapp, onValueChange = { adminWhatsapp = it }, label = { Text("Numéro WhatsApp") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
                        TextButton(onClick = { adminWhatsapp = adminPhone }) { Text("= Tél.") }
                    }
                    OutlinedTextField(value = adminEmail, onValueChange = { adminEmail = it }, label = { Text("Email personnel") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = adminPassword,
                        onValueChange = { adminPassword = it },
                        label = { Text("Mot de passe (min. 4 car.) *") },
                        trailingIcon = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(
                                    if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Afficher mot de passe"
                                )
                            }
                        },
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = ext.cardBorder)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, ext.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "4. Compte Gestionnaire (optionnel)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = ext.violetPrimary
                                    )
                                    Text(
                                        "Ajouter dès maintenant un compte Gestionnaire (produits, stocks, achats, rapports & reset).",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(checked = createManager, onCheckedChange = { createManager = it })
                            }
                            if (createManager) {
                                OutlinedTextField(value = managerFullName, onValueChange = { managerFullName = it }, label = { Text("Nom complet gestionnaire *") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = managerUsername, onValueChange = { managerUsername = it }, label = { Text("Identifiant gestionnaire *") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = managerPhone, onValueChange = { managerPhone = it }, label = { Text("Téléphone gestionnaire") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = managerWhatsapp, onValueChange = { managerWhatsapp = it }, label = { Text("WhatsApp gestionnaire") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
                                    TextButton(onClick = { managerWhatsapp = managerPhone }) { Text("= Tél.") }
                                }
                                OutlinedTextField(value = managerEmail, onValueChange = { managerEmail = it }, label = { Text("Email gestionnaire") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(
                                    value = managerPassword,
                                    onValueChange = { managerPassword = it },
                                    label = { Text("Mot de passe gestionnaire (min. 4 car.) *") },
                                    trailingIcon = {
                                        IconButton(onClick = { showManagerPass = !showManagerPass }) {
                                            Icon(
                                                if (showManagerPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                contentDescription = "Afficher mot de passe gestionnaire"
                                            )
                                        }
                                    },
                                    visualTransformation = if (showManagerPass) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    if (!loginError.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = loginError,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onInitialSetup(
                                initStoreName,
                                initCity,
                                initLocation,
                                initAddress,
                                initStorePhone,
                                initStoreEmail,
                                selectedPreset.label,
                                selectedPreset.key,
                                adminFullName,
                                adminUsername,
                                adminPassword,
                                adminPhone,
                                adminWhatsapp,
                                adminEmail,
                                createManager,
                                managerFullName,
                                managerUsername,
                                managerPassword,
                                managerPhone,
                                managerWhatsapp,
                                managerEmail
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("initial_setup_button")
                    ) {
                        Icon(Icons.Rounded.VerifiedUser, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Créer ma structure & Démarrer", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                    }
                } else {
                    var username by remember { mutableStateOf("") }
                    var password by remember { mutableStateOf("") }
                    var showPass by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Identifiant utilisateur") },
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("staff_username_input")
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Mot de passe") },
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(
                                    if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Afficher mot de passe"
                                )
                            }
                        },
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onLogin(username, password) }),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("staff_password_input")
                    )

                    if (!loginError.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = loginError,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Button(
                        onClick = { onLogin(username, password) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("staff_login_button")
                    ) {
                        Icon(Icons.Rounded.Login, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ouvrir la session", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                    }
                    Text(
                        "Accès Administrateur, Gestionnaire, Caissier ou Employé. Vous pourrez modifier votre mot de passe depuis le menu utilisateur une fois connecté.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

