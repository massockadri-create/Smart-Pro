package com.example.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

@IgnoreExtraProperties
data class CloudStoreProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val storeName: String = "",
    val city: String = "",
    val location: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val structureType: String = "GENERAL",
    val logoKey: String = "GENERAL",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudProduct(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val name: String = "",
    val sku: String = "",
    val barcode: String = "",
    val categoryName: String = "",
    val purchasePrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val stockQuantity: Long = 0L,
    val minThreshold: Long = 0L,
    val unit: String = "Unité",
    val status: String = "ACTIF",
    val clientUpdatedAt: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudSale(
    @DocumentId val id: String = "",
    val userId: String = "",
    val invoiceNumber: String = "",
    val customerName: String = "",
    val cashierName: String = "",
    val totalAmount: Double = 0.0,
    val profit: Double = 0.0,
    val paymentMethod: String = "",
    val status: String = "VALIDEE",
    val itemCount: Long = 1L,
    val clientCreatedAt: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudAuditLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val staffName: String = "",
    val action: String = "",
    val module: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudBackup(
    @DocumentId val id: String = "",
    val userId: String = "",
    val label: String = "",
    val backupType: String = "MANUELLE",
    val recordCount: Long = 0L,
    val summary: String = "",
    val payloadJson: String = "{}",
    val createdByName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
