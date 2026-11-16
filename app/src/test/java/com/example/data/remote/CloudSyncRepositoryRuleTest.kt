package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.AuditLogEntity
import com.example.data.local.BackupRecordEntity
import com.example.data.local.ProductEntity
import com.example.data.local.SaleEntity
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class CloudSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun upsertAndObserveProduct_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = CloudSyncRepository(firestore)
        val suffix = UUID.randomUUID().toString().take(8)
        val product = ProductEntity(
            id = 101L,
            name = "Top Pamplemousse $suffix",
            reference = "REF-$suffix",
            sku = "SKU_$suffix",
            barcode = "617100001$suffix",
            categoryId = 1L,
            categoryName = "Boissons",
            purchasePrice = 400.0,
            sellingPrice = 600.0,
            stockQuantity = 30,
            minThreshold = 10,
            unit = "Bouteille",
            status = "ACTIF",
            updatedAt = System.currentTimeMillis()
        )

        val result = withTimeout(DEFAULT_TIMEOUT_MS) { repo.upsertProduct(aliceUid, product) }
        assertTrue(result.isSuccess)
        val docId = result.getOrThrow()

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeCloudProducts(aliceUid).first { list -> list.any { it.id == docId } }
        }
        assertTrue(observed.any { it.id == docId && it.name == product.name })
    }

    @Test
    fun crossUserReadProduct_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CloudSyncRepository(firestore)
        val suffix = UUID.randomUUID().toString().take(8)
        val product = ProductEntity(
            id = 202L,
            name = "Huile Mayor $suffix",
            reference = "REF-$suffix",
            sku = "HUI_$suffix",
            barcode = "617200002$suffix",
            categoryId = 2L,
            categoryName = "Aliments",
            purchasePrice = 1150.0,
            sellingPrice = 1450.0,
            stockQuantity = 15,
            minThreshold = 5,
            unit = "Bouteille",
            status = "ACTIF",
            updatedAt = System.currentTimeMillis()
        )
        val docId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertProduct(aliceUid, product).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = CloudSyncRepository(firestore)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.getProductById(aliceUid, docId) }
            fail("Expected PERMISSION_DENIED for cross-user product read")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
        }
    }

    @Test
    fun observeProducts_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = CloudSyncRepository(firestore)
        try {
            withTimeout(FLOW_TIMEOUT_MS) { repo.observeCloudProducts("any_uid").first() }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
        }
    }

    @Test
    fun upsertSaleAuditLogAndBackup_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = CloudSyncRepository(firestore)
        val suffix = UUID.randomUUID().toString().take(8)

        val sale = SaleEntity(
            id = 1L,
            invoiceNumber = "SSP-$suffix",
            customerName = "Client Comptant",
            userId = 1L,
            userName = "Pauline Atangana",
            subtotal = 15000.0,
            totalAmount = 15000.0,
            totalCost = 10000.0,
            profit = 5000.0,
            paymentMethod = "Orange Money",
            amountPaid = 15000.0,
            status = "VALIDEE",
            createdAt = System.currentTimeMillis()
        )
        val saleRes = withTimeout(DEFAULT_TIMEOUT_MS) { repo.upsertSale(aliceUid, sale, 3) }
        assertTrue(saleRes.isSuccess)

        val auditLog = AuditLogEntity(
            id = 55L,
            userId = 1L,
            userName = "Samuel Ndongo",
            action = "Test Audit $suffix",
            module = "POS",
            oldValue = "0",
            newValue = "15000",
            createdAt = System.currentTimeMillis()
        )
        val logRes = withTimeout(DEFAULT_TIMEOUT_MS) { repo.pushAuditLog(aliceUid, auditLog) }
        assertTrue(logRes.isSuccess)

        val backup = BackupRecordEntity(
            id = 99L,
            label = "Backup $suffix",
            type = "MANUELLE",
            recordCount = 10,
            sizeBytes = 128L,
            jsonPayload = "{\"ok\":true}",
            createdByName = "Samuel Ndongo",
            createdAt = System.currentTimeMillis()
        )
        val bkpRes = withTimeout(DEFAULT_TIMEOUT_MS) { repo.pushBackup(aliceUid, backup, "10 records") }
        assertTrue(bkpRes.isSuccess)

        val settings = com.example.data.local.StoreSettingsEntity(
            storeName = "Ets Commerce",
            city = "Douala",
            location = "Akwa",
            address = "Rue Castelnau",
            phone = "+237690001122",
            email = "info@etscommerce.cm",
            structureType = "GENERAL",
            logoKey = "GENERAL"
        )
        val profileRes = withTimeout(DEFAULT_TIMEOUT_MS) { repo.upsertStoreProfile(aliceUid, settings) }
        assertTrue(profileRes.isSuccess)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_ssp@test.com"
        const val BOB_EMAIL = "bob_ssp@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
