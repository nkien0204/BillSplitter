// Performance test: how the server holds up when phones keep syncing.
// Seeds one group of 3 people with 200 bills, then hammers the endpoints the
// app calls most with autocannon. Prints a table and writes results.json.
//   npm run test:perf            (SQLite)
//   DATABASE_URL=... npm run test:perf   (Postgres)
const fs = require("fs");
const path = require("path");
const autocannon = require("autocannon");
const { app, device, resetDatabase } = require("../helpers");
const sequelize = require("../../src/config/db");

const BILLS = Number(process.env.PERF_BILLS || 200);
const DURATION = Number(process.env.PERF_SECONDS || 10);

function run(opts) {
  return new Promise((resolve, reject) => autocannon(opts, (err, r) => (err ? reject(err) : resolve(r))));
}

async function main() {
  resetDatabase();
  const an = await device("An", "0902000001");
  const binh = await device("Bình", "0902000002");
  const chi = await device("Chi", "0902000003");
  const group = (await an.post("/api/groups", { name: "Perf", memberIds: [binh.id, chi.id] })).body;
  const ids = [an.id, binh.id, chi.id].join(",");
  for (let i = 0; i < BILLS; i++) {
    await an.post("/api/bills", {
      groupId: group.id, title: `Bill ${i}`, payerId: an.id, mode: "EQUAL",
      subtotal: 90000 + i * 1000, participantsCsv: ids, rounding: 1, category: "AN_UONG",
    });
  }
  const login = await require("supertest")(app).post("/api/auth/login").send({ phone: "0902000002", password: "matkhau123" });
  const token = login.body.token;

  const server = app.listen(0, "127.0.0.1");
  await new Promise((r) => server.once("listening", r));
  const base = `http://127.0.0.1:${server.address().port}`;
  const headers = { Authorization: `Bearer ${token}` };

  const scenarios = [
    { name: "GET /health", url: `${base}/health`, connections: 10 },
    { name: "GET /api/sync, 3 phones", url: `${base}/api/sync`, connections: 3, headers },
    { name: "GET /api/sync, 30 phones", url: `${base}/api/sync`, connections: 30, headers },
    { name: "GET /api/payments/.../balances, 3 phones", url: `${base}/api/payments/group/${group.id}/balances`, connections: 3, headers },
  ];

  const results = [];
  for (const s of scenarios) {
    const r = await run({ url: s.url, connections: s.connections, duration: DURATION, headers: s.headers });
    results.push({
      scenario: s.name,
      connections: s.connections,
      requestsPerSec: Math.round(r.requests.average),
      latencyP50ms: r.latency.p50,
      latencyP99ms: r.latency.p99,
      errors: r.errors + r.non2xx,
      total: r.requests.total,
    });
  }
  server.close();
  await sequelize.close();

  const meta = { date: new Date().toISOString(), db: sequelize.getDialect(), bills: BILLS, seconds: DURATION, node: process.version };
  console.table(results);
  console.log(meta);
  fs.writeFileSync(path.join(__dirname, "results.json"), JSON.stringify({ meta, results }, null, 2) + "\n");
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
