// System test: three phones using one server, end to end through the HTTP API.
const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { app, request, device, resetDatabase } = require("./helpers");
const sequelize = require("../src/config/db");

// Smallest valid PNG (1x1), standing in for a photo.
const PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=",
  "base64",
);

let an, binh, chi, group, bill;

before(async () => {
  resetDatabase();
  an = await device("An", "0901000001");
  binh = await device("Bình", "0901000002");
  chi = await device("Chi", "0901000003");
});

after(async () => {
  await sequelize.close();
});

test("health check answers without a token", async () => {
  const res = await request(app).get("/health");
  assert.equal(res.status, 200);
  assert.equal(res.body.ok, true);
});

test("An finds Bình by phone and creates a group with Bình and Chi", async () => {
  const found = await an.get("/api/users/find?phone=0901000002");
  assert.equal(found.status, 200);
  assert.equal(String(found.body.id), binh.id);
  assert.equal(found.body.password_hash, undefined);

  const res = await an.post("/api/groups", { name: "Đà Lạt", memberIds: [binh.id, chi.id] });
  assert.equal(res.status, 201);
  group = res.body;
});

test("Bình and Chi see the group on their own devices after sync", async () => {
  for (const d of [binh, chi]) {
    const res = await d.get("/api/sync");
    assert.equal(res.status, 200);
    assert.deepEqual(res.body.groups.map((g) => g.id), [group.id]);
    assert.equal(res.body.members.length, 3);
    assert.deepEqual(res.body.users.map((u) => u.name).sort(), ["An", "Bình", "Chi"]);
    assert.ok(res.body.users.every((u) => u.password_hash === undefined));
  }
});

test("An uploads a bill photo and adds an itemised bill; items keep their order", async () => {
  const up = await an.upload("/api/uploads?kind=bill").attach("image", PNG, { filename: "bill.png", contentType: "image/png" });
  assert.equal(up.status, 201);
  assert.match(up.body.url, /\/uploads\/bill\//);
  const photo = await request(app).get(new URL(up.body.url).pathname);
  assert.equal(photo.status, 200);

  const res = await an.post("/api/bills", {
    groupId: group.id,
    title: "Lẩu",
    payerId: an.id,
    mode: "ITEMIZED",
    subtotal: 450000,
    participantsCsv: [an.id, binh.id, chi.id].join(","),
    vatPercent: 0,
    servicePercent: 0,
    rounding: 1,
    category: "AN_UONG",
    imageUrl: up.body.url,
    items: [
      { name: "Lẩu thái", price: 300000, consumersCsv: [an.id, binh.id, chi.id].join(",") },
      { name: "Bia", price: 150000, consumersCsv: [binh.id, chi.id].join(",") },
    ],
  });
  assert.equal(res.status, 201);
  bill = res.body;
  assert.equal(bill.imageUrl, up.body.url);

  const synced = await chi.get("/api/sync");
  const b = synced.body.bills.find((x) => x.bill.id === bill.id);
  assert.deepEqual(b.items.map((i) => i.name), ["Lẩu thái", "Bia"]);
  assert.equal(typeof b.bill.subtotal, "number");
  assert.equal(b.bill.subtotal, 450000);
});

test("Bình marks paid with a screenshot; An sees MARKED_PAID and the proof", async () => {
  const up = await binh.upload("/api/uploads?kind=proof").attach("image", PNG, { filename: "ck.png", contentType: "image/png" });
  assert.equal(up.status, 201);

  const res = await binh.post("/api/payments/mark-paid", { billId: bill.id, proofImageUrl: up.body.url });
  assert.equal(res.status, 200);

  const synced = await an.get("/api/sync");
  const s = synced.body.debtStatuses.find((x) => x.debtKey === `${bill.id}:${binh.id}`);
  assert.equal(s.status, "MARKED_PAID");
  assert.equal(s.proofImageUrl, up.body.url);
});

test("only the payer confirms; the screenshot stays after confirming", async () => {
  const wrong = await chi.post("/api/payments/confirm", { billId: bill.id, debtorId: binh.id });
  assert.equal(wrong.status, 400);

  const ok = await an.post("/api/payments/confirm", { billId: bill.id, debtorId: binh.id });
  assert.equal(ok.status, 200);
  const synced = await binh.get("/api/sync");
  const s = synced.body.debtStatuses.find((x) => x.debtKey === `${bill.id}:${binh.id}`);
  assert.equal(s.status, "CONFIRMED");
  assert.ok(s.proofImageUrl);
});

test("An can confirm a cash payment that was never marked", async () => {
  const res = await an.post("/api/payments/confirm", { billId: bill.id, debtorId: chi.id });
  assert.equal(res.status, 200);
});

test("dispute needs a MARKED_PAID debt", async () => {
  const res = await an.post("/api/payments/dispute", { billId: bill.id, debtorId: chi.id });
  assert.equal(res.status, 400);
});

test("a message sent from one device lands in the other's inbox", async () => {
  const res = await an.post("/api/inbox/send", { recipientId: chi.id, message: "An nhắc bạn trả 150.000 đ", billId: bill.id });
  assert.equal(res.status, 201);
  const synced = await chi.get("/api/sync");
  assert.equal(synced.body.inbox[0].message, "An nhắc bạn trả 150.000 đ");
});

test("deleting a bill removes it and its statuses from every device", async () => {
  const res = await an.del(`/api/bills/${bill.id}`);
  assert.equal(res.status, 200);
  const synced = await binh.get("/api/sync");
  assert.equal(synced.body.bills.length, 0);
  assert.equal(synced.body.debtStatuses.length, 0);
});

test("a member who leaves no longer gets the group", async () => {
  const res = await chi.del(`/api/groups/${group.id}/leave`);
  assert.equal(res.status, 200);
  const synced = await chi.get("/api/sync");
  assert.equal(synced.body.groups.length, 0);
  const forAn = await an.get("/api/sync");
  assert.equal(forAn.body.members.length, 3, "past members are kept so old bills split the same");
});

test("uploads refuse non-images and requests without a token", async () => {
  const bad = await an.upload("/api/uploads").attach("image", Buffer.from("hello"), { filename: "a.txt", contentType: "text/plain" });
  assert.equal(bad.status, 400);
  const anon = await request(app).get("/api/sync");
  assert.equal(anon.status, 401);
});
