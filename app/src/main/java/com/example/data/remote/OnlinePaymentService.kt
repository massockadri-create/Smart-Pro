package com.example.data.remote

import com.example.BuildConfig
import com.example.data.local.OnlinePaymentConfigEntity
import com.example.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

enum class OnlinePaymentGatewayType(
    val code: String,
    val label: String,
    val shortHint: String
) {
    CINETPAY(
        code = "CINETPAY",
        label = "CinetPay API",
        shortHint = "Clé API + Site ID de l'entreprise"
    ),
    NOTCHPAY(
        code = "NOTCHPAY",
        label = "NotchPay API",
        shortHint = "Clé publique NotchPay de l'entreprise"
    ),
    ORANGE_MONEY(
        code = "ORANGE_MONEY",
        label = "Orange Money Direct",
        shortHint = "Numéro marchand / téléphone & code USSD"
    ),
    MTN_MOMO(
        code = "MTN_MOMO",
        label = "MTN Mobile Money",
        shortHint = "Numéro marchand / téléphone & code USSD"
    ),
    BANK_CARD(
        code = "BANK_CARD",
        label = "Carte / Virement Bancaire",
        shortHint = "IBAN / RIB / TPE ou lien bancaire"
    ),
    CUSTOM_LINK(
        code = "CUSTOM_LINK",
        label = "Lien de paiement externe",
        shortHint = "Wave, PayPal, Stripe, Flutterwave, Kkiapay..."
    ),
    CUSTOM_API(
        code = "CUSTOM_API",
        label = "Passerelle API personnalisée",
        shortHint = "Endpoint REST JSON + clé / token Bearer"
    );

    companion object {
        fun fromCode(code: String): OnlinePaymentGatewayType =
            entries.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) } ?: CUSTOM_LINK
    }
}

data class GatewayInitResponse(
    val gateway: String,
    val transactionId: String,
    val paymentUrl: String,
    val status: String,
    val message: String,
    val isLiveGatewayResponse: Boolean
)

