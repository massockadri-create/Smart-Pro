package com.example.util

import com.example.data.local.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

data class DesktopSnapshotState(
    val settings: StoreSettingsEntity = StoreSettingsEntity(),
    val products: List<ProductEntity> = emptyList(),
    val sales: List<SaleEntity> = emptyList(),
    val saleItems: List<SaleItemEntity> = emptyList(),
    val invoices: List<InvoiceEntity> = emptyList(),
    val employees: List<UserEntity> = emptyList(),
    val onlinePayments: List<OnlinePaymentEntity> = emptyList(),
    val notifications: List<AlertNotificationEntity> = emptyList(),
    val todaySalesFcfa: Double = 0.0,
    val monthSalesFcfa: Double = 0.0,
    val totalProfitFcfa: Double = 0.0
)

/**
 * Embedded LAN HTTP Server (ports 8080-8092) providing a real-time synchronized
 * Computer / Desktop web portal accessible from any PC browser on the local network.
 */
class DesktopWebServer {

    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val latestSnapshot = AtomicReference(DesktopSnapshotState())
    private val cachedSnapshotJson = AtomicReference("{}")

    @Volatile
    var activePort: Int = 0
        private set

    fun isRunning(): Boolean = running.get()

    fun updateSnapshot(state: DesktopSnapshotState) {
        latestSnapshot.set(state)
        cachedSnapshotJson.set(serializeSnapshot(state))
    }

    fun start(): Result<Int> {
        if (running.get() && activePort > 0) return Result.success(activePort)

        for (port in 8080..8092) {
            try {
                val ss = ServerSocket(port)
                serverSocket = ss
                activePort = port
                running.set(true)
                cachedSnapshotJson.set(serializeSnapshot(latestSnapshot.get()))
                thread(isDaemon = true, name = "SmartStoreDesktopHttp") {
                    while (running.get()) {
                        try {
                            val client = ss.accept()
                            thread(isDaemon = true) { handleClient(client) }
                        } catch (_: Exception) {
                            if (!running.get()) break
                        }
                    }
                }
                return Result.success(port)
            } catch (_: Exception) {
                // Try next port
            }
        }
        return Result.failure(IllegalStateException("Aucun port disponible (8080-8092)"))
    }

