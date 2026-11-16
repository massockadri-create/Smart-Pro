package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.*
import com.example.data.remote.CloudAuditLog
import com.example.data.remote.CloudBackup
import com.example.data.remote.OnlinePaymentGatewayType
import com.example.data.repository.AdminResetScope
import com.example.security.AppPermission
import com.example.security.RoleCode
import com.example.ui.components.*
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalSmartStoreColors
import com.example.util.Formatters
import com.example.util.StructureCatalog
import com.example.util.StructureLogoBadge

@Composable
fun ChangeOwnPasswordDialog(
    fullName: String,
    username: String,
    onDismiss: () -> Unit,
    onSubmit: (currentPassword: String, newPassword: String, confirmPassword: String, onDone: (Result<Unit>) -> Unit) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showCurrent by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        shape = RoundedCornerShape(18.dp),
        icon = { Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Column {
                Text("Changer mon mot de passe", fontWeight = FontWeight.Bold)
                Text(
                    "$fullName (@$username)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it; errorMsg = null },
                    label = { Text("Mot de passe actuel *") },
                    visualTransformation = if (showCurrent) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showCurrent = !showCurrent }) {
                            Icon(
                                if (showCurrent) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Afficher mot de passe actuel"
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("own_current_password_input")
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; errorMsg = null },
                    label = { Text("Nouveau mot de passe (min. 4 car.) *") },
                    visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showNew = !showNew }) {
                            Icon(
                                if (showNew) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Afficher nouveau mot de passe"
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("own_new_password_input")
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; errorMsg = null },
                    label = { Text("Confirmer le nouveau mot de passe *") },
                    visualTransformation = if (showConfirm) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showConfirm = !showConfirm }) {
                            Icon(
                                if (showConfirm) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Afficher confirmation"
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("own_confirm_password_input")
                )
                if (!errorMsg.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = errorMsg!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("confirm_change_own_password_button"),
                onClick = {
                    when {
                        currentPassword.isBlank() -> errorMsg = "Saisissez votre mot de passe actuel."
                        newPassword.length < 4 -> errorMsg = "Le nouveau mot de passe doit contenir au moins 4 caractères."
                        newPassword != confirmPassword -> errorMsg = "La confirmation ne correspond pas."
                        else -> {
                            isSubmitting = true
                            onSubmit(currentPassword, newPassword, confirmPassword) { res ->
                                isSubmitting = false
                                res.onSuccess { onDismiss() }
                                    .onFailure { err -> errorMsg = err.message ?: "Impossible de modifier le mot de passe." }
                            }
                        }
                    }
                }
            ) {
                Text(if (isSubmitting) "Validation..." else "Mettre à jour", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(enabled = !isSubmitting, onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
fun UsersPermissionsScreen(
    users: List<UserEntity>,
    userPermissions: List<UserPermissionEntity>,
    currentUser: UserEntity? = null,
    onCreateUser: (String, String, String, String, String, String, String, String, Double) -> Unit,
    onUpdateUserProfile: (UserEntity, String, String, String, String, String, Double, String, String?) -> Unit,
    onResetUserPassword: (UserEntity, String, String, (Result<Unit>) -> Unit) -> Unit = { _, _, _, cb -> cb(Result.success(Unit)) },
    onChangeOwnPassword: ((String, String, String, (Result<Unit>) -> Unit) -> Unit)? = null,
    onToggleActive: (UserEntity) -> Unit,
    onSetPermission: (UserEntity, String, Boolean) -> Unit
) {
    val ext = LocalSmartStoreColors.current
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingProfileUser by remember { mutableStateOf<UserEntity?>(null) }
    var editingPermsUser by remember { mutableStateOf<UserEntity?>(null) }
    var resettingPassUser by remember { mutableStateOf<UserEntity?>(null) }
    var showOwnPassDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var roleFilter by remember { mutableStateOf("TOUS") }

    val activeCount = remember(users) { users.count { it.isActive } }
    val totalPayroll = remember(users) { users.filter { it.isActive }.sumOf { it.salaryFcfa } }

    val filteredUsers = remember(users, searchQuery, roleFilter) {
        val q = searchQuery.trim().lowercase()
        users.filter { u ->
            val matchRole = roleFilter == "TOUS" || u.roleCode == roleFilter
            val matchSearch = q.isEmpty() ||
                u.fullName.lowercase().contains(q) ||
                u.username.lowercase().contains(q) ||
                u.phone.lowercase().contains(q) ||
                u.whatsappNumber.lowercase().contains(q) ||
                u.email.lowercase().contains(q) ||
                u.positionTitle.lowercase().contains(q)
            matchRole && matchSearch
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Employés du Magasin & Accès",
                subtitle = "Noms, téléphones, WhatsApp, emails, postes, salaires et permissions",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (currentUser != null && onChangeOwnPassword != null) {
                            FilledTonalButton(
                                onClick = { showOwnPassDialog = true },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Mon mot de passe", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Button(
                            onClick = { showCreateDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("create_user_button")
                        ) {
                            Icon(Icons.Rounded.PersonAdd, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Nouvel employé", fontWeight = FontWeight.SemiBold)
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
                    title = "Effectif total",
                    value = "${users.size}",
                    subtitle = "$activeCount actif(s)",
                    icon = Icons.Rounded.Groups,
                    accentColor = ext.violetPrimary,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = "Masse salariale",
                    value = Formatters.fcfaCompact(totalPayroll),
                    subtitle = Formatters.fcfa(totalPayroll),
                    icon = Icons.Rounded.Payments,
                    accentColor = ext.successGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Rechercher nom, téléphone, WhatsApp, email, poste...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = roleFilter == "TOUS",
                        onClick = { roleFilter = "TOUS" },
                        shape = RoundedCornerShape(14.dp),
                        label = { Text("Tous (${users.size})", fontWeight = FontWeight.SemiBold) }
                    )
                    RoleCode.entries.forEach { r ->
                        val count = users.count { it.roleCode == r.code }
                        FilterChip(
                            selected = roleFilter == r.code,
                            onClick = { roleFilter = r.code },
                            shape = RoundedCornerShape(14.dp),
                            label = { Text("${r.displayName} ($count)", fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }
        }
        items(filteredUsers, key = { "usr_${it.id}" }) { user ->
            val grantedCount = userPermissions.count { it.userId == user.id && it.granted }
            PremiumSurfaceCard(modifier = Modifier.animateItem()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(user.fullName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                when (user.roleCode) {
                                    "ADMIN" -> GoldPremiumBadge("ADMIN")
                                    "MANAGER" -> StatusBadge("GESTIONNAIRE", ext.violetPrimary)
                                }
                            }
                            Text(
                                "@${user.username} · ${user.positionTitle.ifBlank { user.roleCode }} · Rôle ${user.roleCode}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = ext.violetPrimary
                            )
                        }
                        StatusBadge(
                            if (user.isActive) "ACTIF" else "RÉVOQUÉ",
                            if (user.isActive) ext.successGreen else MaterialTheme.colorScheme.error
                        )
                    }

                    HorizontalDivider(color = ext.cardBorder)

                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Phone, contentDescription = null, modifier = Modifier.size(16.dp), tint = ext.violetPrimary)
                            Text("Tél : ${user.phone.ifBlank { "Non renseigné" }}", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Chat, contentDescription = null, modifier = Modifier.size(16.dp), tint = ext.successGreen)
                            Text("WhatsApp : ${user.whatsappNumber.ifBlank { "Non renseigné" }}", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(16.dp), tint = ext.midnightSecondary)
                            Text("Email : ${user.email.ifBlank { "Non renseigné" }}", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Salaire : ${Formatters.fcfa(user.salaryFcfa)}",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "$grantedCount perm. · ${if (user.lastLoginAt > 0) "Vu ${Formatters.dateTime(user.lastLoginAt)}" else "Jamais connecté"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Quick contact actions
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (user.phone.isNotBlank()) {
                            AssistChip(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${user.phone.trim()}")))
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                leadingIcon = { Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Appeler", fontWeight = FontWeight.SemiBold) }
                            )
                        }
                        if (user.whatsappNumber.isNotBlank()) {
                            AssistChip(
                                onClick = {
                                    val digits = user.whatsappNumber.filter { it.isDigit() }
                                    if (digits.isNotEmpty()) {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")))
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                leadingIcon = { Icon(Icons.Rounded.Chat, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("WhatsApp", fontWeight = FontWeight.SemiBold) }
                            )
                        }
                        if (user.email.isNotBlank()) {
                            AssistChip(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${user.email.trim()}")))
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                leadingIcon = { Icon(Icons.Rounded.AlternateEmail, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Email", fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }

                    // Admin management actions
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = { editingProfileUser = user }, shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Modifier fiche", fontWeight = FontWeight.SemiBold)
                        }
                        FilledTonalButton(onClick = { editingPermsUser = user }, shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Permissions", fontWeight = FontWeight.SemiBold)
                        }
                        val canResetThisPass = currentUser != null && (
                            currentUser.id == user.id ||
                                currentUser.roleCode == "ADMIN" ||
                                (user.roleCode != "ADMIN")
                            )
                        if (canResetThisPass) {
                            OutlinedButton(
                                onClick = {
                                    if (currentUser?.id == user.id && onChangeOwnPassword != null) {
                                        showOwnPassDialog = true
                                    } else {
                                        resettingPassUser = user
                                    }
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Mot de passe", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (user.roleCode != "ADMIN" || users.count { it.roleCode == "ADMIN" && it.isActive } > 1) {
                            OutlinedButton(onClick = { onToggleActive(user) }, shape = RoundedCornerShape(14.dp)) {
                                Text(
                                    if (user.isActive) "Révoquer" else "Réactiver",
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (user.isActive) MaterialTheme.colorScheme.error else ext.successGreen
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var username by remember { mutableStateOf("") }
        var fullName by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var whatsapp by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var positionTitle by remember { mutableStateOf("") }
        var salaryStr by remember { mutableStateOf("") }
        var roleCode by remember { mutableStateOf(RoleCode.CASHIER.code) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            shape = RoundedCornerShape(18.dp),
            title = { Text("Nouvel employé du magasin", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Nom complet *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Identifiant connexion *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Mot de passe (min 4 car.) *") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(value = positionTitle, onValueChange = { positionTitle = it }, label = { Text("Poste / Fonction (ex: Caissier principal)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Téléphone") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = whatsapp,
                            onValueChange = { whatsapp = it },
                            label = { Text("Numéro WhatsApp") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { whatsapp = phone }) { Text("= Tél.") }
                    }
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Adresse Email") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = salaryStr,
                        onValueChange = { salaryStr = it },
                        label = { Text("Salaire mensuel (FCFA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Rôle système :", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RoleCode.entries.forEach { r ->
                            FilterChip(selected = roleCode == r.code, onClick = { roleCode = r.code }, label = { Text(r.displayName) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (username.isNotBlank() && fullName.isNotBlank() && password.length >= 4) {
                        onCreateUser(
                            username.trim(),
                            fullName.trim(),
                            password,
                            roleCode,
                            phone.trim(),
                            whatsapp.trim().ifBlank { phone.trim() },
                            email.trim(),
                            positionTitle.trim(),
                            salaryStr.toDoubleOrNull() ?: 0.0
                        )
                        showCreateDialog = false
                    }
                }) { Text("Enregistrer employé") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Annuler") } }
        )
    }

    editingProfileUser?.let { target ->
        var fullName by remember(target.id) { mutableStateOf(target.fullName) }
        var positionTitle by remember(target.id) { mutableStateOf(target.positionTitle) }
        var phone by remember(target.id) { mutableStateOf(target.phone) }
        var whatsapp by remember(target.id) { mutableStateOf(target.whatsappNumber) }
        var email by remember(target.id) { mutableStateOf(target.email) }
        var salaryStr by remember(target.id) { mutableStateOf(target.salaryFcfa.toInt().toString()) }
        var roleCode by remember(target.id) { mutableStateOf(target.roleCode) }
        var newPassword by remember(target.id) { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { editingProfileUser = null },
            title = { Text("Modifier fiche : ${target.fullName}") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Nom complet *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = positionTitle, onValueChange = { positionTitle = it }, label = { Text("Poste / Fonction") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Téléphone") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = whatsapp,
                            onValueChange = { whatsapp = it },
                            label = { Text("Numéro WhatsApp") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { whatsapp = phone }) { Text("= Tél.") }
                    }
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = salaryStr,
                        onValueChange = { salaryStr = it },
                        label = { Text("Salaire mensuel (FCFA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (target.roleCode != "ADMIN" || users.count { it.roleCode == "ADMIN" && it.isActive } > 1) {
                        Text("Rôle système :", style = MaterialTheme.typography.labelMedium)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RoleCode.entries.forEach { r ->
                                FilterChip(selected = roleCode == r.code, onClick = { roleCode = r.code }, label = { Text(r.displayName) })
                            }
                        }
                    }
                    var showEditPass by remember(target.id) { mutableStateOf(false) }
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Nouveau mot de passe (optionnel, min. 4 car.)") },
                        isError = newPassword.isNotEmpty() && newPassword.length < 4,
                        supportingText = if (newPassword.isNotEmpty() && newPassword.length < 4) {
                            { Text("Minimum 4 caractères requis") }
                        } else null,
                        visualTransformation = if (showEditPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showEditPass = !showEditPass }) {
                                Icon(
                                    if (showEditPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Afficher mot de passe"
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (fullName.isNotBlank()) {
                        onUpdateUserProfile(
                            target,
                            fullName.trim(),
                            phone.trim(),
                            whatsapp.trim(),
                            email.trim(),
                            positionTitle.trim(),
                            salaryStr.toDoubleOrNull() ?: target.salaryFcfa,
                            roleCode,
                            newPassword.takeIf { it.isNotBlank() }
                        )
                        editingProfileUser = null
                    }
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { editingProfileUser = null }) { Text("Annuler") } }
        )
    }

    editingPermsUser?.let { target ->
        val currentMap = userPermissions.filter { it.userId == target.id }.associate { it.permissionCode to it.granted }
        AlertDialog(
            onDismissRequest = { editingPermsUser = null },
            title = { Text("Permissions : ${target.fullName}") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppPermission.entries.forEach { perm ->
                        val granted = target.roleCode == "ADMIN" || (currentMap[perm.code] == true)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(perm.label, style = MaterialTheme.typography.bodyMedium)
                                Text(perm.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = granted,
                                enabled = target.roleCode != "ADMIN",
                                onCheckedChange = { checked -> onSetPermission(target, perm.code, checked) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { editingPermsUser = null }) { Text("Fermer") }
            }
        )
    }

    resettingPassUser?.let { target ->
        var newPass by remember(target.id) { mutableStateOf("") }
        var confirmPass by remember(target.id) { mutableStateOf("") }
        var showPass by remember { mutableStateOf(false) }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        var submitting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!submitting) resettingPassUser = null },
            shape = RoundedCornerShape(18.dp),
            icon = { Icon(Icons.Rounded.Key, contentDescription = null, tint = ext.violetPrimary) },
            title = { Text("Mot de passe · ${target.fullName}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Définir un nouveau mot de passe pour @${target.username}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it; errorMsg = null },
                        label = { Text("Nouveau mot de passe (min. 4 car.) *") },
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(
                                    if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Afficher mot de passe"
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPass,
                        onValueChange = { confirmPass = it; errorMsg = null },
                        label = { Text("Confirmer le mot de passe *") },
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!errorMsg.isNullOrBlank()) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                            Text(
                                errorMsg!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !submitting,
                    shape = RoundedCornerShape(14.dp),
                    onClick = {
                        when {
                            newPass.length < 4 -> errorMsg = "4 caractères minimum."
                            newPass != confirmPass -> errorMsg = "Les mots de passe ne correspondent pas."
                            else -> {
                                submitting = true
                                onResetUserPassword(target, newPass, confirmPass) { res ->
                                    submitting = false
                                    res.onSuccess { resettingPassUser = null }
                                        .onFailure { err -> errorMsg = err.message ?: "Erreur" }
                                }
                            }
                        }
                    }
                ) { Text(if (submitting) "Enregistrement..." else "Enregistrer") }
            },
            dismissButton = {
                TextButton(enabled = !submitting, onClick = { resettingPassUser = null }) { Text("Annuler") }
            }
        )
    }

    if (showOwnPassDialog && currentUser != null && onChangeOwnPassword != null) {
        ChangeOwnPasswordDialog(
            fullName = currentUser.fullName,
            username = currentUser.username,
            onDismiss = { showOwnPassDialog = false },
            onSubmit = onChangeOwnPassword
        )
    }
}

@Composable
fun SettingsBackupsScreen(
    settings: StoreSettingsEntity,
    currentUser: UserEntity?,
    canManageSettings: Boolean = true,
    canResetData: Boolean = true,
    onlinePaymentConfigs: List<OnlinePaymentConfigEntity> = emptyList(),
    backups: List<BackupRecordEntity>,
    cloudBackups: List<CloudBackup>,
    isSyncing: Boolean,
    cloudEmail: String,
    salesCount: Int = 0,
    invoicesCount: Int = 0,
    productsCount: Int = 0,
    customersCount: Int = 0,
    suppliersCount: Int = 0,
    usersCount: Int = 0,
    onSaveSettings: (StoreSettingsEntity) -> Unit,
    onSaveOnlinePaymentConfig: (OnlinePaymentConfigEntity, (Result<OnlinePaymentConfigEntity>) -> Unit) -> Unit = { _, cb -> cb(Result.success(OnlinePaymentConfigEntity(category = "CUSTOM_LINK", displayName = ""))) },
    onToggleOnlinePaymentConfig: (OnlinePaymentConfigEntity, Boolean) -> Unit = { _, _ -> },
    onDeleteOnlinePaymentConfig: (OnlinePaymentConfigEntity) -> Unit = {},
    onToggleOfflineMode: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onCreateBackup: (String) -> Unit,
    onRestoreBackupJson: (String) -> Unit,
    onChangeOwnPassword: (String, String, String, (Result<Unit>) -> Unit) -> Unit,
    onAdminReset: (String, AdminResetScope, Boolean, Boolean, (Result<String>) -> Unit) -> Unit,
    onSignOutGoogle: () -> Unit
) {
    val ext = LocalSmartStoreColors.current
    val context = LocalContext.current
    var showChangeOwnPass by remember { mutableStateOf(false) }
    var editingPaymentConfig by remember { mutableStateOf<OnlinePaymentConfigEntity?>(null) }
    var showNewPaymentConfigDialog by remember { mutableStateOf(false) }
    var selectedResetScope by remember { mutableStateOf(AdminResetScope.OPERATIONS_ONLY) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var storeName by remember(settings) { mutableStateOf(settings.storeName) }
    var selectedLogoKey by remember(settings) { mutableStateOf(settings.logoKey.ifBlank { "GENERAL" }) }
    var city by remember(settings) { mutableStateOf(settings.city) }
    var location by remember(settings) { mutableStateOf(settings.location) }
    var address by remember(settings) { mutableStateOf(settings.address) }
    var phone by remember(settings) { mutableStateOf(settings.phone) }
    var email by remember(settings) { mutableStateOf(settings.email) }
    var vatRateStr by remember(settings) { mutableStateOf(settings.vatRate.toString()) }
    var taxEnabled by remember(settings) { mutableStateOf(settings.taxEnabled) }
    var receiptFooter by remember(settings) { mutableStateOf(settings.receiptFooter) }
    var minStockStr by remember(settings) { mutableStateOf(settings.defaultMinStock.toString()) }
    var autoLogoutStr by remember(settings) { mutableStateOf(settings.autoLogoutMinutes.toString()) }
    var darkTheme by remember(settings) { mutableStateOf(settings.darkThemeEnabled) }

    val jsonImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val txt = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                if (!txt.isNullOrBlank()) onRestoreBackupJson(txt)
            }
        }
    }

    val jsonExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val latest = backups.firstOrNull()
        if (uri != null && latest != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(latest.jsonPayload.toByteArray(Charsets.UTF_8))
                }
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
                title = if (canManageSettings) "Paramètres, Sécurité & Réinitialisation" else "Sécurité & Réinitialisation",
                subtitle = "Compte Google : $cloudEmail"
            )
        }
        if (canManageSettings) {
        item {
            PremiumSurfaceCard {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StructureLogoBadge(logoKey = selectedLogoKey, size = 48.dp)
                        Column {
                            Text("Identité, Logo & Localisation", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(StructureCatalog.presetFor(selectedLogoKey).label, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = ext.violetPrimary)
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StructureCatalog.presets.forEach { preset ->
                            val isSelected = preset.key == selectedLogoKey
                            Surface(
                                onClick = { selectedLogoKey = preset.key },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) ext.violetPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) ext.violetPrimary else ext.cardBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    StructureLogoBadge(logoKey = preset.key, size = 30.dp)
                                    Text(preset.label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                }
                            }
                        }
                    }
                    OutlinedTextField(value = storeName, onValueChange = { storeName = it }, label = { Text("Nom de la structure / magasin") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("Ville (ex: Yaoundé, Douala)") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Lieu / Quartier (ex: Bastos)") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Adresse complète / Repère") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Téléphone") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email structure") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = receiptFooter, onValueChange = { receiptFooter = it }, label = { Text("Pied de ticket / facture") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = vatRateStr, onValueChange = { vatRateStr = it },
                            label = { Text("TVA (%)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minStockStr, onValueChange = { minStockStr = it },
                            label = { Text("Seuil défaut") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = autoLogoutStr, onValueChange = { autoLogoutStr = it },
                            label = { Text("Auto-lock (min)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Appliquer TVA (${vatRateStr}%) sur factures/POS", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = taxEnabled, onCheckedChange = { taxEnabled = it })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Thème sombre professionnel", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = darkTheme, onCheckedChange = { darkTheme = it })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mode hors-ligne (file locale)", style = MaterialTheme.typography.bodyMedium)
                            Text("Désactivé = synchro Firestore temps réel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = settings.offlineModeEnabled, onCheckedChange = onToggleOfflineMode)
                    }
                    Button(
                        onClick = {
                            val chosenPreset = StructureCatalog.presetFor(selectedLogoKey)
                            onSaveSettings(
                                settings.copy(
                                    storeName = storeName.trim(),
                                    structureType = chosenPreset.label,
                                    logoKey = chosenPreset.key,
                                    city = city.trim(),
                                    location = location.trim(),
                                    address = address.trim(),
                                    phone = phone.trim(),
                                    email = email.trim(),
                                    receiptFooter = receiptFooter,
                                    vatRate = vatRateStr.toDoubleOrNull() ?: settings.vatRate,
                                    taxEnabled = taxEnabled,
                                    defaultMinStock = minStockStr.toIntOrNull() ?: settings.defaultMinStock,
                                    autoLogoutMinutes = autoLogoutStr.toIntOrNull() ?: settings.autoLogoutMinutes,
                                    darkThemeEnabled = darkTheme
                                )
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_settings_button")
                    ) { Text("Enregistrer la configuration", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        item {
            PremiumSurfaceCard {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Payments, contentDescription = null, tint = ext.violetPrimary)
                            Column {
                                Text(
                                    "Passerelles & Paiements en ligne",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    "Paramétrez vos propres comptes marchands, clés API, numéros MoMo/OM, RIB ou liens de paiement",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (onlinePaymentConfigs.any { it.isEnabled }) {
                            GoldPremiumBadge("${onlinePaymentConfigs.count { it.isEnabled }} ACTIF(S)")
                        } else {
                            StatusBadge("VIDE", ext.midnightSecondary)
                        }
                    }

                    if (onlinePaymentConfigs.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, ext.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Aucun mode de paiement configuré pour cette entreprise. Ajoutez CinetPay, NotchPay, Orange Money, MTN MoMo, Compte Bancaire/Carte, Wave/PayPal/Stripe ou votre API personnalisée.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            onlinePaymentConfigs.forEach { gw ->
                                val gwType = OnlinePaymentGatewayType.fromCode(gw.category)
                                val summaryLine = buildList {
                                    add(gwType.label)
                                    if (gw.receiverPhone.isNotBlank()) add("Tél: ${gw.receiverPhone}")
                                    if (gw.siteOrMerchantId.isNotBlank()) add("ID/Compte: ${gw.siteOrMerchantId}")
                                    if (gw.apiKey.isNotBlank()) add("Clé: ••••${gw.apiKey.takeLast(4)}")
                                    if (gw.paymentBaseUrl.isNotBlank()) add(gw.paymentBaseUrl.take(34))
                                }.joinToString(" · ")
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, if (gw.isEnabled) ext.violetPrimary.copy(alpha = 0.4f) else ext.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    gw.displayName,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                                StatusBadge(
                                                    if (gw.isEnabled) "ACTIF" else "INACTIF",
                                                    if (gw.isEnabled) ext.successGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                summaryLine,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = gw.isEnabled,
                                            onCheckedChange = { checked -> onToggleOnlinePaymentConfig(gw, checked) }
                                        )
                                        IconButton(onClick = { editingPaymentConfig = gw }) {
                                            Icon(Icons.Rounded.Edit, contentDescription = "Modifier passerelle", tint = ext.violetPrimary)
                                        }
                                        IconButton(onClick = { onDeleteOnlinePaymentConfig(gw) }) {
                                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Supprimer passerelle", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { showNewPaymentConfigDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ext.violetPrimary),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_online_payment_config_button")
                    ) {
                        Icon(Icons.Rounded.AddCard, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ajouter un mode de paiement en ligne", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            PremiumSurfaceCard {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sauvegardes JSON & Synchro Cloud", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "Dernière synchro : ${if (settings.lastSyncAt > 0) Formatters.dateTimeWithSeconds(settings.lastSyncAt) else "Jamais"} · ${cloudBackups.size} sauvegarde(s) cloud",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onCreateBackup("Sauvegarde manuelle ${Formatters.dateTime(System.currentTimeMillis())}") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Créer sauvegarde", fontWeight = FontWeight.SemiBold) }
                        FilledTonalButton(onClick = onSyncNow, enabled = !isSyncing, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) {
                            Text(if (isSyncing) "Synchro..." else "Sync Cloud", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { jsonImportLauncher.launch(arrayOf("application/json", "*/*")) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) {
                            Text("Restaurer JSON", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { jsonExportLauncher.launch("smartstore_backup.json") },
                            enabled = backups.isNotEmpty(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Exporter JSON", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onSignOutGoogle) {
                            Icon(Icons.Rounded.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp))
                            Text("Déconnecter Google", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        }
        item {
            PremiumSurfaceCard {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.LockReset, contentDescription = null, tint = ext.violetPrimary)
                            Column {
                                Text("Mon compte & Mot de passe", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    currentUser?.let { "${it.fullName} (@${it.username}) · ${it.roleCode}" } ?: "Session active",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        when (currentUser?.roleCode) {
                            "ADMIN" -> GoldPremiumBadge("ADMIN")
                            "MANAGER" -> StatusBadge("GESTIONNAIRE", ext.violetPrimary)
                            else -> currentUser?.let { StatusBadge(it.roleCode, ext.midnightSecondary) }
                        }
                    }
                    Text(
                        "Modifiez votre mot de passe personnel à tout moment en confirmant votre mot de passe actuel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { showChangeOwnPass = true },
                        enabled = currentUser != null,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("change_own_password_button")
                    ) {
                        Icon(Icons.Rounded.Key, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Changer mon mot de passe", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            val isAdmin = currentUser?.roleCode == RoleCode.ADMIN.code
            val canPerformAnyReset = isAdmin || canResetData
            PremiumSurfaceCard {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Column {
                                Text(
                                    if (isAdmin) "Zone Administrateur · Réinitialisation" else "Gestionnaire · Réinitialisation des données",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    "$salesCount ventes · $invoicesCount factures · $productsCount produits · ${customersCount + suppliersCount} partenaires · $usersCount employés",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        StatusBadge(
                            when {
                                isAdmin -> "ACCÈS TOTAL"
                                canResetData -> "GESTIONNAIRE"
                                else -> "VERROUILLÉ"
                            },
                            if (canPerformAnyReset) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!canPerformAnyReset) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                "La réinitialisation des données est réservée à l'Administrateur principal ou à un Gestionnaire autorisé.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        Text(
                            "Choisissez le périmètre à remettre à zéro. Une confirmation par votre mot de passe est exigée.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AdminResetScope.entries.forEach { scope ->
                            val allowedForRole = isAdmin || scope != AdminResetScope.FULL_FACTORY_RESET
                            val selected = selectedResetScope == scope
                            val danger = scope == AdminResetScope.FULL_FACTORY_RESET
                            val accent = if (danger) MaterialTheme.colorScheme.error else ext.violetPrimary
                            Surface(
                                onClick = { if (allowedForRole) selectedResetScope = scope },
                                enabled = allowedForRole,
                                shape = RoundedCornerShape(14.dp),
                                color = when {
                                    !allowedForRole -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    selected -> accent.copy(alpha = 0.08f)
                                    else -> MaterialTheme.colorScheme.surface
                                },
                                border = BorderStroke(
                                    width = if (selected && allowedForRole) 2.dp else 1.dp,
                                    color = if (selected && allowedForRole) accent else ext.cardBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RadioButton(
                                        selected = selected && allowedForRole,
                                        enabled = allowedForRole,
                                        onClick = { if (allowedForRole) selectedResetScope = scope }
                                    )
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            if (allowedForRole) scope.title else "${scope.title} (Admin requis)",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = when {
                                                !allowedForRole -> MaterialTheme.colorScheme.onSurfaceVariant
                                                danger && selected -> MaterialTheme.colorScheme.error
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        Text(
                                            scope.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        Button(
                            onClick = { showResetConfirm = true },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedResetScope == AdminResetScope.FULL_FACTORY_RESET) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    ext.midnightSecondary
                                }
                            ),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("open_admin_reset_button")
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Réinitialiser : ${selectedResetScope.title}", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        if (canManageSettings) {
        items(backups, key = { "bkp_${it.id}" }) { bkp ->
            PremiumSurfaceCard(modifier = Modifier.animateItem()) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(bkp.label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text("${bkp.type} · ${bkp.recordCount} enreg. · ${(bkp.sizeBytes / 1024).coerceAtLeast(1)} Ko · ${Formatters.dateTime(bkp.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { onRestoreBackupJson(bkp.jsonPayload) }) {
                        Text("Restaurer", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        }
    }

    if (showChangeOwnPass && currentUser != null) {
        ChangeOwnPasswordDialog(
            fullName = currentUser.fullName,
            username = currentUser.username,
            onDismiss = { showChangeOwnPass = false },
            onSubmit = onChangeOwnPassword
        )
    }

    if (showResetConfirm) {
        val isAdminActor = currentUser?.roleCode == RoleCode.ADMIN.code
        var adminPassword by remember(selectedResetScope) { mutableStateOf("") }
        var showAdminPass by remember { mutableStateOf(false) }
        var createBackupFirst by remember(selectedResetScope) {
            mutableStateOf(selectedResetScope != AdminResetScope.FULL_FACTORY_RESET)
        }
        var alsoCleanCloud by remember(selectedResetScope) { mutableStateOf(false) }
        var resetError by remember { mutableStateOf<String?>(null) }
        var isResetting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isResetting) showResetConfirm = false },
            shape = RoundedCornerShape(18.dp),
            icon = { Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Confirmer : ${selectedResetScope.title}", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        selectedResetScope.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (selectedResetScope != AdminResetScope.FULL_FACTORY_RESET) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Créer sauvegarde JSON avant reset", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Switch(checked = createBackupFirst, onCheckedChange = { createBackupFirst = it })
                        }
                    } else if (!settings.offlineModeEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Purger aussi profil, produits & sauvegardes Cloud", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "L'historique immuable des ventes et audits Cloud reste protégé par Firestore.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = alsoCleanCloud, onCheckedChange = { alsoCleanCloud = it })
                        }
                    }
                    OutlinedTextField(
                        value = adminPassword,
                        onValueChange = { adminPassword = it; resetError = null },
                        label = { Text("Mot de passe ${if (isAdminActor) "administrateur" else "gestionnaire"} *") },
                        visualTransformation = if (showAdminPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showAdminPass = !showAdminPass }) {
                                Icon(
                                    if (showAdminPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Afficher mot de passe"
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("admin_reset_password_input")
                    )
                    if (!resetError.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                resetError!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isResetting,
                    onClick = {
                        if (adminPassword.isBlank()) {
                            resetError = "Mot de passe administrateur requis."
                            return@Button
                        }
                        isResetting = true
                        onAdminReset(adminPassword, selectedResetScope, createBackupFirst, alsoCleanCloud) { res ->
                            isResetting = false
                            res.onSuccess { showResetConfirm = false }
                                .onFailure { err -> resetError = err.message ?: "Échec de la réinitialisation." }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_admin_reset_button")
                ) {
                    Text(if (isResetting) "Réinitialisation..." else "Confirmer le reset", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(enabled = !isResetting, onClick = { showResetConfirm = false }) { Text("Annuler") }
            }
        )
    }

    val targetConfig = editingPaymentConfig ?: if (showNewPaymentConfigDialog) {
        OnlinePaymentConfigEntity(
            category = OnlinePaymentGatewayType.ORANGE_MONEY.code,
            displayName = ""
        )
    } else null

    if (targetConfig != null) {
        OnlinePaymentConfigDialog(
            initial = targetConfig,
            onDismiss = {
                showNewPaymentConfigDialog = false
                editingPaymentConfig = null
            },
            onSave = onSaveOnlinePaymentConfig
        )
    }
}

@Composable
fun OnlinePaymentConfigDialog(
    initial: OnlinePaymentConfigEntity,
    onDismiss: () -> Unit,
    onSave: (OnlinePaymentConfigEntity, (Result<OnlinePaymentConfigEntity>) -> Unit) -> Unit
) {
    val ext = LocalSmartStoreColors.current
    val isEditing = initial.id > 0L
    var category by remember(initial) { mutableStateOf(OnlinePaymentGatewayType.fromCode(initial.category)) }
    var displayName by remember(initial) { mutableStateOf(initial.displayName) }
    var apiKey by remember(initial) { mutableStateOf(initial.apiKey) }
    var secretOrToken by remember(initial) { mutableStateOf(initial.secretOrToken) }
    var siteOrMerchantId by remember(initial) { mutableStateOf(initial.siteOrMerchantId) }
    var receiverPhone by remember(initial) { mutableStateOf(initial.receiverPhone) }
    var accountHolderName by remember(initial) { mutableStateOf(initial.accountHolderName) }
    var ussdPattern by remember(initial) { mutableStateOf(initial.ussdPattern) }
    var paymentBaseUrl by remember(initial) { mutableStateOf(initial.paymentBaseUrl) }
    var callbackUrl by remember(initial) { mutableStateOf(initial.callbackUrl) }
    var verifyEndpointUrl by remember(initial) { mutableStateOf(initial.verifyEndpointUrl) }
    var instructions by remember(initial) { mutableStateOf(initial.instructions) }
    var isEnabled by remember(initial) { mutableStateOf(initial.isEnabled) }
    var showSecrets by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        shape = RoundedCornerShape(18.dp),
        icon = { Icon(Icons.Rounded.Payments, contentDescription = null, tint = ext.violetPrimary) },
        title = {
            Text(
                if (isEditing) "Modifier le paiement en ligne" else "Nouveau paiement en ligne",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Type de passerelle / paiement :", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OnlinePaymentGatewayType.entries.forEach { gwType ->
                        FilterChip(
                            selected = category == gwType,
                            onClick = {
                                category = gwType
                                errorMsg = null
                                if (!isEditing && displayName.isBlank()) {
                                    displayName = gwType.label
                                }
                            },
                            label = { Text("${gwType.label} — ${gwType.shortHint}", maxLines = 2) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it; errorMsg = null },
                    label = { Text("Nom affiché en caisse *") },
                    placeholder = { Text("Ex: Orange Money Entreprise, Wave, CinetPay...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("payment_config_name_input")
                )

                when (category) {
                    OnlinePaymentGatewayType.CINETPAY -> {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it; errorMsg = null },
                            label = { Text("CinetPay API Key *") },
                            visualTransformation = if (showSecrets) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showSecrets = !showSecrets }) {
                                    Icon(if (showSecrets) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = "Afficher clé")
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("payment_config_apikey_input")
                        )
                        OutlinedTextField(
                            value = siteOrMerchantId,
                            onValueChange = { siteOrMerchantId = it; errorMsg = null },
                            label = { Text("CinetPay Site ID *") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("payment_config_site_input")
                        )
                        OutlinedTextField(
                            value = callbackUrl,
                            onValueChange = { callbackUrl = it },
                            label = { Text("URL Callback / Notify (optionnel)") },
                            placeholder = { Text("https://votre-domaine.com/cinetpay/notify") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OnlinePaymentGatewayType.NOTCHPAY -> {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it; errorMsg = null },
                            label = { Text("NotchPay Public Key *") },
                            visualTransformation = if (showSecrets) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showSecrets = !showSecrets }) {
                                    Icon(if (showSecrets) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = "Afficher clé")
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("payment_config_apikey_input")
                        )
                        OutlinedTextField(
                            value = secretOrToken,
                            onValueChange = { secretOrToken = it },
                            label = { Text("Clé privée / Webhook Hash (optionnel)") },
                            visualTransformation = if (showSecrets) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = callbackUrl,
                            onValueChange = { callbackUrl = it },
                            label = { Text("URL de retour / Callback (optionnel)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OnlinePaymentGatewayType.ORANGE_MONEY,
                    OnlinePaymentGatewayType.MTN_MOMO -> {
                        OutlinedTextField(
                            value = receiverPhone,
                            onValueChange = { receiverPhone = it; errorMsg = null },
                            label = { Text("Numéro marchand / téléphone récepteur *") },
                            placeholder = { Text("Ex: +237 6XX XXX XXX") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("payment_config_phone_input")
                        )
                        OutlinedTextField(
                            value = siteOrMerchantId,
                            onValueChange = { siteOrMerchantId = it },
                            label = { Text("Code marchand (optionnel)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = accountHolderName,
                            onValueChange = { accountHolderName = it },
                            label = { Text("Nom du titulaire / entreprise") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = ussdPattern,
                            onValueChange = { ussdPattern = it },
                            label = { Text("Modèle USSD (optionnel, ex: #150*14*{merchant}*{amount}#)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OnlinePaymentGatewayType.BANK_CARD -> {
                        OutlinedTextField(
                            value = siteOrMerchantId,
                            onValueChange = { siteOrMerchantId = it; errorMsg = null },
                            label = { Text("IBAN / RIB / Numéro TPE *") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("payment_config_site_input")
                        )
                        OutlinedTextField(
                            value = accountHolderName,
                            onValueChange = { accountHolderName = it },
                            label = { Text("Titulaire / Banque") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = paymentBaseUrl,
                            onValueChange = { paymentBaseUrl = it },
                            label = { Text("Lien de paiement carte en ligne (optionnel)") },
                            placeholder = { Text("https://...") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OnlinePaymentGatewayType.CUSTOM_LINK -> {
                        OutlinedTextField(
                            value = paymentBaseUrl,
                            onValueChange = { paymentBaseUrl = it; errorMsg = null },
                            label = { Text("URL de paiement (Wave, PayPal, Stripe...) *") },
                            placeholder = { Text("https://exemple.com/pay?amount={amount}&ref={ref}") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("payment_config_url_input")
                        )
                        OutlinedTextField(
                            value = accountHolderName,
                            onValueChange = { accountHolderName = it },
                            label = { Text("Nom du bénéficiaire (optionnel)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = receiverPhone,
                            onValueChange = { receiverPhone = it },
                            label = { Text("Téléphone support (optionnel)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OnlinePaymentGatewayType.CUSTOM_API -> {
                        OutlinedTextField(
                            value = paymentBaseUrl,
                            onValueChange = { paymentBaseUrl = it; errorMsg = null },
                            label = { Text("Endpoint API d'initiation (POST JSON) *") },
                            placeholder = { Text("https://api.votre-passerelle.com/v1/checkout") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("payment_config_url_input")
                        )
                        OutlinedTextField(
                            value = verifyEndpointUrl,
                            onValueChange = { verifyEndpointUrl = it },
                            label = { Text("Endpoint API de vérification (GET, optionnel)") },
                            placeholder = { Text("https://api.votre-passerelle.com/v1/status/{ref}") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("Clé API / Bearer Token") },
                            visualTransformation = if (showSecrets) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showSecrets = !showSecrets }) {
                                    Icon(if (showSecrets) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = "Afficher clé")
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("payment_config_apikey_input")
                        )
                        OutlinedTextField(
                            value = secretOrToken,
                            onValueChange = { secretOrToken = it },
                            label = { Text("Secret API (X-Api-Secret, optionnel)") },
                            visualTransformation = if (showSecrets) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = siteOrMerchantId,
                            onValueChange = { siteOrMerchantId = it },
                            label = { Text("Merchant ID / Store ID (optionnel)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = callbackUrl,
                            onValueChange = { callbackUrl = it },
                            label = { Text("URL Webhook / Callback (optionnel)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Instructions pour la caisse / le client (optionnel)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Activer ce mode de paiement", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                }

                if (!errorMsg.isNullOrBlank()) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                        Text(
                            errorMsg!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving,
                shape = RoundedCornerShape(14.dp),
                onClick = {
                    saving = true
                    onSave(
                        initial.copy(
                            category = category.code,
                            displayName = displayName.trim(),
                            isEnabled = isEnabled,
                            apiKey = apiKey.trim(),
                            secretOrToken = secretOrToken.trim(),
                            siteOrMerchantId = siteOrMerchantId.trim(),
                            receiverPhone = receiverPhone.trim(),
                            accountHolderName = accountHolderName.trim(),
                            ussdPattern = ussdPattern.trim(),
                            paymentBaseUrl = paymentBaseUrl.trim(),
                            callbackUrl = callbackUrl.trim(),
                            verifyEndpointUrl = verifyEndpointUrl.trim(),
                            instructions = instructions.trim()
                        )
                    ) { res ->
                        saving = false
                        res.onSuccess { onDismiss() }
                            .onFailure { err -> errorMsg = err.message ?: "Erreur d'enregistrement." }
                    }
                },
                modifier = Modifier.testTag("save_payment_config_button")
            ) {
                Text(if (saving) "Enregistrement..." else "Enregistrer", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
fun AuditAlertsScreen(
    auditLogs: List<AuditLogEntity>,
    cloudAuditLogs: List<CloudAuditLog>,
    notifications: List<AlertNotificationEntity>,
    onMarkRead: (Long) -> Unit,
    onMarkAllRead: () -> Unit
) {
    val ext = LocalSmartStoreColors.current
    var tab by remember { mutableIntStateOf(0) } // 0 = Alertes, 1 = Audit Local, 2 = Audit Cloud

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Alertes & Journal d'audit immuable",
                subtitle = "Traçabilité complète : ventes, stock, caisse, connexions, accès refusés",
                action = {
                    if (tab == 0 && notifications.any { !it.isRead }) {
                        TextButton(onClick = onMarkAllRead) { Text("Tout marquer lu", fontWeight = FontWeight.SemiBold) }
                    }
                }
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, shape = RoundedCornerShape(14.dp), label = { Text("Alertes (${notifications.size})", fontWeight = FontWeight.SemiBold) })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, shape = RoundedCornerShape(14.dp), label = { Text("Audit Local (${auditLogs.size})", fontWeight = FontWeight.SemiBold) })
                FilterChip(selected = tab == 2, onClick = { tab = 2 }, shape = RoundedCornerShape(14.dp), label = { Text("Audit Cloud (${cloudAuditLogs.size})", fontWeight = FontWeight.SemiBold) })
            }
        }
        when (tab) {
            0 -> items(notifications, key = { "adm_alert_${it.id}" }) { alert ->
                val sevColor = when (alert.severity) {
                    "CRITICAL" -> MaterialTheme.colorScheme.error
                    "WARNING" -> ext.goldOnSoft
                    else -> ext.violetPrimary
                }
                PremiumSurfaceCard(
                    modifier = Modifier.animateItem(),
                    onClick = { onMarkRead(alert.id) }
                ) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(alert.type, sevColor)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(alert.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text(alert.message, style = MaterialTheme.typography.bodySmall)
                            Text(Formatters.dateTimeWithSeconds(alert.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!alert.isRead) {
                            Icon(Icons.Rounded.FiberManualRecord, contentDescription = "Non lu", tint = sevColor, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
            1 -> items(auditLogs, key = { "adm_audit_${it.id}" }) { log ->
                PremiumSurfaceCard(modifier = Modifier.animateItem()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            StatusBadge(log.module, ext.midnightSecondary)
                            Text(Formatters.dateTimeWithSeconds(log.createdAt), style = MaterialTheme.typography.labelSmall.copy(fontFamily = InterFontFamily))
                        }
                        Text(log.action, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text("${log.oldValue} → ${log.newValue} · ${log.deviceInfo}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            else -> itemsIndexed(cloudAuditLogs, key = { idx, clog -> "adm_cloud_${idx}_${clog.id}" }) { _, clog ->
                PremiumSurfaceCard(modifier = Modifier.animateItem()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            StatusBadge("CLOUD · ${clog.module}", ext.successGreen)
                            Text(clog.staffName, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                        }
                        Text(clog.action, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text(clog.details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
