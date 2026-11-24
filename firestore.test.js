const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_yazan_1";
const OWNER_EMAIL = "elakhwaelmthdon@gmail.com";
const ALICE_UID = "customer_alice_123";
const BOB_UID = "customer_bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function ownerDb() {
  return testEnv
    .authenticatedContext(OWNER_UID, {
      email: OWNER_EMAIL,
      email_verified: true,
    })
    .firestore();
}

function spoofedOwnerDb() {
  return testEnv
    .authenticatedContext("spoof_uid_999", {
      email: OWNER_EMAIL,
      email_verified: false,
    })
    .firestore();
}

function customerDb(uid, email = `${uid}@example.com`) {
  return testEnv
    .authenticatedContext(uid, {
      email,
      email_verified: true,
    })
    .firestore();
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

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("menu_items").where("available", "==", true).get());
  await assertFails(unauthDb.collection("orders").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Email spoofing attack (unverified owner email) is rejected", async () => {
  const spoofDb = spoofedOwnerDb();
  const now = new Date(Date.now() - 1000);
  await assertFails(
    spoofDb.collection("menu_items").doc("turkish_plain").set({
      name: "بن تركي سادة",
      description: "تحميص وسط",
      category: "GROUND_COFFEE",
      unitLabel: "ربع كيلو",
      price: 120.0,
      available: true,
      sortOrder: 1,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Menu items: Owner CRUD succeeds; Customer can only query available items and cannot mutate", async () => {
  const owner = ownerDb();
  const alice = customerDb(ALICE_UID);
  const created = new Date(Date.now() - 10000);

  // Owner creates available and unavailable items (no @DocumentId `id` field)
  await assertSucceeds(
    owner.collection("menu_items").doc("turkish_plain").set({
      name: "بن تركي سادة",
      description: "بن برازيلي محمص بعناية",
      category: "GROUND_COFFEE",
      unitLabel: "ربع كيلو",
      price: 135.0,
      available: true,
      sortOrder: 1,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    owner.collection("menu_items").doc("espresso_shot").set({
      name: "اسبريسو دبل",
      description: "قهوة إسبريسو مركزة",
      category: "BEVERAGE",
      unitLabel: "كوب",
      price: 45.0,
      available: false,
      sortOrder: 2,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject ghost field & @DocumentId `id` field
  await assertFails(
    owner.collection("menu_items").doc("bad_item").set({
      id: "bad_item",
      name: "صنف خاطئ",
      description: "",
      category: "BEVERAGE",
      unitLabel: "كوب",
      price: 30.0,
      available: true,
      sortOrder: 3,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Owner partial update succeeds (preserving createdAt)
  await assertSucceeds(
    owner.collection("menu_items").doc("turkish_plain").update({
      price: 145.0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Customer cannot create, update, or delete menu items
  await assertFails(
    alice.collection("menu_items").doc("hack_item").set({
      name: "بلاش",
      description: "",
      category: "BEVERAGE",
      unitLabel: "كوب",
      price: 1.0,
      available: true,
      sortOrder: 1,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    alice.collection("menu_items").doc("turkish_plain").update({
      price: 1.0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(alice.collection("menu_items").doc("turkish_plain").delete());

  // Query alignment: Customer filtered query succeeds, unfiltered query fails, Owner unfiltered succeeds
  await assertSucceeds(alice.collection("menu_items").where("available", "==", true).get());
  await assertFails(alice.collection("menu_items").get());
  await assertSucceeds(owner.collection("menu_items").get());
});

test("Orders: Customer creates PENDING order, PII isolated across customers, Owner updates status & paid", async () => {
  const owner = ownerDb();
  const alice = customerDb(ALICE_UID, "alice@example.com");
  const bob = customerDb(BOB_UID, "bob@example.com");
  const created = new Date(Date.now() - 10000);

  const validOrder = {
    orderNumber: "YZ-1001",
    customerId: ALICE_UID,
    customerName: "أحمد محمد",
    customerPhone: "01012345678",
    customerEmail: "alice@example.com",
    notes: "طحنة وسط مع هيل",
    items: [
      {
        menuItemId: "turkish_plain",
        name: "بن تركي سادة",
        category: "GROUND_COFFEE",
        unitLabel: "ربع كيلو",
        unitPrice: 145.0,
        quantity: 2,
        subtotal: 290.0,
      },
    ],
    totalAmount: 290.0,
    status: "PENDING",
    paid: false,
    createdAt: created,
    updatedAt: created,
  };

  // Alice creates her own order
  await assertSucceeds(alice.collection("orders").doc("order_1").set(validOrder));

  // Alice cannot forge a pre-paid or COMPLETED order or impersonate Bob
  await assertFails(
    alice.collection("orders").doc("order_forged").set({
      ...validOrder,
      paid: true,
    })
  );
  await assertFails(
    alice.collection("orders").doc("order_impersonated").set({
      ...validOrder,
      customerId: BOB_UID,
    })
  );
  await assertFails(
    alice.collection("orders").doc("order_future").set({
      ...validOrder,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // PII isolation: Bob cannot read or list Alice's order, and unfiltered list fails for customers
  await assertFails(bob.collection("orders").doc("order_1").get());
  await assertFails(bob.collection("orders").get());
  await assertSucceeds(alice.collection("orders").where("customerId", "==", ALICE_UID).get());

  // Owner can list all orders and advance status -> PREPARING -> READY -> COMPLETED + paid
  await assertSucceeds(owner.collection("orders").get());
  await assertSucceeds(
    owner.collection("orders").doc("order_1").update({
      status: "PREPARING",
      updatedAt: new Date(Date.now() - 3000),
    })
  );

  // Alice cannot cancel once PREPARING, and cannot tamper with totalAmount or paid
  await assertFails(
    alice.collection("orders").doc("order_1").update({
      status: "CANCELLED",
      updatedAt: new Date(Date.now() - 2000),
    })
  );
  await assertFails(
    alice.collection("orders").doc("order_1").update({
      paid: true,
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Owner marks READY and then COMPLETED + paid
  await assertSucceeds(
    owner.collection("orders").doc("order_1").update({
      status: "READY",
      updatedAt: new Date(Date.now() - 2000),
    })
  );
  await assertSucceeds(
    owner.collection("orders").doc("order_1").update({
      status: "COMPLETED",
      paid: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Owner cannot mutate immutable totalAmount or customerId
  await assertFails(
    owner.collection("orders").doc("order_1").update({
      totalAmount: 1.0,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Orders: Customer can cancel their own order only while PENDING", async () => {
  const alice = customerDb(ALICE_UID, "alice@example.com");
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    alice.collection("orders").doc("order_cancel").set({
      orderNumber: "YZ-1002",
      customerId: ALICE_UID,
      customerName: "سارة علي",
      customerPhone: "01198765432",
      customerEmail: "alice@example.com",
      notes: "",
      items: [
        {
          menuItemId: "espresso_shot",
          name: "اسبريسو دبل",
          category: "BEVERAGE",
          unitLabel: "كوب",
          unitPrice: 45.0,
          quantity: 1,
          subtotal: 45.0,
        },
      ],
      totalAmount: 45.0,
      status: "PENDING",
      paid: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    alice.collection("orders").doc("order_cancel").update({
      status: "CANCELLED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("User profiles: strictly scoped to own userId", async () => {
  const alice = customerDb(ALICE_UID);
  const bob = customerDb(BOB_UID);
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "أحمد محمد",
      phone: "01012345678",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).update({
      phone: "01099998888",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bob.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bob.collection("users").doc(ALICE_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      phone: "01200000000",
      createdAt: created,
      updatedAt: created,
    })
  );
});