    fun stop() {
        running.set(false)
        activePort = 0
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    fun getLanUrl(): String {
        val ip = detectLocalIpv4() ?: "127.0.0.1"
        val p = if (activePort > 0) activePort else 8080
        return "http://$ip:$p"
    }

    fun detectLocalIpv4(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            val candidates = mutableListOf<String>()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        candidates.add(addr.hostAddress ?: continue)
                    }
                }
            }
            candidates.firstOrNull { it.startsWith("192.168.") || it.startsWith("10.") || it.startsWith("172.") }
                ?: candidates.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 4000
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val path = requestLine.split(" ").getOrNull(1)?.substringBefore("?") ?: "/"
            val out = s.getOutputStream()

            val (status, contentType, bodyBytes) = when (path) {
                "/api/snapshot", "/api/sync" -> {
                    Triple("200 OK", "application/json; charset=utf-8", cachedSnapshotJson.get().toByteArray(Charsets.UTF_8))
                }
                "/api/catalog.csv" -> {
                    Triple("200 OK", "text/csv; charset=utf-8", buildCatalogCsv().toByteArray(Charsets.UTF_8))
                }
                else -> {
                    Triple("200 OK", "text/html; charset=utf-8", buildDesktopPortalHtml().toByteArray(Charsets.UTF_8))
                }
            }

            val headers = buildString {
                append("HTTP/1.1 $status\r\n")
                append("Content-Type: $contentType\r\n")
                append("Content-Length: ${bodyBytes.size}\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("Cache-Control: no-store, no-cache\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray(Charsets.US_ASCII)

            out.write(headers)
            out.write(bodyBytes)
            out.flush()
        }
    }

    private fun buildCatalogCsv(): String {
        val snap = latestSnapshot.get()
        val sb = StringBuilder("\uFEFF")
        sb.append("Nom;CodeFactureCat;SKU;CodeBarres;Categorie;PrixAchat;PrixVente;Stock;SeuilMin\n")
        for (p in snap.products) {
            sb.append("${csv(p.name)};${csv(p.reference)};${csv(p.sku)};${csv(p.barcode)};${csv(p.categoryName)};${p.purchasePrice.toLong()};${p.sellingPrice.toLong()};${p.stockQuantity};${p.minThreshold}\n")
        }
        return sb.toString()
    }

    private fun csv(s: String): String = "\"${s.replace("\"", "\"\"")}\""

    private fun serializeSnapshot(state: DesktopSnapshotState): String {
        val s = state.settings
        val now = System.currentTimeMillis()
        val itemsBySale = state.saleItems.groupBy { it.saleId }

        val json = JSONObject()
        json.put("serverTimeMs", now)
        json.put("serverTimeFormatted", Formatters.dateTimeWithSeconds(now))
        json.put("store", JSONObject().apply {
            put("name", s.storeName.ifBlank { "Structure non configurée" })
            put("city", s.city)
            put("location", s.location)
            put("address", s.address)
            put("phone", s.phone)
            put("email", s.email)
            put("currency", s.currency)
            put("structureType", s.structureType)
            put("logoKey", s.logoKey)
            put("logoSvg", StructureCatalog.buildStructureLogoSvg(s.logoKey, 48))
        })
        json.put("kpis", JSONObject().apply {
            put("todaySalesFcfa", state.todaySalesFcfa)
            put("monthSalesFcfa", state.monthSalesFcfa)
            put("totalProfitFcfa", state.totalProfitFcfa)
            put("productsCount", state.products.size)
            put("invoicesCount", state.invoices.size)
            put("employeesCount", state.employees.size)
            put("onlinePaymentsCount", state.onlinePayments.size)
            put("unreadAlertsCount", state.notifications.count { !it.isRead })
        })
        json.put("products", JSONArray(state.products.map { p ->
            val barcodeVal = p.barcode.ifBlank { p.sku.ifBlank { p.reference } }
            JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("reference", p.reference)
                put("sku", p.sku)
                put("barcode", barcodeVal)
                put("barcodeSvg", BarcodeSvgHelper.buildCode128Svg(barcodeVal, 26))
                put("category", p.categoryName)
                put("purchasePrice", p.purchasePrice)
                put("sellingPrice", p.sellingPrice)
                put("stockQuantity", p.stockQuantity)
                put("minThreshold", p.minThreshold)
                put("unit", p.unit)
            }
        }))
        json.put("invoices", JSONArray(state.invoices.take(50).map { inv ->
            val lookupSaleId = if (inv.saleId > 0L) inv.saleId else -inv.id
            val invItems = itemsBySale[lookupSaleId].orEmpty()
            JSONObject().apply {
                put("id", inv.id)
                put("invoiceNumber", inv.invoiceNumber)
                put("invoiceType", inv.invoiceType)
                put("customerName", inv.customerName)
                put("totalTtc", inv.totalTtc)
                put("amountPaid", inv.amountPaid)
                put("balanceDue", inv.balanceDue)
                put("paymentMethod", inv.paymentMethod)
                put("status", inv.status)
                put("issuedByName", inv.issuedByName)
                put("createdAtText", Formatters.dateTimeWithSeconds(inv.createdAt))
                put("invoiceBarcodeSvg", BarcodeSvgHelper.buildCode128Svg(inv.invoiceNumber, 30))
                put("items", JSONArray(invItems.map { item ->
                    val bCode = item.productBarcode.ifBlank { item.sku.ifBlank { item.productInvoiceCode } }
                    val invCode = item.productInvoiceCode.ifBlank { Formatters.productInvoiceCode(inv.invoiceNumber, item.categoryName) }
                    JSONObject().apply {
                        put("productName", item.productName)
                        put("categoryName", item.categoryName)
                        put("productInvoiceCode", invCode)
                        put("barcode", bCode)
                        put("barcodeSvg", BarcodeSvgHelper.buildCode128Svg(bCode, 28))
                        put("quantity", item.quantity)
                        put("unitPrice", item.unitPrice)
                        put("lineTotal", item.lineTotal)
                    }
                }))
            }
        }))
        json.put("employees", JSONArray(state.employees.map { u ->
            JSONObject().apply {
                put("id", u.id)
                put("fullName", u.fullName)
                put("username", u.username)
                put("roleCode", u.roleCode)
                put("positionTitle", u.positionTitle)
                put("phone", u.phone)
                put("whatsapp", u.whatsappNumber)
                put("email", u.email)
                put("isActive", u.isActive)
            }
        }))
        json.put("onlinePayments", JSONArray(state.onlinePayments.take(40).map { op ->
            JSONObject().apply {
                put("transactionId", op.transactionId)
                put("provider", op.provider)
                put("customerName", op.customerName)
                put("customerPhone", op.customerPhone)
                put("amountXaf", op.amountXaf)
                put("status", op.status)
                put("createdAtText", Formatters.dateTimeWithSeconds(op.createdAt))
            }
        }))
        json.put("alerts", JSONArray(state.notifications.take(25).map { n ->
            JSONObject().apply {
                put("title", n.title)
                put("message", n.message)
                put("severity", n.severity)
                put("createdAtText", Formatters.dateTimeWithSeconds(n.createdAt))
            }
        }))
        return json.toString()
    }

    private fun buildDesktopPortalHtml(): String = """
        <!DOCTYPE html>
        <html lang="fr">
        <head>
        <meta charset="utf-8"/>
        <meta name="viewport" content="width=device-width,initial-scale=1"/>
        <title>SMART STORE PRO — Poste Ordinateur Synchronisé</title>
        <link rel="preconnect" href="https://fonts.googleapis.com"/>
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin/>
        <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&display=swap" rel="stylesheet"/>
        <style>
          :root {
            --bg:#F8FAFC;
            --card:#FFFFFF;
            --border:#E2E8F0;
            --violet:#5B3FD3;
            --violet-hover:#4A2FC0;
            --midnight:#172554;
            --gold:#F5C542;
            --green:#16A34A;
            --txt:#111827;
            --mut:#64748B;
          }
          * { box-sizing:border-box; }
          body { margin:0; font-family:'Inter',system-ui,-apple-system,sans-serif; background:var(--bg); color:var(--txt); line-height:1.45; }
          .topbar { display:flex; justify-content:space-between; align-items:center; padding:16px 28px; background:var(--midnight); color:#fff; border-bottom:1px solid rgba(255,255,255,0.12); flex-wrap:wrap; gap:14px; }
          .brand { display:flex; align-items:center; gap:14px; }
          .pill { display:inline-flex; align-items:center; gap:8px; padding:7px 14px; border-radius:999px; background:rgba(22,163,74,0.18); border:1px solid rgba(22,163,74,0.45); color:#DCFCE7; font-size:12px; font-weight:600; }
          .gold-pill { display:inline-flex; align-items:center; gap:6px; padding:6px 12px; border-radius:999px; background:rgba(245,197,66,0.18); border:1px solid rgba(245,197,66,0.55); color:var(--gold); font-size:12px; font-weight:600; }
          .dot { width:8px; height:8px; border-radius:50%; background:var(--green); animation: pulse 1.2s infinite; }
          @keyframes pulse { 0%,100%{opacity:1} 50%{opacity:0.35} }
          .container { max-width:1360px; margin:0 auto; padding:24px; }
          .grid { display:grid; grid-template-columns:repeat(auto-fit, minmax(220px, 1fr)); gap:16px; margin-bottom:22px; }
          .kpi { background:var(--card); border:1px solid var(--border); border-radius:16px; padding:18px; box-shadow:0 2px 10px rgba(15,23,42,0.04); transition:transform 160ms ease, box-shadow 160ms ease; }
          .kpi:hover { transform:translateY(-2px); box-shadow:0 8px 20px rgba(91,63,211,0.08); }
          .kpi-label { color:var(--mut); font-size:12px; font-weight:600; letter-spacing:0.02em; }
          .kpi-val { font-size:22px; font-weight:700; margin-top:6px; color:var(--midnight); }
          .kpi-val.violet { color:var(--violet); }
          .kpi-val.green { color:var(--green); }
          .tabs { display:flex; gap:10px; flex-wrap:wrap; margin-bottom:18px; }
          .tab { background:var(--card); color:var(--txt); border:1px solid var(--border); border-radius:14px; padding:10px 18px; cursor:pointer; font-family:inherit; font-weight:600; font-size:13px; transition:all 150ms ease; }
          .tab:hover { border-color:var(--violet); color:var(--violet); background:#F5F3FF; }
          .tab.active { background:var(--violet); color:#fff; border-color:var(--violet); box-shadow:0 4px 12px rgba(91,63,211,0.22); }
          .panel { background:var(--card); border:1px solid var(--border); border-radius:16px; padding:20px; box-shadow:0 2px 12px rgba(15,23,42,0.04); overflow-x:auto; }
          table { width:100%; border-collapse:collapse; font-size:13px; }
          th { text-align:left; padding:12px 10px; border-bottom:1px solid var(--border); color:var(--mut); font-weight:600; }
          td { padding:12px 10px; border-bottom:1px solid #F1F5F9; vertical-align:middle; }
          tr:hover td { background:#F8FAFC; }
          .btn { background:var(--violet); color:#fff; border:none; border-radius:12px; padding:8px 14px; font-family:inherit; font-weight:600; cursor:pointer; text-decoration:none; display:inline-block; transition:background 150ms ease, transform 150ms ease; }
          .btn:hover { background:var(--violet-hover); transform:translateY(-1px); }
          .btn-sec { background:rgba(255,255,255,0.12); color:#fff; border:1px solid rgba(255,255,255,0.25); }
          .btn-sec:hover { background:rgba(255,255,255,0.2); }
          .badge { padding:4px 10px; border-radius:10px; font-size:11px; font-weight:600; background:#EDE9FE; color:var(--violet); display:inline-block; }
          .badge-green { background:#DCFCE7; color:#15803D; }
          .bc-box { background:#fff; color:#111827; padding:5px 8px; border:1px solid var(--border); border-radius:10px; display:inline-block; margin-top:4px; }
          @media print { .no-print { display:none !important; } body { background:#fff; color:#000; } .panel { border:none; box-shadow:none; padding:0; } }
        </style>
        </head>
        <body>
          <div class="topbar no-print">
            <div class="brand">
              <div id="storeLogo"></div>
              <div>
                <div id="storeName" style="font-size:18px;font-weight:700;">SMART STORE PRO</div>
                <div id="storeMeta" style="font-size:12px;color:#CBD5E1;">Poste Ordinateur Synchronisé</div>
              </div>
            </div>
            <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap;">
              <div class="gold-pill">POSTE DESKTOP</div>
              <div class="pill"><span class="dot"></span> SYNCHRO TEMPS RÉEL · <span id="clock">--:--:--</span></div>
              <a class="btn btn-sec" href="/api/catalog.csv">Exporter Catalogue CSV</a>
            </div>
          </div>
          <div class="container">
            <div class="grid no-print">
              <div class="kpi"><div class="kpi-label">Ventes du jour</div><div class="kpi-val violet" id="kToday">0 FCFA</div></div>
              <div class="kpi"><div class="kpi-label">CA Mensuel</div><div class="kpi-val green" id="kMonth">0 FCFA</div></div>
              <div class="kpi"><div class="kpi-label">Factures émises</div><div class="kpi-val" id="kInv">0</div></div>
              <div class="kpi"><div class="kpi-label">Articles catalogue</div><div class="kpi-val" id="kProd">0</div></div>
              <div class="kpi"><div class="kpi-label">Équipe & Employés</div><div class="kpi-val" id="kEmp">0</div></div>
            </div>
            <div class="tabs no-print">
              <button class="tab active" onclick="setTab('invoices', this)">Factures & Codes-barres</button>
              <button class="tab" onclick="setTab('products', this)">Catalogue & Stocks</button>
              <button class="tab" onclick="setTab('employees', this)">Employés & Contacts</button>
              <button class="tab" onclick="setTab('payments', this)">Paiements en ligne</button>
              <button class="tab" onclick="setTab('alerts', this)">Alertes</button>
            </div>
            <div class="panel" id="content">Chargement des données en temps réel...</div>
          </div>
          <script>
            let activeTab = 'invoices';
            let lastData = null;
            const fmt = (n) => Math.round(n || 0).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ' ') + ' FCFA';
            const esc = (s) => (s || '').toString().replace(/[&<>"']/g, m => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]));

            function setTab(t, btn) {
              activeTab = t;
              document.querySelectorAll('.tab').forEach(b => b.classList.remove('active'));
              if (btn) btn.classList.add('active');
              render();
            }

            function printInvoiceByIndex(idx) {
              if (!lastData || !lastData.invoices[idx]) return;
              const inv = lastData.invoices[idx];
              const st = lastData.store;
              const rows = (inv.items || []).map(it =>
                '<tr><td><strong>' + esc(it.productName) + '</strong><br><small style="color:#5B3FD3;font-weight:600;">' + esc(it.productInvoiceCode) + '</small></td>' +
                '<td><div class="bc-box">' + it.barcodeSvg + '<div style="font-size:10px;text-align:center;">' + esc(it.barcode) + '</div></div></td>' +
                '<td style="text-align:center;">' + it.quantity + '</td>' +
                '<td style="text-align:right;">' + fmt(it.unitPrice) + '</td>' +
                '<td style="text-align:right;font-weight:700;">' + fmt(it.lineTotal) + '</td></tr>'
              ).join('');
              const win = window.open('', '_blank', 'width=860,height=720');
              win.document.write('<html><head><title>Facture ' + esc(inv.invoiceNumber) + '</title><style>body{font-family:Inter,system-ui,sans-serif;padding:24px;color:#111827}table{width:100%;border-collapse:collapse;margin-top:14px}th{background:#172554;color:#fff;padding:10px;text-align:left}td{padding:10px;border-bottom:1px solid #E2E8F0}.bc-box{display:inline-block;padding:4px;border:1px solid #E2E8F0;border-radius:8px}</style></head><body>' +
                '<div style="display:flex;justify-content:space-between;border-bottom:2px solid #5B3FD3;padding-bottom:12px;">' +
                '<div style="display:flex;gap:12px;align-items:center;">' + st.logoSvg + '<div><h2 style="margin:0;color:#172554;">' + esc(st.name) + '</h2><div>' + esc(st.city) + (st.location ? ' · ' + esc(st.location) : '') + '</div><div>' + esc(st.phone) + ' · ' + esc(st.email) + '</div></div></div>' +
                '<div style="text-align:right;"><h3 style="margin:0;color:#5B3FD3;">' + esc(inv.invoiceType) + ' ' + esc(inv.invoiceNumber) + '</h3><div>' + esc(inv.createdAtText) + '</div><div>Client : <strong>' + esc(inv.customerName) + '</strong></div></div></div>' +
                '<table><thead><tr><th>Produit & Code Catégorie</th><th>Code-barres</th><th>Qté</th><th>Prix U.</th><th>Total</th></tr></thead><tbody>' + rows + '</tbody></table>' +
                '<div style="margin-top:18px;display:flex;justify-content:space-between;align-items:center;"><div>' + inv.invoiceBarcodeSvg + '<div style="font-size:11px;font-weight:600;">' + esc(inv.invoiceNumber) + '</div></div><div style="text-align:right;font-size:18px;font-weight:700;color:#5B3FD3;">TOTAL TTC : ' + fmt(inv.totalTtc) + '</div></div>' +
                '<script>window.onload=function(){window.print();}</' + 'script></body></html>');
              win.document.close();
            }

            function render() {
              if (!lastData) return;
              const s = lastData.store;
              document.getElementById('storeLogo').innerHTML = s.logoSvg || '';
              document.getElementById('storeName').textContent = s.name || 'SMART STORE PRO';
              document.getElementById('storeMeta').textContent = [s.city, s.location, s.phone, s.email].filter(Boolean).join(' · ') || 'Poste Ordinateur Synchronisé';
              document.getElementById('clock').textContent = lastData.serverTimeFormatted;
              document.getElementById('kToday').textContent = fmt(lastData.kpis.todaySalesFcfa);
              document.getElementById('kMonth').textContent = fmt(lastData.kpis.monthSalesFcfa);
              document.getElementById('kInv').textContent = lastData.kpis.invoicesCount;
              document.getElementById('kProd').textContent = lastData.kpis.productsCount;
              document.getElementById('kEmp').textContent = lastData.kpis.employeesCount;

              const el = document.getElementById('content');
              if (activeTab === 'invoices') {
                if (!lastData.invoices.length) { el.innerHTML = '<p style="color:#64748B;">Aucune facture enregistrée pour le moment.</p>'; return; }
                el.innerHTML = '<table><thead><tr><th>N° Facture</th><th>Client</th><th>Articles & Codes-barres</th><th>Total TTC</th><th>Statut</th><th>Action PC</th></tr></thead><tbody>' +
                  lastData.invoices.map((inv, i) => {
                    const itemsHtml = (inv.items || []).map(it =>
                      '<div style="margin-bottom:6px;"><strong>' + esc(it.productName) + '</strong> ×' + it.quantity + ' · <span style="color:#5B3FD3;font-weight:600;">' + esc(it.productInvoiceCode) + '</span><br><div class="bc-box">' + it.barcodeSvg + '<div style="font-size:10px;text-align:center;">' + esc(it.barcode) + '</div></div></div>'
                    ).join('') || '<span class="badge">Facture directe</span>';
                    const stCls = inv.status === 'PAYEE' ? 'badge badge-green' : 'badge';
                    return '<tr><td><strong>' + esc(inv.invoiceNumber) + '</strong><br><small style="color:#64748B;">' + esc(inv.createdAtText) + '</small></td><td>' + esc(inv.customerName) + '<br><small style="color:#64748B;">' + esc(inv.paymentMethod) + '</small></td><td>' + itemsHtml + '</td><td style="font-weight:700;color:#172554;">' + fmt(inv.totalTtc) + '</td><td><span class="' + stCls + '">' + esc(inv.status) + '</span></td><td><button class="btn" onclick="printInvoiceByIndex(' + i + ')">Imprimer</button></td></tr>';
                  }).join('') + '</tbody></table>';
              } else if (activeTab === 'products') {
                if (!lastData.products.length) { el.innerHTML = '<p style="color:#64748B;">Catalogue vide. Ajoutez vos produits sur le terminal.</p>'; return; }
                el.innerHTML = '<table><thead><tr><th>Produit</th><th>Code Auto</th><th>Catégorie</th><th>Code-barres</th><th>Prix Vente</th><th>Stock</th></tr></thead><tbody>' +
                  lastData.products.map(p => '<tr><td><strong>' + esc(p.name) + '</strong><br><small style="color:#64748B;">' + esc(p.sku) + '</small></td><td style="color:#5B3FD3;font-weight:600;">' + esc(p.reference) + '</td><td>' + esc(p.category) + '</td><td><div class="bc-box">' + p.barcodeSvg + '<div style="font-size:10px;text-align:center;">' + esc(p.barcode) + '</div></div></td><td style="font-weight:700;">' + fmt(p.sellingPrice) + '</td><td>' + p.stockQuantity + ' ' + esc(p.unit) + '</td></tr>').join('') +
                  '</tbody></table>';
              } else if (activeTab === 'employees') {
                if (!lastData.employees.length) { el.innerHTML = '<p style="color:#64748B;">Aucun employé enregistré.</p>'; return; }
                el.innerHTML = '<table><thead><tr><th>Nom & Poste</th><th>Rôle</th><th>Téléphone</th><th>WhatsApp</th><th>Email</th><th>Statut</th></tr></thead><tbody>' +
                  lastData.employees.map(u => '<tr><td><strong>' + esc(u.fullName) + '</strong><br><small style="color:#64748B;">@' + esc(u.username) + (u.positionTitle ? ' · ' + esc(u.positionTitle) : '') + '</small></td><td><span class="badge">' + esc(u.roleCode) + '</span></td><td>' + esc(u.phone || '—') + '</td><td>' + esc(u.whatsapp || '—') + '</td><td>' + esc(u.email || '—') + '</td><td><span class="' + (u.isActive ? 'badge badge-green' : 'badge') + '">' + (u.isActive ? 'ACTIF' : 'RÉVOQUÉ') + '</span></td></tr>').join('') +
                  '</tbody></table>';
              } else if (activeTab === 'payments') {
                if (!lastData.onlinePayments.length) { el.innerHTML = '<p style="color:#64748B;">Aucun paiement en ligne enregistré.</p>'; return; }
                el.innerHTML = '<table><thead><tr><th>Réf.</th><th>Passerelle</th><th>Client</th><th>Montant</th><th>Statut</th><th>Date</th></tr></thead><tbody>' +
                  lastData.onlinePayments.map(op => '<tr><td style="font-weight:600;">' + esc(op.transactionId) + '</td><td>' + esc(op.provider) + '</td><td>' + esc(op.customerName) + '<br><small style="color:#64748B;">' + esc(op.customerPhone) + '</small></td><td style="font-weight:700;">' + fmt(op.amountXaf) + '</td><td><span class="' + (op.status === 'CONFIRME' ? 'badge badge-green' : 'badge') + '">' + esc(op.status) + '</span></td><td>' + esc(op.createdAtText) + '</td></tr>').join('') +
                  '</tbody></table>';
              } else {
                if (!lastData.alerts.length) { el.innerHTML = '<p style="color:#64748B;">Aucune alerte active.</p>'; return; }
                el.innerHTML = '<table><thead><tr><th>Gravité</th><th>Titre</th><th>Message</th><th>Date</th></tr></thead><tbody>' +
                  lastData.alerts.map(a => '<tr><td><span class="badge">' + esc(a.severity) + '</span></td><td><strong>' + esc(a.title) + '</strong></td><td>' + esc(a.message) + '</td><td>' + esc(a.createdAtText) + '</td></tr>').join('') +
                  '</tbody></table>';
              }
            }

            async function refresh() {
              try {
                const res = await fetch('/api/snapshot');
                if (res.ok) { lastData = await res.json(); render(); }
              } catch (e) {}
            }
            refresh();
            setInterval(refresh, 2500);
          </script>
        </body>
        </html>
    """.trimIndent()
}
