const { DataTypes } = require("sequelize");
const sequelize = require("../config/db");

const Inbox = sequelize.define(
  "Inbox",
  {
    id: {
      type: DataTypes.INTEGER,
      primaryKey: true,
      autoIncrement: true,
    },
    recipientId: {
      type: DataTypes.STRING,
      allowNull: false,
    },
    message: {
      type: DataTypes.STRING,
      allowNull: false,
    },
    billId: {
      type: DataTypes.STRING,
    },
    debtKey: {
      type: DataTypes.STRING,
    },
    createdAt: {
      type: DataTypes.BIGINT,
    },
  },
  {
    tableName: "Inbox",
    timestamps: false,
  },
);

module.exports = Inbox;
