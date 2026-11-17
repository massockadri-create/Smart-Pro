package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import com.google.zxing.BarcodeFormat
import com.google.zxing.oned.Code128Writer
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.OpenableColumns
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import com.example.data.local.*
import java.io.File
import java.io.OutputStream

object DocumentExporter {

    data class ImportedFileInfo(
        val fileName: String,
        val mimeType: String,
        val extension: String,
        val localPath: String,
        val sizeBytes: Long,
        val textPreviewIfText: String?
    )

    private var activePrintWebView: WebView? = null

    fun printHtmlDocument(context: Context, jobName: String, htmlContent: String) {
        val webView = WebView(context)
        activePrintWebView = webView
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.postDelayed({
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                    if (printManager != null) {
                        val adapter = webView.createPrintDocumentAdapter(jobName)
                        printManager.print(
                            jobName,
                            adapter,
                            PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .build()
                        )
                    }
                }
                                  , 500)
            }
        }
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }
    
    fun buildInvoiceHtml(
        settings: StoreSettingsEntity,
        invoice: InvoiceEntity,
        items: List<SaleItemEntity>
    ): String {
        val logoSvg = StructureCatalog.buildStructureLogoSvg(settings.logoKey, 56)
        val invoiceBarcodeSvg = BarcodeSvgHelper.buildCode128Svg(invoice.invoiceNumber, 38)
        val locationLine = listOf(settings.city, settings.location)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        val storeTitle = settings.storeName.ifBlank { "SMART STORE PRO" }

        val rows = if (items.isNotEmpty()) {
            items.joinToString("") { item ->
                val itemCode = item.productInvoiceCode.ifBlank {
                    Formatters.productInvoiceCode(invoice.invoiceNumber, item.categoryName)
                }
                val barcodeValue = item.productBarcode.ifBlank { item.sku.ifBlank { itemCode } }
                val barcodeSvg = BarcodeSvgHelper.buildCode128Svg(barcodeValue, 32)
                """
                <tr>
                  <td>
                    <strong>${escapeHtml(item.productName)}</strong><br/>
                    <small style="color:#059669;font-family:monospace;">Code ligne : ${escapeHtml(itemCode)}</small>
                    ${if (item.categoryName.isNotBlank()) " · <small style=\"color:#475569;\">${escapeHtml(item.categoryName)}</small>" else ""}
                  </td>
                  <td>
                    <div style="background:#ffffff;padding:4px;border:1px solid #e2e8f0;border-radius:6px;display:inline-block;">
                      $barcodeSvg
                      <div style="font-family:monospace;font-size:10px;text-align:center;color:#0f172a;margin-top:2px;">${escapeHtml(barcodeValue)}</div>
                    </div>
                  </td>
                  <td style="text-align:center;">${item.quantity}</td>
                  <td style="text-align:right;">${Formatters.fcfa(item.unitPrice)}</td>
                  <td style="text-align:right;font-weight:bold;">${Formatters.fcfa(item.lineTotal)}</td>
                </tr>
                """.trimIndent()
            }
        } else {
            val fallbackCode = Formatters.productInvoiceCode(invoice.invoiceNumber, "DIV")
            val barcodeSvg = BarcodeSvgHelper.buildCode128Svg(fallbackCode, 32)
            """
            <tr>
              <td>Prestation / Facturation directe<br/><small style="color:#059669;font-family:monospace;">Code ligne : ${escapeHtml(fallbackCode)}</small></td>
              <td>
                <div style="background:#ffffff;padding:4px;border:1px solid #e2e8f0;border-radius:6px;display:inline-block;">
                  $barcodeSvg
                  <div style="font-family:monospace;font-size:10px;text-align:center;color:#0f172a;margin-top:2px;">${escapeHtml(fallbackCode)}</div>
                </div>
              </td>
              <td style="text-align:center;">1</td>
              <td style="text-align:right;">${Formatters.fcfa(invoice.subtotalHt)}</td>
              <td style="text-align:right;font-weight:bold;">${Formatters.fcfa(invoice.subtotalHt)}</td>
            </tr>
            """.trimIndent()
        }

        return """
        <!DOCTYPE html>
        <html lang="fr">
        <head>
        <meta charset="utf-8"/>
        <style>
          body { font-family: sans-serif; color: #0f172a; padding: 24px; max-width: 780px; margin: auto; }
          .header { display: flex; justify-content: space-between; align-items: flex-start; border-bottom: 2px solid #059669; padding-bottom: 14px; margin-bottom: 18px; gap: 16px; }
          .brand-wrap { display: flex; gap: 12px; align-items: center; }
          .brand { font-size: 20px; font-weight: 800; color: #059669; }
          .meta { text-align: right; font-size: 12px; color: #334155; }
          .box { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; margin-bottom: 16px; font-size: 13px; }
          table { width: 100%; border-collapse: collapse; margin-top: 12px; font-size: 12.5px; }
          th { background: #0f172a; color: #ffffff; padding: 8px; text-align: left; }
          td { padding: 8px; border-bottom: 1px solid #e2e8f0; vertical-align: middle; }
          .totals { margin-top: 18px; width: 320px; margin-left: auto; font-size: 13px; }
          .totals td { border: none; padding: 4px 8px; }
          .grand { font-size: 16px; font-weight: 800; color: #059669; border-top: 2px solid #0f172a; }
          .inv-barcode { margin-top: 16px; padding: 10px; border: 1px dashed #cbd5e1; border-radius: 8px; display: inline-block; background: #fff; text-align: center; }
          .footer { margin-top: 24px; text-align: center; font-size: 12px; color: #64748b; border-top: 1px dashed #cbd5e1; padding-top: 12px; }
        </style>
        </head>
        <body>
          <div class="header">
            <div class="brand-wrap">
              <div>$logoSvg</div>
              <div>
                <div class="brand">${escapeHtml(storeTitle)}</div>
                ${if (locationLine.isNotBlank()) "<div style=\"font-size:12.5px;font-weight:600;color:#0f172a;\">${escapeHtml(locationLine)}</div>" else ""}
                ${if (settings.address.isNotBlank()) "<div style=\"font-size:12px;color:#475569;\">${escapeHtml(settings.address)}</div>" else ""}
                <div style="font-size:12px;color:#475569;">${listOf(settings.phone, settings.email).filter { it.isNotBlank() }.joinToString(" · ") { escapeHtml(it) }}</div>
              </div>
            </div>
            <div class="meta">
              <div style="font-size:16px;font-weight:700;color:#0f172a;">${escapeHtml(invoice.invoiceType)} N° ${escapeHtml(invoice.invoiceNumber)}</div>
              <div>Date : ${Formatters.dateTimeWithSeconds(invoice.createdAt)}</div>
              <div>Statut : <strong>${escapeHtml(invoice.status)}</strong></div>
              <div>Émetteur : ${escapeHtml(invoice.issuedByName)}</div>
            </div>
          </div>
          <div class="box">
            <strong>Client :</strong> ${escapeHtml(invoice.customerName)}<br/>
            ${if (invoice.customerPhone.isNotBlank()) "Tél : ${escapeHtml(invoice.customerPhone)}<br/>" else ""}
            ${if (invoice.customerAddress.isNotBlank()) "Adresse : ${escapeHtml(invoice.customerAddress)}<br/>" else ""}
            <strong>Mode de paiement :</strong> ${escapeHtml(invoice.paymentMethod)}
          </div>
          <table>
            <thead>
              <tr>
                <th>Désignation & Code Facture-Catégorie</th>
                <th>Code-barres produit</th>
                <th style="text-align:center;">Qté</th>
                <th style="text-align:right;">Prix U.</th>
                <th style="text-align:right;">Total</th>
              </tr>
            </thead>
            <tbody>$rows</tbody>
          </table>
          <div style="display:flex;justify-content:space-between;align-items:flex-end;flex-wrap:wrap;gap:16px;">
            <div class="inv-barcode">
              $invoiceBarcodeSvg
              <div style="font-family:monospace;font-size:11px;margin-top:4px;font-weight:bold;">${escapeHtml(invoice.invoiceNumber)}</div>
            </div>
            <table class="totals">
              <tr><td>Sous-total HT :</td><td style="text-align:right;">${Formatters.fcfa(invoice.subtotalHt)}</td></tr>
              <tr><td>Remise :</td><td style="text-align:right;">- ${Formatters.fcfa(invoice.discountAmount)}</td></tr>
              <tr><td>TVA (${Formatters.percent(invoice.taxRate)}) :</td><td style="text-align:right;">${Formatters.fcfa(invoice.taxAmount)}</td></tr>
              <tr class="grand"><td>Total TTC :</td><td style="text-align:right;">${Formatters.fcfa(invoice.totalTtc)}</td></tr>
              <tr><td>Montant payé :</td><td style="text-align:right;">${Formatters.fcfa(invoice.amountPaid)}</td></tr>
              <tr><td>Reste à payer :</td><td style="text-align:right;">${Formatters.fcfa(invoice.balanceDue)}</td></tr>
            </table>
          </div>
          <div class="footer">
            ${escapeHtml(settings.receiptFooter)}<br/>
            Document généré automatiquement · ${escapeHtml(storeTitle)}${if (locationLine.isNotBlank()) " (${escapeHtml(locationLine)})" else ""} · ${escapeHtml(settings.currency)}
          </div>
        </body>
        </html>
        """.trimIndent()
    }

    private fun fitText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val n = paint.breakText(text, true, (maxWidth - paint.measureText("…")).coerceAtLeast(1f), null)
        return text.take(n).trimEnd() + "…"
    }

    /** Facture PDF professionnelle : bandeau, cartes client/infos, tableau, totaux, tampon de statut, code-barres. */
    fun writeInvoicePdf(
        outputStream: OutputStream,
        settings: StoreSettingsEntity,
        invoice: InvoiceEntity,
        items: List<SaleItemEntity>,
        changeGiven: Double? = null
    ) {
        val pw = 595f
        val ph = 842f
        val m = 36f
        val primary = Color.parseColor("#065F46")
        val accent = Color.parseColor("#F59E0B")
        val navy = Color.parseColor("#0F172A")
        val gray = Color.parseColor("#64748B")
        val soft = Color.parseColor("#F1F5F9")
        val rule = Color.parseColor("#CBD5E1")
        val danger = Color.parseColor("#DC2626")
        fun tp(color: Int, size: Float, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = size
                textAlign = align
                typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = rule }
        val storeName = settings.storeName.ifBlank { "Ma boutique" }
        val issuer = invoice.issuedByName.ifBlank { "—" }
        val (stLabel, stColor) = when (invoice.status) {
            "PAYEE" -> "PAYÉE" to Color.parseColor("#059669")
            "PARTIELLE" -> "PARTIELLE" to Color.parseColor("#D97706")
            "IMPAYEE" -> "IMPAYÉE" to danger
            "ANNULEE" -> "ANNULÉE" to danger
            "PROFORMA" -> "PROFORMA" to Color.parseColor("#2563EB")
            else -> invoice.status to gray
        }
        val doc = PdfDocument()
        var pageNum = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas

        fun drawFooter() {
            fill.color = accent
            c.drawRect(m, ph - 52f, pw - m, ph - 51f, fill)
            val msg = tp(navy, 9.5f, true, Paint.Align.CENTER)
            c.drawText(fitText(settings.receiptFooter.ifBlank { "Merci pour votre confiance !" }, msg, pw - 2 * m), pw / 2, ph - 36f, msg)
            val small = tp(gray, 8f, false, Paint.Align.CENTER)
            c.drawText("Généré le ${Formatters.dateTimeWithSeconds(System.currentTimeMillis())} · Émis par $issuer · Page $pageNum", pw / 2, ph - 22f, small)
        }

        fun startPage(full: Boolean): Float {
            if (pageNum > 0) { drawFooter(); doc.finishPage(page) }
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pw.toInt(), ph.toInt(), pageNum).create())
            c = page.canvas
            val h = if (full) 118f else 54f
            fill.color = primary
            c.drawRect(0f, 0f, pw, h, fill)
            fill.color = accent
            c.drawRect(0f, h, pw, h + 5f, fill)
            if (full) {
                val white = tp(Color.WHITE, 22f, true)
                c.drawText(fitText(storeName.uppercase(), white, 320f), m, 44f, white)
                val sub = tp(Color.parseColor("#D1FAE5"), 9.5f)
                val info = listOf(
                    "${settings.city}${if (settings.location.isNotBlank()) " · ${settings.location}" else ""}",
                    settings.address,
                    listOf(settings.phone, settings.email).filter { it.isNotBlank() }.joinToString(" · ")
                ).filter { it.isNotBlank() }
                info.take(3).forEachIndexed { i, t -> c.drawText(fitText(t, sub, 320f), m, 66f + i * 15f, sub) }
                c.drawText(invoice.invoiceType.uppercase(), pw - m, 46f, tp(Color.WHITE, 26f, true, Paint.Align.RIGHT))
                c.drawText("N° ${invoice.invoiceNumber}", pw - m, 70f, tp(Color.WHITE, 13f, true, Paint.Align.RIGHT))
                c.drawText(Formatters.dateTime(invoice.createdAt), pw - m, 88f, tp(Color.parseColor("#D1FAE5"), 10f, false, Paint.Align.RIGHT))
            } else {
                val w14 = tp(Color.WHITE, 14f, true)
                c.drawText(fitText(storeName.uppercase(), w14, 300f), m, 34f, w14)
                c.drawText("${invoice.invoiceType} N° ${invoice.invoiceNumber}", pw - m, 34f, tp(Color.WHITE, 12f, true, Paint.Align.RIGHT))
            }
            return h + 24f
        }

        fun tableHeader(y: Float): Float {
            fill.color = navy
            c.drawRoundRect(RectF(m, y, pw - m, y + 24f), 6f, 6f, fill)
            val hl = tp(Color.WHITE, 8.5f, true)
            val hr = tp(Color.WHITE, 8.5f, true, Paint.Align.RIGHT)
            c.drawText("RÉF.", m + 10f, y + 16f, hl)
            c.drawText("DÉSIGNATION", m + 78f, y + 16f, hl)
            c.drawText("QTÉ", 372f, y + 16f, hr)
            c.drawText("PRIX UNIT.", 462f, y + 16f, hr)
            c.drawText("TOTAL", pw - m - 10f, y + 16f, hr)
            return y + 28f
        }

        var y = startPage(true)
        val cw = (pw - 2 * m - 12f) / 2f
        val x2 = m + cw + 12f
        for (x in listOf(m, x2)) {
            fill.color = soft
            c.drawRoundRect(RectF(x, y, x + cw, y + 84f), 10f, 10f, fill)
            c.drawRoundRect(RectF(x, y, x + cw, y + 84f), 10f, 10f, border)
        }
        c.drawText("FACTURÉ À", m + 12f, y + 18f, tp(primary, 8.5f, true))
        val nameP = tp(navy, 12f, true)
        c.drawText(fitText(invoice.customerName.ifBlank { "Client Comptant" }, nameP, cw - 24f), m + 12f, y + 38f, nameP)
        val grayP = tp(gray, 9f)
        listOf(invoice.customerPhone, invoice.customerAddress).filter { it.isNotBlank() }.take(2).forEachIndexed { i, t ->
            c.drawText(fitText(t, grayP, cw - 24f), m + 12f, y + 54f + i * 14f, grayP)
        }
        c.drawText("INFORMATIONS", x2 + 12f, y + 18f, tp(primary, 8.5f, true))
        val valP = tp(navy, 9.5f, true, Paint.Align.RIGHT)
        listOf("Émis par" to issuer, "Paiement" to invoice.paymentMethod, "Statut" to stLabel).forEachIndexed { i, (k, v) ->
            c.drawText(k, x2 + 12f, y + 38f + i * 16f, tp(gray, 9.5f))
            c.drawText(fitText(v, valP, cw - 100f), x2 + cw - 12f, y + 38f + i * 16f, valP)
        }

        y += 84f + 20f
        y = tableHeader(y)
        if (items.isEmpty()) {
            fill.color = soft
            c.drawRect(m, y, pw - m, y + 30f, fill)
            c.drawText("Prestation directe", m + 78f, y + 19f, tp(navy, 10f, true))
            c.drawText(Formatters.fcfa(invoice.subtotalHt), pw - m - 10f, y + 19f, tp(navy, 10f, true, Paint.Align.RIGHT))
            y += 30f
        }
        items.forEachIndexed { idx, item ->
            if (y + 34f > ph - 70f) { y = startPage(false); y = tableHeader(y) }
            if (idx % 2 == 0) {
                fill.color = soft
                c.drawRect(m, y, pw - m, y + 34f, fill)
            }
            val code = item.productInvoiceCode.ifBlank { Formatters.productInvoiceCode(invoice.invoiceNumber, item.categoryName) }
            val cb = item.productBarcode.ifBlank { item.sku }
            val g8 = tp(gray, 8f)
            val bold10 = tp(navy, 10f, true)
            val right10 = tp(navy, 10f, false, Paint.Align.RIGHT)
            c.drawText(fitText(code, g8, 62f), m + 10f, y + 14f, g8)
            c.drawText(fitText(item.productName, bold10, 226f), m + 78f, y + 14f, bold10)
            if (cb.isNotBlank()) c.drawText(fitText("CB : $cb", g8, 226f), m + 78f, y + 27f, g8)
            c.drawText(item.quantity.toString(), 372f, y + 19f, right10)
            c.drawText(fitText(Formatters.fcfa(item.unitPrice), right10, 84f), 462f, y + 19f, right10)
            c.drawText(Formatters.fcfa(item.lineTotal), pw - m - 10f, y + 19f, tp(navy, 10f, true, Paint.Align.RIGHT))
            y += 34f
        }

        if (y + 200f > ph - 160f) y = startPage(false)
        val tx = 330f
        var ry = y + 18f
        listOf(
            "Sous-total HT" to Formatters.fcfa(invoice.subtotalHt),
            "Remise" to Formatters.fcfa(invoice.discountAmount),
            "TVA (${Formatters.percent(invoice.taxRate)})" to Formatters.fcfa(invoice.taxAmount)
        ).forEach { (k, v) ->
            c.drawText(k, tx, ry + 12f, tp(gray, 10f))
            c.drawText(v, pw - m, ry + 12f, tp(navy, 10f, true, Paint.Align.RIGHT))
            fill.color = rule
            c.drawRect(tx, ry + 20f, pw - m, ry + 21f, fill)
            ry += 24f
        }
        fill.color = primary
        c.drawRoundRect(RectF(tx, ry + 4f, pw - m, ry + 40f), 8f, 8f, fill)
        c.drawText("TOTAL TTC", tx + 12f, ry + 27f, tp(Color.WHITE, 11f, true))
        c.drawText(Formatters.fcfa(invoice.totalTtc), pw - m - 12f, ry + 28f, tp(Color.WHITE, 14f, true, Paint.Align.RIGHT))
        ry += 58f
        c.drawText("Montant payé", tx, ry + 4f, tp(gray, 10f))
        c.drawText(Formatters.fcfa(invoice.amountPaid), pw - m, ry + 4f, tp(navy, 10f, true, Paint.Align.RIGHT))
        ry += 20f
        if (invoice.balanceDue > 0.0) {
            c.drawText("Reste à payer", tx, ry + 4f, tp(danger, 10f, true))
            c.drawText(Formatters.fcfa(invoice.balanceDue), pw - m, ry + 4f, tp(danger, 10f, true, Paint.Align.RIGHT))
        } else if (changeGiven != null) {
            c.drawText("Monnaie rendue", tx, ry + 4f, tp(gray, 10f))
            c.drawText(Formatters.fcfa(changeGiven), pw - m, ry + 4f, tp(navy, 10f, true, Paint.Align.RIGHT))
        }

        // Tampon de statut
        val cx = m + 120f
        val cy = y + 78f
        c.save()
        c.rotate(-10f, cx, cy)
        val stampBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = stColor }
        c.drawRoundRect(RectF(cx - 70f, cy - 22f, cx + 70f, cy + 22f), 9f, 9f, stampBorder)
        c.drawText(stLabel, cx, cy + 8f, tp(stColor, 22f, true, Paint.Align.CENTER))
        c.restore()

        // Code-barres de la facture + zone de signature
        val by = ph - 150f
        fill.color = Color.WHITE
        c.drawRoundRect(RectF(m, by, m + 250f, by + 84f), 10f, 10f, fill)
        c.drawRoundRect(RectF(m, by, m + 250f, by + 84f), 10f, 10f, border)
        c.drawText("CODE-BARRES FACTURE", m + 14f, by + 14f, tp(primary, 8f, true))
        val matrix = runCatching { Code128Writer().encode(invoice.invoiceNumber, BarcodeFormat.CODE_128, 0, 1) }.getOrNull()
        if (matrix != null) {
            val n = matrix.width
            val bw = minOf(1.8f, 222f / n)
            val x0 = m + 14f + (222f - bw * n) / 2f
            fill.color = navy
            for (i in 0 until n) {
                if (matrix[i, 0]) c.drawRect(x0 + i * bw, by + 20f, x0 + (i + 1) * bw + 0.2f, by + 58f, fill)
            }
        }
        c.drawText(invoice.invoiceNumber, m + 125f, by + 74f, tp(navy, 10f, true, Paint.Align.CENTER))
        c.drawRoundRect(RectF(tx, by, pw - m, by + 84f), 10f, 10f, border)
        c.drawText("Cachet & signature", tx + 12f, by + 14f, tp(gray, 8.5f, true))

        drawFooter()
        doc.finishPage(page)
        doc.writeTo(outputStream)
        doc.close()
    }

    fun writePdfReport(
        outputStream: OutputStream,
        title: String,
        subtitle: String,
        lines: List<String>
    ) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.parseColor("#059669")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint().apply {
            color = Color.parseColor("#475569")
            textSize = 11f
        }
        val bodyPaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 10f
            typeface = Typeface.MONOSPACE
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1f
        }

        fun drawHeader(c: android.graphics.Canvas) {
            val band = Paint().apply { color = Color.parseColor("#065F46") }
            val strip = Paint().apply { color = Color.parseColor("#F59E0B") }
            c.drawRect(0f, 0f, pageWidth.toFloat(), 74f, band)
            c.drawRect(0f, 74f, pageWidth.toFloat(), 79f, strip)
            val white = Paint(titlePaint).apply { color = Color.WHITE }
            val light = Paint(subPaint).apply { color = Color.parseColor("#D1FAE5") }
            c.drawText("SMART STORE PRO — $title", 36f, 34f, white)
            c.drawText(subtitle, 36f, 56f, light)
        }

        drawHeader(canvas)
        var y = 96f
        for (line in lines) {
            if (y > pageHeight - 48f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                drawHeader(canvas)
                y = 96f
            }
            canvas.drawText(line.take(92), 36f, y, bodyPaint)
            y += 16f
        }
        doc.finishPage(page)
        doc.writeTo(outputStream)
        doc.close()
    }

    fun writeCsvBytes(outputStream: OutputStream, header: List<String>, rows: List<List<String>>) {
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.append(header.joinToString(";") { csvEscape(it) }).append("\n")
        for (row in rows) {
            sb.append(row.joinToString(";") { csvEscape(it) }).append("\n")
        }
        outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
    }

    fun copyUriToInternalDocuments(context: Context, uri: Uri): ImportedFileInfo {
        val cr = context.contentResolver
        val mimeType = cr.getType(uri) ?: "application/octet-stream"
        var displayName = "document_${System.currentTimeMillis()}"
        cr.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                displayName = cursor.getString(idx) ?: displayName
            }
        }
        val safeName = displayName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
        val ext = safeName.substringAfterLast('.', "").lowercase()
        val dir = File(context.filesDir, "documents").apply { mkdirs() }
        val target = File(dir, "${System.currentTimeMillis()}_$safeName")

        cr.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("Impossible de lire le fichier sélectionné")

        val textPreview = if (ext in setOf("csv", "txt", "json", "tsv", "html", "xml", "log") || mimeType.startsWith("text/")) {
            target.readText(Charsets.UTF_8)
        } else {
            null
        }

        return ImportedFileInfo(
            fileName = displayName,
            mimeType = mimeType,
            extension = ext.ifBlank { "bin" },
            localPath = target.absolutePath,
            sizeBytes = target.length(),
            textPreviewIfText = textPreview
        )
    }

    fun saveInternalDocument(
        context: Context,
        fileName: String,
        bytes: ByteArray
    ): File {
        val dir = File(context.filesDir, "documents").apply { mkdirs() }
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
        val file = File(dir, safeName)
        file.writeBytes(bytes)
        return file
    }

    fun openDocumentIntent(context: Context, filePath: String, mimeType: String): Intent? {
        val file = File(filePath)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun shareDocumentIntent(context: Context, filePath: String, mimeType: String, title: String): Intent? {
        val file = File(filePath)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType.ifBlank { "*/*" }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            title
        )
    }

    private fun csvEscape(value: String): String {
        val cleaned = value.replace("\r", " ").replace("\n", " ")
        return if (cleaned.contains(';') || cleaned.contains('"') || cleaned.contains(',')) {
            "\"${cleaned.replace("\"", "\"\"")}\""
        } else {
            cleaned
        }
    }

    private fun escapeHtml(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
