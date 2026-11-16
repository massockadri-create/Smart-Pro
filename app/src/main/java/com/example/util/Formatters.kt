package com.example.util

import android.os.Build
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

object Formatters {
    private val spaceSymbols = DecimalFormatSymbols(Locale.FRENCH).apply {
        groupingSeparator = ' '
    }
    private val integerFormatter = DecimalFormat("#,##0", spaceSymbols)

    fun fcfa(amount: Double): String {
        val safe = if (amount.isNaN() || amount.isInfinite()) 0.0 else amount
        val rounded = safe.roundToLong()
        return "${integerFormatter.format(rounded)} FCFA"
    }

    fun fcfaCompact(amount: Double): String {
        val safe = if (amount.isNaN() || amount.isInfinite()) 0.0 else amount
        val abs = kotlin.math.abs(safe)
        val sign = if (safe < 0) "-" else ""
        return when {
            abs >= 1_000_000 -> String.format(Locale.FRANCE, "%s%.1fM FCFA", sign, abs / 1_000_000.0)
            abs >= 100_000 -> String.format(Locale.FRANCE, "%s%.0fk FCFA", sign, abs / 1_000.0)
            else -> fcfa(safe)
        }
    }

    fun percent(value: Double): String {
        val safe = if (value.isNaN() || value.isInfinite()) 0.0 else value
        return String.format(Locale.FRANCE, "%.1f%%", safe)
    }

    fun dateTime(epochMs: Long): String = if (epochMs <= 0L) "—" else
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(epochMs))

    fun dateTimeWithSeconds(epochMs: Long): String = if (epochMs <= 0L) "—" else
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE).format(Date(epochMs))

    fun date(epochMs: Long): String = if (epochMs <= 0L) "—" else
        SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(epochMs))

    fun time(epochMs: Long): String = if (epochMs <= 0L) "—" else
        SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(epochMs))

    fun timeWithSeconds(epochMs: Long): String = if (epochMs <= 0L) "—" else
        SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date(epochMs))

    fun liveClockDate(epochMs: Long): String = if (epochMs <= 0L) "—" else
        SimpleDateFormat("EEE dd MMM yyyy", Locale.FRENCH).format(Date(epochMs))

    fun shortDay(epochMs: Long): String = if (epochMs <= 0L) "—" else
        SimpleDateFormat("dd MMM", Locale.FRENCH).format(Date(epochMs))

    fun marginAmount(sellingPrice: Double, purchasePrice: Double): Double = sellingPrice - purchasePrice

    fun marginPercent(sellingPrice: Double, purchasePrice: Double): Double {
        if (sellingPrice <= 0.0) return 0.0
        val pct = ((sellingPrice - purchasePrice) / sellingPrice) * 100.0
        return if (pct.isNaN() || pct.isInfinite()) 0.0 else pct
    }

    /**
     * Extracts the first 3 uppercase ASCII letters from a category name (e.g. "Boissons" -> "BOI",
     * "Électronique" -> "ELE", "Produits ménagers" -> "PRO").
     */
    fun categoryCodePrefix(categoryName: String): String {
        val normalized = Normalizer.normalize(categoryName.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^A-Za-z0-9]"), "")
            .uppercase(Locale.ROOT)
        return normalized.take(3).ifBlank { "DIV" }.padEnd(3, 'X')
    }

    /**
     * Builds the automatic product reference combining the current invoice sequence and the start
     * of the chosen category name (e.g. "FAC-1001-BOI").
     */
    fun autoProductReference(invoiceBase: String, categoryName: String): String {
        val cleanInv = invoiceBase.trim().ifBlank { "FAC-1001" }.uppercase(Locale.ROOT)
        return "$cleanInv-${categoryCodePrefix(categoryName)}"
    }

    /**
     * Builds the customer-facing product line code placed on invoices:
     * invoice number followed by the start of the product's category name.
     */
    fun productInvoiceCode(invoiceNumber: String, categoryName: String): String {
        val cleanInv = invoiceNumber.trim().ifBlank { "FAC-1001" }.uppercase(Locale.ROOT)
        return "$cleanInv-${categoryCodePrefix(categoryName)}"
    }

    fun cleanPhoneForWhatsApp(rawPhone: String): String {
        val digits = rawPhone.filter { it.isDigit() }
        return if (digits.length == 9 && digits.startsWith("6")) "237$digits" else digits
    }

    fun deviceLabel(): String = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
}
