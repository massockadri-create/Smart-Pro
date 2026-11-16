package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
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
            c.drawText("SMART STORE PRO — $title", 36f, 44f, titlePaint)
            c.drawText(subtitle, 36f, 62f, subPaint)
            c.drawLine(36f, 72f, (pageWidth - 36).toFloat(), 72f, linePaint)
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
