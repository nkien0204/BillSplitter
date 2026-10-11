const settings = require("./src/config/settings");
const app = require("./app");
const sequelize = require("./src/config/db");

const PORT = settings.port;

async function startServer() {
  try {
    if (!settings.jwtSecret) {
      throw new Error("JWT secret missing: set JWT_SECRET or app.jwtSecret in config/config.json");
    }
    // Schema changes only through migrations (npm run migrate); sync() is never used.
    await sequelize.authenticate();
    console.log(`🚀 Database ready (${settings.db.dialect})`);

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
