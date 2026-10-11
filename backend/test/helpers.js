// Shared setup for the backend tests. The database comes from the environment:
// DATABASE_URL (Postgres, like Supabase) or a throwaway SQLite file.
const { execSync } = require("child_process");
const os = require("os");
const path = require("path");

process.env.NODE_ENV = process.env.NODE_ENV || "test";
process.env.JWT_SECRET = process.env.JWT_SECRET || "test-secret";
if (!process.env.DATABASE_URL) {
  process.env.DB_STORAGE = path.join(os.tmpdir(), `chiabill-test-${process.pid}.sqlite`);
}

function resetDatabase() {
  const opts = { cwd: path.resolve(__dirname, ".."), env: process.env, stdio: "pipe" };
  execSync("npx sequelize-cli db:migrate:undo:all", opts);
  execSync("npx sequelize-cli db:migrate", opts);
}

const request = require("supertest");
const app = require("../app");

// One "device": a signed-in account that sends requests with its own token.
async function device(name, phone) {
  const res = await request(app)
    .post("/api/auth/register")
    .send({ name, phone, password: "matkhau123" });
  if (res.status !== 201 && res.status !== 200) {
    throw new Error(`register ${phone}: ${res.status} ${JSON.stringify(res.body)}`);
  }
  const token = res.body.token;
  const id = String(res.body.user.id);
  const auth = (r) => r.set("Authorization", `Bearer ${token}`);
  return {
    id,
    name,
    get: (url) => auth(request(app).get(url)),
    post: (url, body) => auth(request(app).post(url)).send(body),
    put: (url, body) => auth(request(app).put(url)).send(body),
    del: (url) => auth(request(app).delete(url)),
    upload: (url) => auth(request(app).post(url)),
  };
}

module.exports = { app, request, device, resetDatabase };
