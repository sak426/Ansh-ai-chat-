const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_student_1";
const BOB_UID = "bob_student_2";
const ADMIN_UID = "owner_admin_1";
const ADMIN_EMAIL = "sakharepurva83@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msAgo = 2000) {
  return new Date(Date.now() - msAgo);
}

function userProfilePayload(uid, email, displayName) {
  const now = pastDate(5000);
  return {
    userId: uid,
    displayName,
    email,
    photoUrl: "https://example.com/avatar.png",
    blocked: false,
    chatCount: 0,
    imageGenCount: 0,
    noteCount: 0,
    createdAt: now,
    updatedAt: now,
    lastActiveAt: now,
  };
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
  const db = testEnv.unauthenticatedContext().firestore();
  await assertFails(db.collection("users").doc(ALICE_UID).get());
  await assertFails(db.collection("users").get());
  await assertFails(
    db.collection("users").doc(ALICE_UID).set(
      userProfilePayload(ALICE_UID, "alice@edu.test", "Alice")
    )
  );
  await assertFails(db.collection("appBranding").doc("current").get());
  await assertFails(db.collection("announcements").doc("current").get());
  await assertFails(
    db.collection("users").doc(ALICE_UID).collection("studyNotes").get()
  );
});

test("User profile: owner create, partial update, PII isolation, and anti-escalation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@edu.test",
    email_verified: true,
  }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@edu.test",
    email_verified: true,
  }).firestore();

  // Valid create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(
      userProfilePayload(ALICE_UID, "alice@edu.test", "Alice")
    )
  );

  // Reject ghost field & @DocumentId 'id' field on fresh doc
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      ...userProfilePayload(BOB_UID, "bob@edu.test", "Bob"),
      id: BOB_UID,
    })
  );

  // Reject future timestamp
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      ...userProfilePayload(BOB_UID, "bob@edu.test", "Bob"),
      createdAt: new Date(Date.now() + 60000),
    })
  );

  // Valid partial update of usage counters
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      chatCount: 1,
      updatedAt: pastDate(500),
      lastActiveAt: pastDate(500),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(100),
      updatedAt: pastDate(100),
    })
  );

  // PII isolation: Bob cannot read Alice's profile or list users
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
});

test("Chat messages, study notes, and study tasks: owner CRUD and cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@edu.test",
    email_verified: true,
  }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@edu.test",
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(
      userProfilePayload(ALICE_UID, "alice@edu.test", "Alice")
    )
  );

  const now = pastDate(3000);

  // Chat message create + list
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chatMessages").doc("msg_1").set({
      userId: ALICE_UID,
      role: "user",
      text: "Explain Pythagoras theorem step by step.",
      hasImage: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chatMessages").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("chatMessages").get()
  );

  // Study note create + partial update (AI summary saved)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("studyNotes").doc("note_1").set({
      userId: ALICE_UID,
      title: "Cell Mitosis",
      subject: "Biology",
      content: "Mitosis has four main phases: prophase, metaphase, anaphase, telophase.",
      summary: "",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("studyNotes").doc("note_1").update({
      summary: "• Prophase\n• Metaphase\n• Anaphase\n• Telophase",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("studyNotes").doc("note_1").get()
  );

  // Study task create + toggle completed
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("studyTasks").doc("task_1").set({
      userId: ALICE_UID,
      title: "Finish Calculus Worksheet",
      category: "HOMEWORK",
      dueDateLabel: "Tomorrow",
      priority: "HIGH",
      completed: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("studyTasks").doc("task_1").update({
      completed: true,
      updatedAt: pastDate(500),
    })
  );
  // Invalid enum rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("studyTasks").doc("task_2").set({
      userId: ALICE_UID,
      title: "Bad Category",
      category: "UNKNOWN",
      dueDateLabel: "Today",
      priority: "LOW",
      completed: false,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Admin panel security: verified owner allowed, spoofed email and students denied, blocked user enforced", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@edu.test",
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(
      userProfilePayload(ALICE_UID, "alice@edu.test", "Alice")
    )
  );

  // Verified admin can list users & block Alice
  await assertSucceeds(adminDb.collection("users").get());
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({
      blocked: true,
      updatedAt: pastDate(500),
    })
  );

  // Spoofed unverified admin email is rejected
  await assertFails(spoofedAdminDb.collection("users").get());
  await assertFails(
    spoofedAdminDb.collection("users").doc(ALICE_UID).update({
      blocked: false,
      updatedAt: pastDate(400),
    })
  );

  // Blocked Alice cannot unblock herself or create new notes/messages
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      blocked: false,
      updatedAt: pastDate(400),
    })
  );
  const now = pastDate(1000);
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("studyNotes").doc("note_blocked").set({
      userId: ALICE_UID,
      title: "Blocked write",
      subject: "Math",
      content: "Should fail because user is blocked.",
      summary: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Admin can update branding, announcements, & daily usage limits; students can get (not list or write)
  await assertSucceeds(
    adminDb.collection("appBranding").doc("current").set({
      appName: "Ansh AI Chat Pro",
      logoBase64: "",
      bannerBase64: "",
      bannerTitle: "Ace Your Finals",
      bannerSubtitle: "Snap any textbook question for step-by-step help.",
      updatedBy: ADMIN_UID,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    adminDb.collection("announcements").doc("current").set({
      title: "Exam Week Tips",
      message: "Use the AI Notes Summarizer to review chapters fast!",
      active: true,
      updatedBy: ADMIN_UID,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    adminDb.collection("usageLimits").doc("current").set({
      dailyChatLimit: 25,
      dailyImageLimit: 6,
      updatedBy: ADMIN_UID,
      updatedAt: now,
    })
  );
  // Reject out-of-range limits
  await assertFails(
    adminDb.collection("usageLimits").doc("current").update({
      dailyChatLimit: 0,
      dailyImageLimit: 4,
      updatedBy: ADMIN_UID,
      updatedAt: pastDate(200),
    })
  );

  await assertSucceeds(aliceDb.collection("appBranding").doc("current").get());
  await assertSucceeds(aliceDb.collection("announcements").doc("current").get());
  await assertSucceeds(aliceDb.collection("usageLimits").doc("current").get());
  await assertFails(aliceDb.collection("appBranding").get());
  await assertFails(aliceDb.collection("announcements").get());
  await assertFails(aliceDb.collection("usageLimits").get());
  await assertFails(
    aliceDb.collection("announcements").doc("current").update({
      message: "Hacked banner",
      updatedAt: pastDate(200),
    })
  );
  await assertFails(
    aliceDb.collection("usageLimits").doc("current").update({
      dailyChatLimit: 99,
      updatedAt: pastDate(200),
    })
  );
});

test("Daily usage counters: unblocked student can track daily usage and midnight reset", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@edu.test",
    email_verified: true,
  }).firestore();

  const now = pastDate(3000);
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...userProfilePayload(ALICE_UID, "alice@edu.test", "Alice"),
      dailyChatCount: 0,
      dailyImageCount: 0,
      usageDate: "2026-11-26",
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      chatCount: 1,
      dailyChatCount: 1,
      usageDate: "2026-11-26",
      updatedAt: pastDate(500),
      lastActiveAt: pastDate(500),
    })
  );
  // Reject negative daily count
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      dailyChatCount: -1,
      updatedAt: pastDate(200),
    })
  );
});