object OnlinePaymentService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun resolveCinetPayCreds(config: OnlinePaymentConfigEntity?): Triple<String, String, String>? {
        if (config != null) {
            if (!config.isEnabled) return null
            val key = config.apiKey.trim()
            val site = config.siteOrMerchantId.trim()
            if (key.isNotBlank() && site.isNotBlank()) {
                return Triple(key, site, config.callbackUrl.trim())
            }
            return null
        }
        val key = BuildConfig.CINETPAY_API_KEY.trim()
        val site = BuildConfig.CINETPAY_SITE_ID.trim()
        return if (key.isNotBlank() && !key.endsWith("_DEFAULT") &&
            site.isNotBlank() && !site.endsWith("_DEFAULT")
        ) {
            Triple(key, site, "")
        } else {
            null
        }
    }

    private fun resolveNotchPayKey(config: OnlinePaymentConfigEntity?): Pair<String, String>? {
        if (config != null) {
            if (!config.isEnabled) return null
            val key = config.apiKey.trim()
            return if (key.isNotBlank()) key to config.callbackUrl.trim() else null
        }
        val key = BuildConfig.NOTCHPAY_PUBLIC_KEY.trim()
        return if (key.isNotBlank() && !key.endsWith("_DEFAULT")) key to "" else null
    }

    fun isCinetPayConfigured(config: OnlinePaymentConfigEntity? = null): Boolean =
        resolveCinetPayCreds(config) != null

    fun isNotchPayConfigured(config: OnlinePaymentConfigEntity? = null): Boolean =
        resolveNotchPayKey(config) != null

    suspend fun initiateWithConfig(
        config: OnlinePaymentConfigEntity?,
        fallbackProvider: String,
        transactionId: String,
        amountXaf: Double,
        description: String,
        customerName: String,
        customerPhone: String,
        customerEmail: String,
        channel: String
    ): GatewayInitResponse {
        if (config == null) {
            return when {
                fallbackProvider.equals("CinetPay", ignoreCase = true) ||
                    fallbackProvider.equals(OnlinePaymentGatewayType.CINETPAY.code, ignoreCase = true) ->
                    initiateCinetPay(
                        transactionId = transactionId,
                        amountXaf = amountXaf,
                        description = description,
                        customerName = customerName,
                        customerPhone = customerPhone,
                        customerEmail = customerEmail,
                        channel = channel,
                        config = null
                    )
                fallbackProvider.equals("NotchPay", ignoreCase = true) ||
                    fallbackProvider.equals(OnlinePaymentGatewayType.NOTCHPAY.code, ignoreCase = true) ->
                    initiateNotchPay(
                        transactionId = transactionId,
                        amountXaf = amountXaf,
                        description = description,
                        customerName = customerName,
                        customerPhone = customerPhone,
                        customerEmail = customerEmail,
                        config = null
                    )
                else -> GatewayInitResponse(
                    gateway = fallbackProvider,
                    transactionId = transactionId,
                    paymentUrl = buildQrPaymentUri(fallbackProvider.uppercase(), transactionId, amountXaf, customerPhone),
                    status = "EN_ATTENTE_VALIDATION",
                    message = "Ticket QR & Code-barres $fallbackProvider généré. Confirmez dès réception du paiement.",
                    isLiveGatewayResponse = false
                )
            }
        }

        val label = config.displayName.ifBlank { OnlinePaymentGatewayType.fromCode(config.category).label }
        if (!config.isEnabled) {
            return GatewayInitResponse(
                gateway = label,
                transactionId = transactionId,
                paymentUrl = buildQrPaymentUri(label.uppercase(), transactionId, amountXaf, config.receiverPhone.ifBlank { customerPhone }),
                status = "EN_ATTENTE_VALIDATION",
                message = "La passerelle « $label » est actuellement désactivée par l'administrateur.",
                isLiveGatewayResponse = false
            )
        }

        return when (config.category.uppercase()) {
            OnlinePaymentGatewayType.CINETPAY.code -> initiateCinetPay(
                transactionId = transactionId,
                amountXaf = amountXaf,
                description = description,
                customerName = customerName,
                customerPhone = customerPhone,
                customerEmail = customerEmail,
                channel = channel,
                config = config
            )
            OnlinePaymentGatewayType.NOTCHPAY.code -> initiateNotchPay(
                transactionId = transactionId,
                amountXaf = amountXaf,
                description = description,
                customerName = customerName,
                customerPhone = customerPhone,
                customerEmail = customerEmail,
                config = config
            )
            OnlinePaymentGatewayType.CUSTOM_API.code -> initiateCustomApi(
                config = config,
                transactionId = transactionId,
                amountXaf = amountXaf,
                description = description,
                customerName = customerName,
                customerPhone = customerPhone,
                customerEmail = customerEmail,
                channel = channel
            )
            OnlinePaymentGatewayType.CUSTOM_LINK.code -> initiateCustomLink(
                config = config,
                transactionId = transactionId,
                amountXaf = amountXaf,
                description = description,
                customerName = customerName,
                customerPhone = customerPhone
            )
            OnlinePaymentGatewayType.ORANGE_MONEY.code,
            OnlinePaymentGatewayType.MTN_MOMO.code -> initiateDirectMobileMoney(
                config = config,
                transactionId = transactionId,
                amountXaf = amountXaf,
                customerPhone = customerPhone
            )
            OnlinePaymentGatewayType.BANK_CARD.code -> initiateBankOrCard(
                config = config,
                transactionId = transactionId,
                amountXaf = amountXaf,
                description = description,
                customerName = customerName,
                customerPhone = customerPhone
            )
            else -> initiateCustomLink(
                config = config,
                transactionId = transactionId,
                amountXaf = amountXaf,
                description = description,
                customerName = customerName,
                customerPhone = customerPhone
            )
        }
    }

    suspend fun verifyWithConfig(
        transactionId: String,
        providerName: String,
        config: OnlinePaymentConfigEntity?
    ): Pair<String, String> {
        val cat = config?.category?.uppercase()
        return when {
            cat == OnlinePaymentGatewayType.CINETPAY.code ||
                (cat == null && providerName.equals("CinetPay", ignoreCase = true)) ->
                verifyCinetPayStatus(transactionId, config)
            cat == OnlinePaymentGatewayType.NOTCHPAY.code ||
                (cat == null && providerName.equals("NotchPay", ignoreCase = true)) ->
                verifyNotchPayStatus(transactionId, config)
            cat == OnlinePaymentGatewayType.CUSTOM_API.code ->
                verifyCustomApiStatus(transactionId, config)
            else -> "EN_ATTENTE_VALIDATION" to
                "Vérification manuelle requise pour $providerName : saisissez la référence de confirmation."
        }
    }

    suspend fun initiateCinetPay(
        transactionId: String,
        amountXaf: Double,
        description: String,
        customerName: String,
        customerPhone: String,
        customerEmail: String,
        channel: String,
        config: OnlinePaymentConfigEntity? = null
    ): GatewayInitResponse = withContext(Dispatchers.IO) {
        val label = config?.displayName?.ifBlank { "CinetPay" } ?: "CinetPay"
        val creds = resolveCinetPayCreds(config)
        if (creds == null) {
            return@withContext GatewayInitResponse(
                gateway = label,
                transactionId = transactionId,
                paymentUrl = buildQrPaymentUri("CINETPAY", transactionId, amountXaf, customerPhone),
                status = "EN_ATTENTE_VALIDATION",
                message = "Identifiants CinetPay de l'entreprise non configurés (Paramètres > Passerelles de paiement). Ticket enregistré en attente de validation.",
                isLiveGatewayResponse = false
            )
        }
        val (apiKey, siteId, callbackUrl) = creds
        val notifyUrl = callbackUrl.ifBlank { "https://smartstorepro.local/cinetpay/notify" }
        val returnUrl = callbackUrl.ifBlank { "https://smartstorepro.local/cinetpay/return" }
        val endpoint = config?.paymentBaseUrl?.trim()?.ifBlank { null } ?: "https://api-checkout.cinetpay.com/v2/payment"

        val payload = JSONObject().apply {
            put("apikey", apiKey)
            put("site_id", siteId)
            put("transaction_id", transactionId)
            put("amount", amountXaf.toLong().coerceAtLeast(100L))
            put("currency", "XAF")
            put("description", description.ifBlank { "Paiement en ligne" })
            put("channels", if (channel == "MOBILE_MONEY") "MOBILE_MONEY" else "ALL")
            put("customer_name", customerName.ifBlank { "Client" })
            put("customer_phone_number", customerPhone)
            put("customer_email", customerEmail.ifBlank { "client@entreprise.local" })
            put("notify_url", notifyUrl)
            put("return_url", returnUrl)
        }

        return@withContext try {
            val req = Request.Builder()
                .url(endpoint)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                val code = json?.optString("code").orEmpty()
                val payUrl = json?.optJSONObject("data")?.optString("payment_url").orEmpty()
                if (resp.isSuccessful && (code == "201" || payUrl.isNotBlank())) {
                    GatewayInitResponse(
                        gateway = label,
                        transactionId = transactionId,
                        paymentUrl = payUrl.ifBlank { buildQrPaymentUri("CINETPAY", transactionId, amountXaf, customerPhone) },
                        status = "EN_ATTENTE_PAIEMENT",
                        message = json?.optString("description").orEmpty().ifBlank { "Lien de paiement CinetPay généré." },
                        isLiveGatewayResponse = true
                    )
                } else {
                    GatewayInitResponse(
                        gateway = label,
                        transactionId = transactionId,
                        paymentUrl = buildQrPaymentUri("CINETPAY", transactionId, amountXaf, customerPhone),
                        status = "ECHEC_INIT",
                        message = json?.optString("message").orEmpty().ifBlank { "Réponse CinetPay HTTP ${resp.code}" },
                        isLiveGatewayResponse = true
                    )
                }
            }
        } catch (e: Exception) {
            GatewayInitResponse(
                gateway = label,
                transactionId = transactionId,
                paymentUrl = buildQrPaymentUri("CINETPAY", transactionId, amountXaf, customerPhone),
                status = "EN_ATTENTE_VALIDATION",
                message = "Réseau indisponible (${e.localizedMessage ?: "hors ligne"}). Référence enregistrée localement.",
                isLiveGatewayResponse = false
            )
        }
    }

    suspend fun verifyCinetPayStatus(
        transactionId: String,
        config: OnlinePaymentConfigEntity? = null
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val creds = resolveCinetPayCreds(config)
            ?: return@withContext "EN_ATTENTE_VALIDATION" to "Identifiants CinetPay non configurés : confirmation manuelle requise."
        val (apiKey, siteId, _) = creds
        val payload = JSONObject().apply {
            put("apikey", apiKey)
            put("site_id", siteId)
            put("transaction_id", transactionId)
        }
        val checkUrl = config?.verifyEndpointUrl?.trim()?.ifBlank { null }
            ?: "https://api-checkout.cinetpay.com/v2/payment/check"

        return@withContext try {
            val req = Request.Builder()
                .url(checkUrl)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                val data = json?.optJSONObject("data")
                val status = data?.optString("status").orEmpty()
                when (status.uppercase()) {
                    "ACCEPTED" -> "CONFIRME" to "Paiement confirmé par CinetPay (${data?.optString("payment_method") ?: "Mobile Money"})."
                    "REFUSED", "CANCELLED" -> "ECHOUE" to "Paiement refusé ou annulé sur CinetPay."
                    else -> "EN_ATTENTE_PAIEMENT" to (json?.optString("message").orEmpty().ifBlank { "Statut CinetPay : ${status.ifBlank { "EN ATTENTE" }}" })
                }
            }
        } catch (e: Exception) {
            "EN_ATTENTE_VALIDATION" to "Vérification impossible hors ligne (${e.localizedMessage ?: "réseau"})."
        }
    }

    suspend fun initiateNotchPay(
        transactionId: String,
        amountXaf: Double,
        description: String,
        customerName: String,
        customerPhone: String,
        customerEmail: String,
        config: OnlinePaymentConfigEntity? = null
    ): GatewayInitResponse = withContext(Dispatchers.IO) {
        val label = config?.displayName?.ifBlank { "NotchPay" } ?: "NotchPay"
        val keyPair = resolveNotchPayKey(config)
        if (keyPair == null) {
            return@withContext GatewayInitResponse(
                gateway = label,
                transactionId = transactionId,
                paymentUrl = buildQrPaymentUri("NOTCHPAY", transactionId, amountXaf, customerPhone),
                status = "EN_ATTENTE_VALIDATION",
                message = "Clé NotchPay de l'entreprise non configurée (Paramètres > Passerelles de paiement). Transaction enregistrée localement.",
                isLiveGatewayResponse = false
            )
        }
        val (publicKey, callbackUrl) = keyPair
        val endpoint = config?.paymentBaseUrl?.trim()?.ifBlank { null } ?: "https://api.notchpay.co/payments"

        val payload = JSONObject().apply {
            put("amount", amountXaf.toLong().coerceAtLeast(100L))
            put("currency", "XAF")
            put("reference", transactionId)
            put("description", description.ifBlank { "Paiement en ligne" })
            if (callbackUrl.isNotBlank()) {
                put("callback", callbackUrl)
            }
            put("customer", JSONObject().apply {
                put("name", customerName.ifBlank { "Client" })
                put("email", customerEmail.ifBlank { "client@entreprise.local" })
                put("phone", customerPhone)
            })
        }

        return@withContext try {
            val req = Request.Builder()
                .url(endpoint)
                .header("Authorization", publicKey)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                val authUrl = json?.optString("authorization_url").orEmpty()
                    .ifBlank { json?.optJSONObject("transaction")?.optString("authorization_url").orEmpty() }
                if (resp.isSuccessful && authUrl.isNotBlank()) {
                    GatewayInitResponse(
                        gateway = label,
                        transactionId = transactionId,
                        paymentUrl = authUrl,
                        status = "EN_ATTENTE_PAIEMENT",
                        message = json?.optString("message").orEmpty().ifBlank { "Lien de paiement NotchPay généré." },
                        isLiveGatewayResponse = true
                    )
                } else {
                    GatewayInitResponse(
                        gateway = label,
                        transactionId = transactionId,
                        paymentUrl = buildQrPaymentUri("NOTCHPAY", transactionId, amountXaf, customerPhone),
                        status = "ECHEC_INIT",
                        message = json?.optString("message").orEmpty().ifBlank { "Réponse NotchPay HTTP ${resp.code}" },
                        isLiveGatewayResponse = true
                    )
                }
            }
        } catch (e: Exception) {
            GatewayInitResponse(
                gateway = label,
                transactionId = transactionId,
                paymentUrl = buildQrPaymentUri("NOTCHPAY", transactionId, amountXaf, customerPhone),
                status = "EN_ATTENTE_VALIDATION",
                message = "Réseau indisponible (${e.localizedMessage ?: "hors ligne"}). Transaction enregistrée localement.",
                isLiveGatewayResponse = false
            )
        }
    }

    suspend fun verifyNotchPayStatus(
        transactionId: String,
        config: OnlinePaymentConfigEntity? = null
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val keyPair = resolveNotchPayKey(config)
            ?: return@withContext "EN_ATTENTE_VALIDATION" to "Clé NotchPay non configurée : confirmation manuelle requise."
        val (publicKey, _) = keyPair
        val baseCheck = config?.verifyEndpointUrl?.trim()?.ifBlank { null }
            ?: "https://api.notchpay.co/payments/$transactionId"
        val checkUrl = if (baseCheck.contains("{ref}")) baseCheck.replace("{ref}", transactionId) else baseCheck
        return@withContext try {
            val req = Request.Builder()
                .url(checkUrl)
                .header("Authorization", publicKey)
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                val txStatus = json?.optJSONObject("transaction")?.optString("status").orEmpty()
                    .ifBlank { json?.optString("status").orEmpty() }
                when (txStatus.lowercase()) {
                    "complete", "completed", "paid", "success", "accepted" ->
                        "CONFIRME" to "Paiement confirmé par NotchPay."
                    "failed", "canceled", "cancelled", "rejected" ->
                        "ECHOUE" to "Paiement refusé ou annulé sur NotchPay."
                    else -> "EN_ATTENTE_PAIEMENT" to "Statut NotchPay : ${txStatus.ifBlank { "EN ATTENTE" }}"
                }
            }
        } catch (e: Exception) {
            "EN_ATTENTE_VALIDATION" to "Vérification NotchPay impossible hors ligne (${e.localizedMessage ?: "réseau"})."
        }
    }

    private fun initiateDirectMobileMoney(
        config: OnlinePaymentConfigEntity,
        transactionId: String,
        amountXaf: Double,
        customerPhone: String
    ): GatewayInitResponse {
        val label = config.displayName.ifBlank { OnlinePaymentGatewayType.fromCode(config.category).label }
        val merchantPhone = config.receiverPhone.trim()
        val merchantCode = config.siteOrMerchantId.trim()
        val holder = config.accountHolderName.trim()
        val interpolatedUssd = interpolateTemplate(
            template = config.ussdPattern.trim(),
            amountXaf = amountXaf,
            ref = transactionId,
            merchantPhone = merchantPhone,
            merchantId = merchantCode,
            customerPhone = customerPhone,
            customerName = "",
            description = ""
        )
        val details = buildList {
            if (merchantPhone.isNotBlank()) add("N° marchand : $merchantPhone")
            if (merchantCode.isNotBlank()) add("Code marchand : $merchantCode")
            if (holder.isNotBlank()) add("Titulaire : $holder")
            if (interpolatedUssd.isNotBlank()) add("USSD : $interpolatedUssd")
            if (config.instructions.isNotBlank()) add(config.instructions.trim())
        }
        val qrTargetPhone = merchantPhone.ifBlank { customerPhone }
        val qrUri = buildQrPaymentUri(
            provider = config.category,
            reference = transactionId,
            amountXaf = amountXaf,
            phone = qrTargetPhone,
            merchantCode = merchantCode
        )
        val msg = if (details.isNotEmpty()) {
            "$label · ${details.joinToString(" · ")}. Confirmez avec l'ID SMS opérateur."
        } else {
            "Ticket $label généré. Confirmez dès réception du SMS opérateur."
        }
        return GatewayInitResponse(
            gateway = label,
            transactionId = transactionId,
            paymentUrl = qrUri,
            status = "EN_ATTENTE_VALIDATION",
            message = msg,
            isLiveGatewayResponse = false
        )
    }

    private fun initiateBankOrCard(
        config: OnlinePaymentConfigEntity,
        transactionId: String,
        amountXaf: Double,
        description: String,
        customerName: String,
        customerPhone: String
    ): GatewayInitResponse {
        val label = config.displayName.ifBlank { "Carte / Virement Bancaire" }
        val account = config.siteOrMerchantId.trim()
        val holder = config.accountHolderName.trim()
        val baseUrl = config.paymentBaseUrl.trim()
        val link = if (baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            buildCheckoutLink(baseUrl, amountXaf, transactionId, customerPhone, customerName, description, account)
        } else {
            buildQrPaymentUri("BANK_CARD", transactionId, amountXaf, config.receiverPhone.ifBlank { customerPhone }, account)
        }
        val parts = buildList {
            if (holder.isNotBlank()) add("Bénéficiaire : $holder")
            if (account.isNotBlank()) add("Compte / IBAN / TPE : $account")
            if (config.instructions.isNotBlank()) add(config.instructions.trim())
        }
        val msg = if (parts.isNotEmpty()) {
            "$label · ${parts.joinToString(" · ")}"
        } else {
            "Ordre $label généré · Référence $transactionId."
        }
        return GatewayInitResponse(
            gateway = label,
            transactionId = transactionId,
            paymentUrl = link,
            status = "EN_ATTENTE_VALIDATION",
            message = msg,
            isLiveGatewayResponse = baseUrl.startsWith("http")
        )
    }

    private fun initiateCustomLink(
        config: OnlinePaymentConfigEntity,
        transactionId: String,
        amountXaf: Double,
        description: String,
        customerName: String,
        customerPhone: String
    ): GatewayInitResponse {
        val label = config.displayName.ifBlank { "Lien de paiement" }
        val baseUrl = config.paymentBaseUrl.trim()
        val hasHttp = baseUrl.startsWith("http://") || baseUrl.startsWith("https://")
        val targetUrl = if (hasHttp) {
            buildCheckoutLink(
                baseUrl = baseUrl,
                amountXaf = amountXaf,
                ref = transactionId,
                customerPhone = customerPhone,
                customerName = customerName,
                description = description,
                merchantId = config.siteOrMerchantId.trim()
            )
        } else {
            buildQrPaymentUri(label.uppercase(), transactionId, amountXaf, config.receiverPhone.ifBlank { customerPhone }, config.siteOrMerchantId.trim())
        }
        val infoParts = buildList {
            if (hasHttp) add("Lien de paiement $label prêt") else add("Ticket QR $label généré")
            if (config.accountHolderName.isNotBlank()) add("Bénéficiaire : ${config.accountHolderName.trim()}")
            if (config.receiverPhone.isNotBlank()) add("Tél : ${config.receiverPhone.trim()}")
            if (config.instructions.isNotBlank()) add(config.instructions.trim())
        }
        return GatewayInitResponse(
            gateway = label,
            transactionId = transactionId,
            paymentUrl = targetUrl,
            status = if (hasHttp) "EN_ATTENTE_PAIEMENT" else "EN_ATTENTE_VALIDATION",
            message = infoParts.joinToString(" · "),
            isLiveGatewayResponse = hasHttp
        )
    }

    private suspend fun initiateCustomApi(
        config: OnlinePaymentConfigEntity,
        transactionId: String,
        amountXaf: Double,
        description: String,
        customerName: String,
        customerPhone: String,
        customerEmail: String,
        channel: String
    ): GatewayInitResponse = withContext(Dispatchers.IO) {
        val label = config.displayName.ifBlank { "Passerelle API" }
        val endpoint = config.paymentBaseUrl.trim()
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            return@withContext GatewayInitResponse(
                gateway = label,
                transactionId = transactionId,
                paymentUrl = buildQrPaymentUri(label.uppercase(), transactionId, amountXaf, customerPhone),
                status = "EN_ATTENTE_VALIDATION",
                message = "URL d'endpoint API invalide pour « $label ». Ticket enregistré localement.",
                isLiveGatewayResponse = false
            )
        }

        val payload = JSONObject().apply {
            put("transaction_id", transactionId)
            put("reference", transactionId)
            put("amount", amountXaf.toLong().coerceAtLeast(100L))
            put("currency", "XAF")
            put("channel", channel)
            put("description", description.ifBlank { "Paiement $label" })
            put("customer_name", customerName.ifBlank { "Client" })
            put("customer_phone", customerPhone)
            put("customer_email", customerEmail.ifBlank { "client@entreprise.local" })
            if (config.siteOrMerchantId.isNotBlank()) put("merchant_id", config.siteOrMerchantId.trim())
            if (config.callbackUrl.isNotBlank()) {
                put("callback_url", config.callbackUrl.trim())
                put("notify_url", config.callbackUrl.trim())
            }
        }

        return@withContext try {
            val builder = Request.Builder()
                .url(endpoint)
                .post(payload.toString().toRequestBody(jsonMediaType))
            if (config.apiKey.isNotBlank()) {
                builder.header("Authorization", "Bearer ${config.apiKey.trim()}")
                builder.header("X-Api-Key", config.apiKey.trim())
            }
            if (config.secretOrToken.isNotBlank()) {
                builder.header("X-Api-Secret", config.secretOrToken.trim())
            }
            client.newCall(builder.build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                val dataObj = json?.optJSONObject("data")
                val checkoutUrl = listOfNotNull(
                    json?.optString("payment_url")?.takeIf { it.isNotBlank() },
                    json?.optString("checkout_url")?.takeIf { it.isNotBlank() },
                    json?.optString("authorization_url")?.takeIf { it.isNotBlank() },
                    json?.optString("url")?.takeIf { it.isNotBlank() },
                    dataObj?.optString("payment_url")?.takeIf { it.isNotBlank() },
                    dataObj?.optString("checkout_url")?.takeIf { it.isNotBlank() },
                    dataObj?.optString("authorization_url")?.takeIf { it.isNotBlank() },
                    dataObj?.optString("link")?.takeIf { it.isNotBlank() }
                ).firstOrNull().orEmpty()
                val msg = json?.optString("message").orEmpty()
                if (resp.isSuccessful) {
                    GatewayInitResponse(
                        gateway = label,
                        transactionId = transactionId,
                        paymentUrl = checkoutUrl.ifBlank { buildQrPaymentUri(label.uppercase(), transactionId, amountXaf, customerPhone) },
                        status = "EN_ATTENTE_PAIEMENT",
                        message = msg.ifBlank { "Transaction initiée sur $label." },
                        isLiveGatewayResponse = checkoutUrl.startsWith("http")
                    )
                } else {
                    GatewayInitResponse(
                        gateway = label,
                        transactionId = transactionId,
                        paymentUrl = buildQrPaymentUri(label.uppercase(), transactionId, amountXaf, customerPhone),
                        status = "ECHEC_INIT",
                        message = msg.ifBlank { "Réponse $label HTTP ${resp.code}" },
                        isLiveGatewayResponse = true
                    )
                }
            }
        } catch (e: Exception) {
            GatewayInitResponse(
                gateway = label,
                transactionId = transactionId,
                paymentUrl = buildQrPaymentUri(label.uppercase(), transactionId, amountXaf, customerPhone),
                status = "EN_ATTENTE_VALIDATION",
                message = "Passerelle $label injoignable (${e.localizedMessage ?: "réseau"}). Ticket enregistré localement.",
                isLiveGatewayResponse = false
            )
        }
    }

    private suspend fun verifyCustomApiStatus(
        transactionId: String,
        config: OnlinePaymentConfigEntity?
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val label = config?.displayName?.ifBlank { "API" } ?: "API"
        val rawUrl = config?.verifyEndpointUrl?.trim().orEmpty()
        if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
            return@withContext "EN_ATTENTE_VALIDATION" to
                "Aucune URL de vérification configurée pour $label : confirmez manuellement avec la référence."
        }
        val url = if (rawUrl.contains("{ref}")) {
            rawUrl.replace("{ref}", transactionId)
        } else {
            val sep = if (rawUrl.contains("?")) "&" else "?"
            "${rawUrl}${sep}transaction_id=${URLEncoder.encode(transactionId, "UTF-8")}"
        }
        return@withContext try {
            val builder = Request.Builder().url(url).get()
            if (config?.apiKey?.isNotBlank() == true) {
                builder.header("Authorization", "Bearer ${config.apiKey.trim()}")
                builder.header("X-Api-Key", config.apiKey.trim())
            }
            if (config?.secretOrToken?.isNotBlank() == true) {
                builder.header("X-Api-Secret", config.secretOrToken.trim())
            }
            client.newCall(builder.build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                val rawStatus = json?.optString("status").orEmpty()
                    .ifBlank { json?.optJSONObject("data")?.optString("status").orEmpty() }
                when (rawStatus.uppercase()) {
                    "ACCEPTED", "CONFIRMED", "CONFIRME", "PAID", "SUCCESS", "COMPLETED" ->
                        "CONFIRME" to "Paiement confirmé par l'API $label."
                    "REFUSED", "CANCELLED", "CANCELED", "FAILED", "ERROR", "ECHOUE" ->
                        "ECHOUE" to "Paiement refusé ou annulé sur $label."
                    else -> "EN_ATTENTE_PAIEMENT" to "Statut $label : ${rawStatus.ifBlank { "EN ATTENTE" }}"
                }
            }
        } catch (e: Exception) {
            "EN_ATTENTE_VALIDATION" to "Vérification $label impossible hors ligne (${e.localizedMessage ?: "réseau"})."
        }
    }

    private fun interpolateTemplate(
        template: String,
        amountXaf: Double,
        ref: String,
        merchantPhone: String,
        merchantId: String,
        customerPhone: String,
        customerName: String,
        description: String
    ): String {
        if (template.isBlank()) return ""
        return template
            .replace("{amount}", amountXaf.toLong().toString())
            .replace("{ref}", ref)
            .replace("{phone}", merchantPhone.ifBlank { customerPhone })
            .replace("{customer_phone}", customerPhone)
            .replace("{merchant}", merchantId.ifBlank { merchantPhone })
            .replace("{name}", customerName)
            .replace("{description}", description)
    }

    private fun buildCheckoutLink(
        baseUrl: String,
        amountXaf: Double,
        ref: String,
        customerPhone: String,
        customerName: String,
        description: String,
        merchantId: String
    ): String {
        val cleanBase = baseUrl.trim()
        val hasPlaceholders = listOf("{amount}", "{ref}", "{phone}", "{name}", "{description}", "{merchant}")
            .any { cleanBase.contains(it) }
        if (hasPlaceholders) {
            return cleanBase
                .replace("{amount}", amountXaf.toLong().toString())
                .replace("{ref}", URLEncoder.encode(ref, "UTF-8"))
                .replace("{phone}", URLEncoder.encode(customerPhone, "UTF-8"))
                .replace("{name}", URLEncoder.encode(customerName, "UTF-8"))
                .replace("{description}", URLEncoder.encode(description, "UTF-8"))
                .replace("{merchant}", URLEncoder.encode(merchantId, "UTF-8"))
        }
        val sep = if (cleanBase.contains("?")) "&" else "?"
        return "${cleanBase}${sep}amount=${amountXaf.toLong()}&currency=XAF&ref=${URLEncoder.encode(ref, "UTF-8")}"
    }

    fun buildQrPaymentUri(
        provider: String,
        reference: String,
        amountXaf: Double,
        phone: String,
        merchantCode: String = ""
    ): String {
        val cleanPhone = Formatters.cleanPhoneForWhatsApp(phone)
        val base = "SSP-PAY|$provider|REF=$reference|XAF=${amountXaf.toLong()}|TEL=$cleanPhone"
        return if (merchantCode.isNotBlank()) "$base|MID=$merchantCode" else base
    }
}
