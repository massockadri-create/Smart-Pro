package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ProductEntity
import com.example.data.local.SeedDataInitializer
import com.example.data.remote.CloudSyncRepository
import com.example.data.repository.CartLine
import com.example.data.repository.StoreRepository
import com.example.security.RoleCode
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartStoreEmptyAndFlowTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: StoreRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val app = if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.smartstorepro.cmrvxk")
                    .setProjectId("smartstore-unit-test")
                    .setApiKey("unit-test-key")
                    .build()
            )
        } else {
            FirebaseApp.getInstance()
        }
        val firestore = FirebaseFirestore.getInstance(app, "unit-test-db")
        repository = StoreRepository(db, CloudSyncRepository(firestore))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun initialDatabase_hasZeroDemoData() = runBlocking {
        val hasUsers = repository.ensureSeeded()
        assertFalse("L'application doit démarrer sans aucun utilisateur démo", hasUsers)

        assertTrue(db.userDao().getAllUsersOnce().isEmpty())
        assertTrue(db.catalogDao().getCategoriesOnce().isEmpty())
        assertTrue(db.catalogDao().getProductsOnce().isEmpty())
        assertTrue(db.catalogDao().getCustomersOnce().isEmpty())
        assertTrue(db.catalogDao().getSuppliersOnce().isEmpty())
        assertTrue(db.salesDao().getSalesOnce().isEmpty())
        assertTrue(db.adminDao().getExpensesOnce().isEmpty())
        assertTrue(repository.invoices.first().isEmpty())
        assertTrue(repository.cashRegisters.first().isEmpty())
        assertTrue(repository.onlinePayments.first().isEmpty())
        assertTrue(repository.onlinePaymentConfigs.first().isEmpty())

        val settings = db.adminDao().getSettingsOnce()
        assertNotNull(settings)
        assertEquals("", settings!!.storeName)
        assertEquals("", settings.city)
        assertEquals("", settings.location)
    }

    @Test
    fun structureSetup_employeeHr_posSaleWithBarcode_andReset() = runBlocking {
        repository.ensureSeeded()

        val setupRes = repository.setupInitialStructureAndAdmin(
            storeName = "House of Chalenge",
            city = "Douala",
            location = "Bonapriso",
            address = "Rue de la Joie",
            storePhone = "+237 699 00 11 22",
            storeEmail = "contact@houseofchalenge.cm",
            structureType = "MODE",
            logoKey = "MODE",
            adminFullName = "Marie Ndi",
            adminUsername = "mariendi",
            adminPassword = "secret123",
            adminPhone = "+237 699 00 11 22",
            adminWhatsapp = "+237 699 00 11 22",
            adminEmail = "marie@houseofchalenge.cm",
            cloudUid = null
        )
        assertTrue(setupRes.isSuccess)
        val (admin, adminPerms) = setupRes.getOrThrow()
        assertEquals("ADMIN", admin.roleCode)
        assertTrue(adminPerms.isNotEmpty())

        val savedSettings = db.adminDao().getSettingsOnce()!!
        assertEquals("House of Chalenge", savedSettings.storeName)
        assertEquals("Douala", savedSettings.city)
        assertEquals("Bonapriso", savedSettings.location)
        assertEquals("MODE", savedSettings.logoKey)

        // Create and manage employee with phone, WhatsApp, email
        val empRes = repository.createStaffUser(
            actor = admin,
            username = "paul.caisse",
            fullName = "Paul Mbarga",
            rawPassword = "pass1234",
            roleCode = RoleCode.CASHIER.code,
            phone = "+237 677 11 22 33",
            whatsappNumber = "+237 677 11 22 33",
            email = "paul@houseofchalenge.cm",
            positionTitle = "Caissier Principal",
            salaryFcfa = 150000.0
        )
        assertTrue(empRes.isSuccess)
        val loginOk = repository.authenticateStaff("paul.caisse", "pass1234")
        assertTrue(loginOk.isSuccess)
        val cashier = loginOk.getOrThrow().first
        assertEquals("+237 677 11 22 33", cashier.whatsappNumber)

        // Open register, add product, sell on POS, verify invoice barcodes & stock update
        assertTrue(repository.openCashRegister(cashier, 25000.0).isSuccess)
        val prodId = repository.saveProduct(
            actor = admin,
            product = ProductEntity(
                name = "Veste Prestige",
                reference = "",
                sku = "VES-001",
                barcode = "617900123456",
                categoryId = 1L,
                categoryName = "Vêtements",
                purchasePrice = 18000.0,
                sellingPrice = 32000.0,
                stockQuantity = 10,
                minThreshold = 2
            ),
            isNew = true,
            cloudUid = null
        )
        val prod = db.catalogDao().getProductById(prodId)!!

        val saleRes = repository.validateSale(
            actor = cashier,
            cart = listOf(CartLine(prod, 2)),
            customer = null,
            discountAmount = 0.0,
            paymentMethod = "Espèces",
            amountPaid = 64000.0,
            paymentRef = "",
            cloudUid = null
        )
        assertTrue(saleRes.isSuccess)
        val (sale, invoice) = saleRes.getOrThrow()
        assertEquals(64000.0, sale.totalAmount, 0.01)
        assertEquals(28000.0, sale.profit, 0.01)

        val saleItems = db.salesDao().getSaleItems(sale.id)
        assertEquals(1, saleItems.size)
        assertEquals("617900123456", saleItems.first().productBarcode)
        assertTrue(saleItems.first().productInvoiceCode.startsWith(invoice.invoiceNumber))

        assertEquals(8, db.catalogDao().getProductById(prodId)!!.stockQuantity)
        assertEquals(89000.0, db.cashDao().getOpenRegisterOnce()!!.theoreticalBalance, 0.01)

        // Cancel sale restores stock
        assertTrue(repository.cancelSale(admin, sale, null).isSuccess)
        assertEquals(10, db.catalogDao().getProductById(prodId)!!.stockQuantity)

        // Reset empties everything again
        repository.resetAllDataToEmpty()
        assertTrue(db.userDao().getAllUsersOnce().isEmpty())
        assertTrue(db.catalogDao().getProductsOnce().isEmpty())
        assertTrue(db.salesDao().getSalesOnce().isEmpty())
        assertEquals("", db.adminDao().getSettingsOnce()!!.storeName)
    }

    @Test
    fun managerAccount_passwordChanges_andScopedResets_workStrictly() = runBlocking {
        repository.ensureSeeded()

        val setupRes = repository.setupInitialStructureAndAdmin(
            storeName = "Maison Élite",
            city = "Yaoundé",
            location = "Bastos",
            address = "Avenue Kennedy",
            storePhone = "+237 690 11 22 33",
            storeEmail = "contact@maisonelite.cm",
            structureType = "BOUTIQUE",
            logoKey = "BOUTIQUE",
            adminFullName = "Admin Principal",
            adminUsername = "admin.elite",
            adminPassword = "adminPass1",
            adminPhone = "+237 690 11 22 33",
            adminWhatsapp = "",
            adminEmail = "",
            createManager = true,
            managerFullName = "Alain Gestionnaire",
            managerUsername = "alain.mgr",
            managerPassword = "mgrPass1",
            managerPhone = "+237 670 44 55 66",
            managerWhatsapp = "+237 670 44 55 66",
            managerEmail = "alain@maisonelite.cm",
            cloudUid = null
        )
        assertTrue(setupRes.isSuccess)
        val admin = setupRes.getOrThrow().first

        val mgrLogin = repository.authenticateStaff("alain.mgr", "mgrPass1")
        assertTrue(mgrLogin.isSuccess)
        val (manager, mgrPerms) = mgrLogin.getOrThrow()
        assertEquals(RoleCode.MANAGER.code, manager.roleCode)
        assertTrue(com.example.security.AppPermission.RESET_DATA.code in mgrPerms)
        assertFalse(com.example.security.AppPermission.MANAGE_USERS.code in mgrPerms)

        val cashierRes = repository.createStaffUser(
            actor = admin,
            username = "claire.caisse",
            fullName = "Claire Vente",
            rawPassword = "cashPass1",
            roleCode = RoleCode.CASHIER.code
        )
        assertTrue(cashierRes.isSuccess)
        val cashier = repository.authenticateStaff("claire.caisse", "cashPass1").getOrThrow().first

        // Own password changes across roles + validations
        assertTrue(repository.changeOwnPassword(manager, "wrongOld", "newMgrPass").isFailure)
        assertTrue(repository.changeOwnPassword(manager, "mgrPass1", "mgrPass1").isFailure)
        assertTrue(repository.changeOwnPassword(manager, "mgrPass1", "12").isFailure)
        assertTrue(repository.changeOwnPassword(manager, "mgrPass1", "mgrPass2").isSuccess)
        assertTrue(repository.authenticateStaff("alain.mgr", "mgrPass1").isFailure)
        assertTrue(repository.authenticateStaff("alain.mgr", "mgrPass2").isSuccess)

        assertTrue(repository.changeOwnPassword(cashier, "cashPass1", "cashPass2").isSuccess)
        assertTrue(repository.authenticateStaff("claire.caisse", "cashPass2").isSuccess)

        // Manager without MANAGE_USERS cannot reset passwords; after Admin grants MANAGE_USERS, Manager can reset Cashier but not Admin
        val updatedManager = repository.authenticateStaff("alain.mgr", "mgrPass2").getOrThrow().first
        assertTrue(repository.resetStaffPasswordByAdmin(updatedManager, cashier, "blocked123").isFailure)
        repository.updateUserRoleAndPermission(admin, updatedManager, com.example.security.AppPermission.MANAGE_USERS.code, true)
        assertTrue(repository.resetStaffPasswordByAdmin(updatedManager, admin, "hacked123").isFailure)
        assertTrue(repository.resetStaffPasswordByAdmin(updatedManager, cashier, "cashPass3").isSuccess)
        assertTrue(repository.authenticateStaff("claire.caisse", "cashPass3").isSuccess)

        // Create a product + sale to verify scoped reset
        assertTrue(repository.openCashRegister(cashier, 10000.0).isSuccess)
        val prodId = repository.saveProduct(
            actor = updatedManager,
            product = ProductEntity(
                name = "Sac Cuir",
                reference = "",
                sku = "SAC-01",
                barcode = "617900998877",
                categoryId = 1L,
                categoryName = "Accessoires",
                purchasePrice = 10000.0,
                sellingPrice = 18000.0,
                stockQuantity = 5,
                minThreshold = 1
            ),
            isNew = true,
            cloudUid = null
        )
        val prod = db.catalogDao().getProductById(prodId)!!
        assertTrue(
            repository.validateSale(
                actor = cashier,
                cart = listOf(CartLine(prod, 1)),
                customer = null,
                discountAmount = 0.0,
                paymentMethod = "Espèces",
                amountPaid = 18000.0,
                paymentRef = "",
                cloudUid = null
            ).isSuccess
        )

        // Cashier cannot reset; wrong password fails; Manager cannot full-factory reset
        assertTrue(
            repository.performAdminReset(
                actor = cashier,
                adminPassword = "cashPass3",
                scope = com.example.data.repository.AdminResetScope.OPERATIONS_ONLY,
                createBackupFirst = false,
                alsoCleanCloud = false,
                cloudUid = null
            ).isFailure
        )
        assertTrue(
            repository.performAdminReset(
                actor = updatedManager,
                adminPassword = "badPassword",
                scope = com.example.data.repository.AdminResetScope.OPERATIONS_ONLY,
                createBackupFirst = false,
                alsoCleanCloud = false,
                cloudUid = null
            ).isFailure
        )
        assertTrue(
            repository.performAdminReset(
                actor = updatedManager,
                adminPassword = "mgrPass2",
                scope = com.example.data.repository.AdminResetScope.FULL_FACTORY_RESET,
                createBackupFirst = false,
                alsoCleanCloud = false,
                cloudUid = null
            ).isFailure
        )

        // Manager executes OPERATIONS_ONLY -> sales & invoices cleared, product & users kept
        val opResetRes = repository.performAdminReset(
            actor = updatedManager,
            adminPassword = "mgrPass2",
            scope = com.example.data.repository.AdminResetScope.OPERATIONS_ONLY,
            createBackupFirst = true,
            alsoCleanCloud = false,
            cloudUid = null
        )
        assertTrue(opResetRes.isSuccess)
        assertTrue(db.salesDao().getSalesOnce().isEmpty())
        assertTrue(repository.invoices.first().isEmpty())
        assertEquals(1, db.catalogDao().getProductsOnce().size)
        assertTrue(repository.backups.first().isNotEmpty())

        // Admin changes own password and executes FULL_FACTORY_RESET -> everything cleared
        assertTrue(repository.changeOwnPassword(admin, "adminPass1", "adminPass2").isSuccess)
        val updatedAdmin = repository.authenticateStaff("admin.elite", "adminPass2").getOrThrow().first
        val factoryResetRes = repository.performAdminReset(
            actor = updatedAdmin,
            adminPassword = "adminPass2",
            scope = com.example.data.repository.AdminResetScope.FULL_FACTORY_RESET,
            createBackupFirst = false,
            alsoCleanCloud = false,
            cloudUid = null
        )
        assertTrue(factoryResetRes.isSuccess)
        assertTrue(db.userDao().getAllUsersOnce().isEmpty())
        assertTrue(db.catalogDao().getProductsOnce().isEmpty())
        assertEquals("", db.adminDao().getSettingsOnce()!!.storeName)
    }

    @Test
    fun multiTenantOnlinePaymentConfigs_saveInitiateConfirmAndMaskSecrets() = runBlocking {
        repository.ensureSeeded()
        val admin = repository.setupInitialStructureAndAdmin(
            storeName = "Tech Market Sarl",
            city = "Douala",
            location = "Akwa",
            address = "Boulevard de la Liberté",
            storePhone = "+237 655 11 22 33",
            storeEmail = "pay@techmarket.cm",
            structureType = "ELECTRONIQUE",
            logoKey = "ELECTRONIQUE",
            adminFullName = "Jean Admin",
            adminUsername = "jean.admin",
            adminPassword = "admin1234",
            adminPhone = "+237 655 11 22 33",
            adminWhatsapp = "",
            adminEmail = "",
            cloudUid = null
        ).getOrThrow().first

        val cashierId = repository.createStaffUser(
            actor = admin,
            username = "luc.caisse",
            fullName = "Luc Caisse",
            rawPassword = "pass1234",
            roleCode = RoleCode.CASHIER.code
        ).getOrThrow()
        val cashier = db.userDao().getUserById(cashierId)!!

        // Non-admin/cashier without MANAGE_SETTINGS cannot configure payment gateways
        val forbiddenCfg = repository.saveOnlinePaymentConfig(
            actor = cashier,
            config = com.example.data.local.OnlinePaymentConfigEntity(
                category = "ORANGE_MONEY",
                displayName = "OM Pirate",
                receiverPhone = "+237 600000000"
            )
        )
        assertTrue(forbiddenCfg.isFailure)

        // Admin saves company Orange Money, Custom Link (Wave), and CinetPay credentials
        val omSaved = repository.saveOnlinePaymentConfig(
            actor = admin,
            config = com.example.data.local.OnlinePaymentConfigEntity(
                category = "ORANGE_MONEY",
                displayName = "Orange Money TechMarket",
                receiverPhone = "+237 699 88 77 66",
                siteOrMerchantId = "MERCH-7788",
                accountHolderName = "Tech Market Sarl",
                ussdPattern = "#150*14*{merchant}*{amount}#",
                instructions = "Vérifiez le nom Tech Market Sarl avant validation."
            )
        )
        assertTrue(omSaved.isSuccess)
        val omCfg = omSaved.getOrThrow()

        val linkSaved = repository.saveOnlinePaymentConfig(
            actor = admin,
            config = com.example.data.local.OnlinePaymentConfigEntity(
                category = "CUSTOM_LINK",
                displayName = "Lien Wave / PayPal",
                paymentBaseUrl = "https://pay.techmarket.cm/checkout?amt={amount}&ref={ref}",
                accountHolderName = "Tech Market Sarl"
            )
        )
        assertTrue(linkSaved.isSuccess)
        val linkCfg = linkSaved.getOrThrow()

        val secretApiKey = "LIVE_SUPER_SECRET_KEY_987654"
        val cinetSaved = repository.saveOnlinePaymentConfig(
            actor = admin,
            config = com.example.data.local.OnlinePaymentConfigEntity(
                category = "CINETPAY",
                displayName = "CinetPay TechMarket",
                apiKey = secretApiKey,
                siteOrMerchantId = "554433"
            )
        )
        assertTrue(cinetSaved.isSuccess)

        // Ensure raw secret key is never logged in audit logs
        val logs = db.adminDao().observeAuditLogs().first()
        assertFalse(logs.any { it.newValue.contains(secretApiKey) || it.action.contains(secretApiKey) })

        // Cashier initiates payment via company Orange Money config
        assertTrue(repository.openCashRegister(cashier, 5000.0).isSuccess)
        val omTx = repository.initiateOnlinePayment(
            actor = cashier,
            provider = omCfg.displayName,
            channel = "MOBILE_MONEY",
            amountXaf = 12500.0,
            customerName = "Client A",
            customerPhone = "+237 677 00 11 22",
            customerEmail = "",
            configId = omCfg.id
        )
        assertEquals(omCfg.id, omTx.configId)
        assertTrue(omTx.paymentUrl.contains("237699887766"))
        assertTrue(omTx.gatewayMessage.contains("#150*14*MERCH-7788*12500#"))
        assertTrue(omTx.gatewayMessage.contains("Tech Market Sarl"))

        // Cashier initiates payment via company Custom Link
        val linkTx = repository.initiateOnlinePayment(
            actor = cashier,
            provider = linkCfg.displayName,
            channel = "ALL",
            amountXaf = 45000.0,
            customerName = "Client B",
            customerPhone = "",
            customerEmail = "",
            configId = linkCfg.id
        )
        assertTrue(linkTx.paymentUrl.startsWith("https://pay.techmarket.cm/checkout?amt=45000&ref="))
        assertTrue(linkTx.isLiveGateway)

        // Confirming manual payment credits open cash register
        val confirmed = repository.confirmOnlinePaymentManually(cashier, omTx, "OM261116.9988")
        assertEquals("CONFIRME", confirmed.status)
        assertEquals(12500.0, db.cashDao().getOpenRegisterOnce()!!.totalSalesMobile, 0.01)

        // Toggle & delete config
        assertTrue(repository.toggleOnlinePaymentConfig(admin, omCfg, false).isSuccess)
        assertFalse(db.onlinePaymentDao().getOnlinePaymentConfigById(omCfg.id)!!.isEnabled)
        assertTrue(repository.deleteOnlinePaymentConfig(admin, linkCfg).isSuccess)
        assertEquals(2, db.onlinePaymentDao().getOnlinePaymentConfigsOnce().size)
    }
}
