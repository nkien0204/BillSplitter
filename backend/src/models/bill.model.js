const { DataTypes } = require("sequelize");
const sequelize = require("../config/db");

const Bill = sequelize.define(
  "Bill",
  {
    id: {
      type: DataTypes.STRING,
      primaryKey: true,
    },
    groupId: {
      type: DataTypes.STRING,
      allowNull: false,
    },
    title: {
      type: DataTypes.STRING,
      allowNull: false,
    },
    payerId: {
      type: DataTypes.STRING,
      allowNull: false,
    },
    mode: {
      type: DataTypes.STRING,
      defaultValue: "EQUAL",
    },
    subtotal: {
      type: DataTypes.BIGINT,
      defaultValue: 0,
    },
    participantsCsv: {
      type: DataTypes.STRING,
      defaultValue: "",
    },
    vatPercent: {
      type: DataTypes.INTEGER,
      defaultValue: 0,
    },
    servicePercent: {
      type: DataTypes.INTEGER,
      defaultValue: 0,
    },
    rounding: {
      type: DataTypes.INTEGER,
      defaultValue: 1000,
    },
    category: {
      type: DataTypes.STRING,
      defaultValue: "KHAC",
    },
    sharesCsv: {
      type: DataTypes.STRING,
      defaultValue: "",
    },
    createdBy: {
      type: DataTypes.STRING,
    },
    createdAt: {
      type: DataTypes.BIGINT,
    },
    updatedAt: {
      type: DataTypes.BIGINT,
    },
  },
  {
    timestamps: false,
  },
);

module.exports = Bill;
