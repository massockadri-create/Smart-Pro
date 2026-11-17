package com.example.data.local

import androidx.room.withTransaction
import com.example.security.AppPermission
import com.example.security.RoleCode

object SeedDataInitializer {

    suspend fun seedIfNeeded(db: AppDatabase) {
        val userDao = db.userDao()
        if (userDao.getAllUsersOnce().isNotEmpty()) return

        val now = System.currentTimeMillis()

        db.withTransaction {
            db.adminDao().upsertSettings(
                StoreSettingsEntity(
                    id = 1,
                    storeName = "Mon Magasin",
                    slogan = "",
                    address = "",
                    phone = "",
                    email = "",
                    currency = "FCFA (XAF)",
                    vatRate = 19.25,
                    taxEnabled = false,
                    receiptFormat = "80mm Thermique",
                    receiptFooter = "Merci pour votre achat !",
                    invoicePrefix = "FAC",
                    nextInvoiceNumber = 1,
                    defaultMinStock = 10,
                    enabledPaymentMethods = "Espèces,Mobile Money,Orange Money,MTN MoMo,Carte,Virement,Autre",
                    autoLogoutMinutes = 30,
                    autoBackupEnabled = true,
                    offlineModeEnabled = false,
                    darkThemeEnabled = true,
                    lastSyncAt = now
                )
            )

            userDao.insertRoles(
                RoleCode.entries.map { RoleEntity(it.code, it.displayName, it.description) }
            )
            userDao.insertPermissions(
                AppPermission.entries.map { PermissionEntity(it.code, it.label, it.category) }
            )
            // Base vierge : aucun compte, catégorie, client ni donnée de démonstration.
            // Le premier utilisateur crée sa structure et son compte administrateur à l'écran de démarrage.
        }
    }
}
