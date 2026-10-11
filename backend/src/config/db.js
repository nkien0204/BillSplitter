const { Sequelize } = require("sequelize");
const settings = require("./settings");

if (settings.db.dialect === "postgres") {
  // BIGINT columns hold VND amounts and millisecond timestamps; return them as
  // numbers (they fit in a double) so the app's Gson sees the same JSON as with SQLite.
  require("pg").defaults.parseInt8 = true;
}

const sequelize = settings.db.use_env_variable
  ? new Sequelize(process.env[settings.db.use_env_variable], settings.db)
  : new Sequelize({ ...settings.db });

module.exports = sequelize;
