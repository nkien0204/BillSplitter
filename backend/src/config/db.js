const { Sequelize } = require("sequelize");
const config = require("../../config/config.json");

const env = process.env.NODE_ENV || "development";
const dbConfig = config[env];

const sequelize = new Sequelize({
  ...dbConfig,
});

module.exports = sequelize;
