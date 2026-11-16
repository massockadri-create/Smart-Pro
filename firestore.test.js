const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_store_owner";
const BOB_UID = "bob_other_owner";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validTs() {
  return new Date(Date.now() - 2000);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validProduct(uid) {
  const now = validTs();
  return {
    userId: uid,
    localId: 1,
    name: "Top Pamplemousse 65cl",
    sku: "BOI-001",
    barcode: "617100000001",
    categoryName: "Boissons",
    purchasePrice: 400,
    sellingPrice: 600,
    stockQuantity: 48,
    minThreshold: 12,
    unit: "Bouteille",
    status: "ACTIF",
    clientUpdatedAt: 1763230000000,
    createdAt: now,
    updatedAt: now,
  };
}

function validSale(uid, status = "VALIDEE") {
  const now = validTs();
  return {
    userId: uid,
    invoiceNumber: "SSP-0001",
    customerName: "Client Comptant",
    cashierName: "Paul Caissier",
    totalAmount: 75000,
    profit: 18500,
    paymentMethod: "Orange Money",
    status,
    itemCount: 3,
    clientCreatedAt: 1763230000000,
    createdAt: now,
    updatedAt: now,
  };
}

function validAuditLog(uid) {
  return {
    userId: uid,
    staffName: "Jean Gestionnaire",
    action: "Jean a modifie le prix du produit Top Pamplemousse.",
    module: "Produits",
    details: "550 -> 600 FCFA",
    createdAt: validTs(),
  };
}

function validBackup(uid) {
  return {
    userId: uid,
    label: "Sauvegarde Quotidienne",
    backupType: "MANUELLE",
    recordCount: 42,
    summary: "18 produits, 12 ventes",
    payloadJson: "{\"version\":1}",
    createdByName: "Administrateur Principal",
    createdAt: validTs(),
  };
}

test("Unauthenticated user cannot read or list products", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("sync_products").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sync_products")
      .doc("prod_1")
      .get()
  );
});

test("Owner can create, read, list, and update own product", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("sync_products")
    .doc("prod_1");

  await assertSucceeds(ref.set(validProduct(ALICE_UID)));
  await assertSucceeds(ref.get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sync_products").get()
  );
  await assertSucceeds(
    ref.update({
      sellingPrice: 650,
      updatedAt: validTs(),
    })
  );
});

test("Cross-user access is denied for products, sales, logs, backups", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sync_products")
      .doc("prod_1")
      .set(validProduct(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sync_products")
      .doc("prod_1")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sync_sales")
      .doc("sale_1")
      .set(validSale(BOB_UID))
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sync_audit_logs")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sync_backups")
      .get()
  );
});

test("Shadow update with ghost field is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("sync_products")
    .doc("prod_shadow");

  const badPayload = { ...validProduct(ALICE_UID), isAdminBypass: true };
  await assertFails(ref.set(badPayload));
});

test("Sale terminal state lock blocks modifying ANNULEE sale", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("sync_sales")
    .doc("sale_1");

  await assertSucceeds(ref.set(validSale(ALICE_UID, "VALIDEE")));
  await assertSucceeds(
    ref.update({
      status: "ANNULEE",
      updatedAt: validTs(),
    })
  );
  await assertFails(
    ref.update({
      status: "VALIDEE",
      updatedAt: validTs(),
    })
  );
});

test("Audit logs are strictly append-only (cannot update or delete)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("sync_audit_logs")
    .doc("log_1");

  await assertSucceeds(ref.set(validAuditLog(ALICE_UID)));
  await assertFails(ref.update({ action: "Tampered" }));
  await assertFails(ref.delete());
});

test("Backup snapshot can be created, listed, and deleted by owner, never updated", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("sync_backups")
    .doc("bkp_1");

  await assertSucceeds(ref.set(validBackup(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sync_backups").get()
  );
  await assertFails(ref.update({ label: "Modified" }));
  await assertSucceeds(ref.delete());
});

test("Owner can create/update own store profile and cross-user read is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ref = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("sync_store_profile")
    .doc("primary");

  const now = validTs();
  await assertSucceeds(
    ref.set({
      userId: ALICE_UID,
      storeName: "Ma Boutique Douala",
      city: "Douala",
      location: "Akwa",
      address: "Boulevard de la Liberté",
      phone: "+237 699000000",
      email: "contact@boutique.cm",
      structureType: "GENERAL",
      logoKey: "GENERAL",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    ref.update({
      city: "Yaoundé",
      location: "Bastos",
      updatedAt: validTs(),
    })
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sync_store_profile")
      .doc("primary")
      .get()
  );
});
