// Unit test: photos go to Supabase Storage when it is configured.
const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const http = require("http");
require("./helpers");
const settings = require("../src/config/settings");
const uploadService = require("../src/services/upload.service");

let server, received;

before(async () => {
  // A stand-in for Supabase Storage that records the request.
  server = http.createServer((req, res) => {
    const chunks = [];
    req.on("data", (c) => chunks.push(c));
    req.on("end", () => {
      received = { method: req.method, url: req.url, headers: req.headers, body: Buffer.concat(chunks) };
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end("{}");
    });
  });
  await new Promise((r) => server.listen(0, "127.0.0.1", r));
  settings.supabase.url = `http://127.0.0.1:${server.address().port}`;
  settings.supabase.serviceKey = "eyJservice-key";
  settings.supabase.bucket = "chiabill";
});

after(() => {
  settings.supabase.url = "";
  settings.supabase.serviceKey = "";
  server.close();
});

test("uploads to the bucket and returns the public URL", async () => {
  const file = { mimetype: "image/jpeg", buffer: Buffer.from([0xff, 0xd8, 0xff]) };
  const url = await uploadService.save(file, "proof", "7", "http://unused");
  assert.equal(received.method, "POST");
  assert.match(received.url, /^\/storage\/v1\/object\/chiabill\/proof\/7-\d+-[0-9a-f]{8}\.jpg$/);
  assert.equal(received.headers.authorization, "Bearer eyJservice-key");
  assert.equal(received.headers.apikey, "eyJservice-key");
  assert.equal(received.headers["content-type"], "image/jpeg");
  assert.deepEqual(received.body, file.buffer);
  assert.equal(url, `${settings.supabase.url}/storage/v1/object/public${received.url.replace("/storage/v1/object", "")}`);
});

test("unknown kinds are filed under bill, other file types are refused", async () => {
  const file = { mimetype: "image/png", buffer: Buffer.from([1]) };
  await uploadService.save(file, "../../etc", "7", "http://unused");
  assert.match(received.url, /\/chiabill\/bill\//);
  await assert.rejects(uploadService.save({ mimetype: "application/pdf", buffer: Buffer.from([1]) }, "bill", "7", ""), /Only JPEG/);
});

test("the startup check writes a ping image, or says which setting is missing", async () => {
  assert.match(await uploadService.checkStorage(), /Supabase Storage OK/);
  assert.equal(received.url, "/storage/v1/object/chiabill/healthcheck/ping.png");
  assert.equal(received.headers["content-type"], "image/png");

  const key = settings.supabase.serviceKey;
  settings.supabase.serviceKey = "";
  assert.match(await uploadService.checkStorage(), /SUPABASE_SERVICE_KEY not set/);
  settings.supabase.serviceKey = key;
});
