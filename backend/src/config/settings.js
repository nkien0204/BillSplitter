// Load backend/.env if present (local runs); hosts set real environment variables.
require("dotenv").config();

// Server settings. On a host (Render) everything comes from environment
// variables; on a laptop config/config.json works as before. An environment
// variable always wins over the file.
let file = {};
try {
  file = require("../../config/config.json");
} catch (e) {
  file = {};
}

const env = process.env.NODE_ENV || "development";
const app = file.app || {};

function databaseConfig() {
  if (process.env.DATABASE_URL) {
    // Supabase (and most hosted Postgres) needs TLS; a local Postgres for
    // tests can turn it off with DATABASE_SSL=false.
    const ssl = process.env.DATABASE_SSL !== "false";
    return {
      use_env_variable: "DATABASE_URL",
      dialect: "postgres",
      logging: false,
      dialectOptions: ssl ? { ssl: { require: true, rejectUnauthorized: false } } : {},
    };
  }
  return file[env] || {
    dialect: "sqlite",
    storage: process.env.DB_STORAGE || "./database.sqlite",
    logging: false,
  };
}

module.exports = {
  env,
  port: Number(process.env.PORT) || app.port || 3000,
  jwtSecret: process.env.JWT_SECRET || app.jwtSecret,
  db: databaseConfig(),
  // Optional: where uploaded photos go. Without these, photos are kept on the
  // server's disk, which is fine on a laptop but lost on a free host restart.
  supabase: {
    url: (process.env.SUPABASE_URL || "").replace(/\/+$/, ""),
    serviceKey: process.env.SUPABASE_SERVICE_KEY || "",
    bucket: process.env.SUPABASE_BUCKET || "chiabill",
  },
};
