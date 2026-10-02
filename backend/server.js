const config = require("./config/config.json");
const app = require("./app");
const sequelize = require("./src/config/db");

const PORT = config.app.port || 3000;

async function startServer() {
  try {
    // We now use migrations via sequelize-cli to manage the schema.
    // sync() is removed to avoid accidental schema overrides.
    console.log("🚀 Using migrations for database schema management");

    app.listen(PORT, "0.0.0.0", () => {
      console.log(`🚀 Server running on port ${PORT}`);
      console.log(`🔗 Local: http://localhost:${PORT}`);
      console.log(`🌐 Network: http://<your-ip-address>:${PORT}`);
    });
  } catch (error) {
    console.error("❌ Failed to start server:", error);
    process.exit(1);
  }
}

startServer();
