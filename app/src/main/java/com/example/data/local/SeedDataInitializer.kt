package com.example.data.local

import androidx.room.withTransaction
import com.example.security.AppPermission
import com.example.security.PasswordHasher
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

            val salt = PasswordHasher.generateSalt()
            val hash = PasswordHasher.hashPassword("mass22", salt)
            val uid = userDao.insertUser(
                UserEntity(
                    username = "admin",
                    fullName = "Administrateur",
                    passwordHash = hash,
                    passwordSalt = salt,
                    roleCode = RoleCode.ADMIN.code,
                    isActive = true,
                    lastLoginAt = now,
                    createdAt = now
                )
            )
            val defaults = AppPermission.defaultPermissionsForRole(RoleCode.ADMIN.code)
            userDao.insertUserPermissions(
                AppPermission.entries.map { perm ->
                    UserPermissionEntity(uid, perm.code, perm.code in defaults)
                }
            )

            val catalogDao = db.catalogDao()
            catalogDao.insertCategory(CategoryEntity(name = "Boissons", subCategory = "", colorHex = "#06B6D4"))
            catalogDao.insertCategory(CategoryEntity(name = "Aliments", subCategory = "", colorHex = "#10B981"))
            catalogDao.insertCategory(CategoryEntity(name = "Produits ménagers", subCategory = "", colorHex = "#F59E0B"))
            catalogDao.insertCategory(CategoryEntity(name = "Accessoires", subCategory = "", colorHex = "#8B5CF6"))
            catalogDao.insertCategory(CategoryEntity(name = "Autres produits", subCategory = "", colorHex = "#EC4899"))

            catalogDao.insertCustomer(
                CustomerEntity(
                    name = "Client Comptant",
                    phone = "",
                    address = "Vente au comptoir",
                    totalPurchased = 0.0,
                    creditAmount = 0.0,
                                     balance = 0.0,
                    lastTransactionAt = now,
                    notes = "Client anonyme au comptoir"
                )
            )
        }
    }
}   
