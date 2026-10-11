// Used by sequelize-cli (see .sequelizerc), so migrations run against the same
// database as the server: DATABASE_URL when set, otherwise config/config.json.
const settings = require("../src/config/settings");

module.exports = {
  [settings.env]: settings.db,
};
