package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.oned.Code128Writer
import com.google.zxing.qrcode.QRCodeWriter

object BarcodeSvgHelper {

    fun generateBarcodeBitmap(
        rawCode: String,
        widthPx: Int = 560,
        heightPx: Int = 110
    ): Bitmap? {
        val code = sanitizeBarcodeText(rawCode)
        return try {
            val matrix = Code128Writer().encode(code, BarcodeFormat.CODE_128, widthPx, heightPx)
            matrixToBitmap(matrix)
        } catch (_: Exception) {
            try {
                val matrix = MultiFormatWriter().encode(code, BarcodeFormat.CODE_128, widthPx, heightPx)
                matrixToBitmap(matrix)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun generateQrBitmap(content: String, sizePx: Int = 420): Bitmap? {
        val safe = content.ifBlank { "SMART-STORE-PRO" }
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val matrix = QRCodeWriter().encode(safe, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            matrixToBitmap(matrix)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates a real, crisp, scannable inline SVG Code-128 barcode for HTML/Print invoices
     * and the synchronized LAN Computer portal.
     */
    fun buildCode128Svg(rawCode: String, heightPx: Int = 44): String {
        val code = sanitizeBarcodeText(rawCode)
        return try {
            val matrix = Code128Writer().encode(code, BarcodeFormat.CODE_128, 0, 1)
            val width = matrix.width.coerceAtLeast(1)
            val rects = StringBuilder()
            var runStart = -1
            for (x in 0 until width) {
                val black = matrix[x, 0]
                if (black && runStart == -1) {
                    runStart = x
                } else if (!black && runStart != -1) {
                    rects.append("<rect x=\"$runStart\" y=\"0\" width=\"${x - runStart}\" height=\"$heightPx\" fill=\"#0f172a\"/>")
                    runStart = -1
                }
            }
            if (runStart != -1) {
                rects.append("<rect x=\"$runStart\" y=\"0\" width=\"${width - runStart}\" height=\"$heightPx\" fill=\"#0f172a\"/>")
            }
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $width $heightPx" preserveAspectRatio="none" style="width:100%;max-width:210px;height:${heightPx}px;background:#ffffff;display:block;">$rects</svg>"""
        } catch (_: Exception) {
            """<div style="font-family:monospace;font-size:11px;border:1px solid #cbd5e1;padding:2px 6px;">${escapeXml(code)}</div>"""
        }
    }

    private fun matrixToBitmap(matrix: BitMatrix): Bitmap {
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    fun sanitizeBarcodeText(raw: String): String {
        val ascii = raw.trim().replace(Regex("[^\\x20-\\x7E]"), "")
        return ascii.ifBlank { "617000000001" }.take(48)
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
