package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.local.AuditLogEntity
import com.example.data.local.BackupRecordEntity
import com.example.data.local.ProductEntity
import com.example.data.local.SaleEntity
import com.example.data.local.StoreSettingsEntity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CloudSyncRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun sanitizeDocId(raw: String): String {
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return if (cleaned.isBlank()) "doc_${System.currentTimeMillis()}" else cleaned.take(120)
    }

    fun observeCloudStoreProfile(userId: String): Flow<CloudStoreProfile?> =
        db.collection("users").document(userId).collection("sync_store_profile")
            .document("primary")
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(
                    CloudStoreProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    suspend fun upsertStoreProfile(userId: String, settings: StoreSettingsEntity): Result<String> {
        val docId = "primary"
        val docRef = db.collection("users").document(userId).collection("sync_store_profile").document(docId)
        return try {
            val existing = docRef.get().await()
            val safeName = settings.storeName.ifBlank { "Structure" }.take(160)
            if (existing.exists()) {
                val updates = mapOf(
                    "storeName" to safeName,
                    "city" to settings.city.take(100),
                    "location" to settings.location.take(160),
                    "address" to settings.address.take(200),
                    "phone" to settings.phone.take(64),
                    "email" to settings.email.take(120),
                    "structureType" to settings.structureType.ifBlank { "GENERAL" }.take(40),
                    "logoKey" to settings.logoKey.ifBlank { "GENERAL" }.take(40),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            } else {
                val payload = mapOf(
                    "userId" to userId,
                    "storeName" to safeName,
                    "city" to settings.city.take(100),
                    "location" to settings.location.take(160),
                    "address" to settings.address.take(200),
                    "phone" to settings.phone.take(64),
                    "email" to settings.email.take(120),
                    "structureType" to settings.structureType.ifBlank { "GENERAL" }.take(40),
                    "logoKey" to settings.logoKey.ifBlank { "GENERAL" }.take(40),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeCloudProducts(userId: String): Flow<List<CloudProduct>> =
        db.collection("users").document(userId).collection("sync_products")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudProduct::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    fun observeCloudSales(userId: String): Flow<List<CloudSale>> =
        db.collection("users").document(userId).collection("sync_sales")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudSale::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    fun observeCloudAuditLogs(userId: String): Flow<List<CloudAuditLog>> =
        db.collection("users").document(userId).collection("sync_audit_logs")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudAuditLog::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    fun observeCloudBackups(userId: String): Flow<List<CloudBackup>> =
        db.collection("users").document(userId).collection("sync_backups")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudBackup::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    /** Supprime un document produit du cloud à partir de son identifiant (ex. produit créé sur PC). */
    suspend fun deleteProductDoc(userId: String, docId: String): Result<Unit> {
        val docRef = db.collection("users").document(userId).collection("sync_products").document(sanitizeDocId(docId))
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun upsertProduct(userId: String, product: ProductEntity): Result<String> {
        val docId = sanitizeDocId("prod_${product.id}_${product.sku}")
        val docRef = db.collection("users").document(userId).collection("sync_products").document(docId)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updates = mapOf(
                    "name" to product.name.take(160),
                    "sku" to product.sku.take(64),
                    "barcode" to product.barcode.take(64),
                    "categoryName" to product.categoryName.take(80),
                    "purchasePrice" to product.purchasePrice,
                    "sellingPrice" to product.sellingPrice,
                    "stockQuantity" to product.stockQuantity.coerceAtLeast(0).toLong(),
                    "minThreshold" to product.minThreshold.coerceAtLeast(0).toLong(),
                    "unit" to product.unit.take(32),
                    "status" to product.status,
                    "clientUpdatedAt" to product.updatedAt.coerceAtLeast(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            } else {
                val payload = mapOf(
                    "userId" to userId,
                    "localId" to product.id,
                    "name" to product.name.take(160),
                    "sku" to product.sku.take(64),
                    "barcode" to product.barcode.take(64),
                    "categoryName" to product.categoryName.take(80),
                    "purchasePrice" to product.purchasePrice,
                    "sellingPrice" to product.sellingPrice,
                    "stockQuantity" to product.stockQuantity.coerceAtLeast(0).toLong(),
                    "minThreshold" to product.minThreshold.coerceAtLeast(0).toLong(),
                    "unit" to product.unit.take(32),
                    "status" to product.status,
                    "clientUpdatedAt" to product.updatedAt.coerceAtLeast(1L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteProduct(userId: String, product: ProductEntity): Result<Unit> {
        val docId = sanitizeDocId("prod_${product.id}_${product.sku}")
        val docRef = db.collection("users").document(userId).collection("sync_products").document(docId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun upsertSale(userId: String, sale: SaleEntity, itemCount: Int, items: List<com.example.data.local.SaleItemEntity> = emptyList()): Result<String> {
        val docId = sanitizeDocId("sale_${sale.invoiceNumber}")
        val docRef = db.collection("users").document(userId).collection("sync_sales").document(docId)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val currentStatus = existing.getString("status") ?: "VALIDEE"
                if (currentStatus != "ANNULEE" && sale.status == "ANNULEE") {
                    docRef.update(
                        mapOf(
                            "status" to "ANNULEE",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } else {
                val payload = mapOf(
                    "userId" to userId,
                    "invoiceNumber" to sale.invoiceNumber.take(64),
                    "customerName" to sale.customerName.take(120),
                    "cashierName" to sale.userName.ifBlank { "Caissier" }.take(120),
                    "totalAmount" to sale.totalAmount.coerceAtLeast(0.0),
                    "profit" to sale.profit,
                    "paymentMethod" to sale.paymentMethod.ifBlank { "Espèces" }.take(64),
                    "status" to sale.status,
                    "itemCount" to itemCount.coerceAtLeast(1).toLong(),
                    "clientCreatedAt" to sale.createdAt.coerceAtLeast(1L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val itemsPayload = items.take(200).map {
                    mapOf<String, Any>(
                        "pid" to sanitizeDocId("prod_${it.productId}_${it.sku}"),
                        "name" to it.productName.take(120),
                        "sku" to it.sku.take(64),
                        "qty" to it.quantity.toLong(),
                        "price" to it.unitPrice,
                        "cost" to it.purchasePrice
                    )
                }
                docRef.set(if (itemsPayload.isEmpty()) payload else payload + ("items" to itemsPayload)).await()
            }
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun cancelSale(userId: String, invoiceNumber: String): Result<Unit> {
        val docId = sanitizeDocId("sale_$invoiceNumber")
        val docRef = db.collection("users").document(userId).collection("sync_sales").document(docId)
        return try {
            val existing = docRef.get().await()
            if (existing.exists() && existing.getString("status") != "ANNULEE") {
                docRef.update(
                    mapOf(
                        "status" to "ANNULEE",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun pushAuditLog(userId: String, log: AuditLogEntity): Result<String> {
        val docId = sanitizeDocId("log_${log.id}_${log.createdAt}")
        val docRef = db.collection("users").document(userId).collection("sync_audit_logs").document(docId)
        return try {
            val payload = mapOf(
                "userId" to userId,
                "staffName" to log.userName.ifBlank { "Système" }.take(120),
                "action" to log.action.take(300),
                "module" to log.module.ifBlank { "Général" }.take(80),
                "details" to "${log.oldValue} -> ${log.newValue}".take(500),
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun pushBackup(userId: String, backup: BackupRecordEntity, summary: String): Result<String> {
        val docId = sanitizeDocId("bkp_${backup.id}_${backup.createdAt}")
        val docRef = db.collection("users").document(userId).collection("sync_backups").document(docId)
        return try {
            val payload = mapOf(
                "userId" to userId,
                "label" to backup.label.take(160),
                "backupType" to backup.type,
                "recordCount" to backup.recordCount.coerceAtLeast(0).toLong(),
                "summary" to summary.take(400),
                "payloadJson" to backup.jsonPayload.take(650_000),
                "createdByName" to backup.createdByName.ifBlank { "Admin" }.take(120),
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteBackup(userId: String, backupDocId: String): Result<Unit> {
        val docRef = db.collection("users").document(userId).collection("sync_backups").document(sanitizeDocId(backupDocId))
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getProductById(userId: String, docId: String): CloudProduct? {
        val docRef = db.collection("users").document(userId).collection("sync_products").document(sanitizeDocId(docId))
        return try {
            docRef.get().await().toObject(
                CloudProduct::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    /**
     * Deletes user-owned mutable collections permitted by Firestore security rules
     * (sync_products, sync_backups, sync_store_profile). Immutable sync_sales and sync_audit_logs
     * remain preserved in Firestore by design.
     */
    suspend fun clearDeletableStoreData(userId: String): Result<Int> {
        return try {
            var deleted = 0
            val userDoc = db.collection("users").document(userId)
            val collections = listOf("sync_products", "sync_backups", "sync_store_profile")
            for (col in collections) {
                val snap = userDoc.collection(col).get().await()
                for (doc in snap.documents) {
                    doc.reference.delete().await()
                    deleted++
                }
            }
            Result.success(deleted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$userId")
            Result.failure(e)
        }
    }
}
