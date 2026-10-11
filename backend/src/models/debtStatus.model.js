const { DataTypes } = require("sequelize");
const sequelize = require("../config/db");

const DebtStatus = sequelize.define(
  "DebtStatus",
  {
    debtKey: {
      type: DataTypes.STRING,
      primaryKey: true,
    },
    billId: {
      type: DataTypes.STRING,
      allowNull: false,
    },
    status: {
      type: DataTypes.STRING,
      defaultValue: "PENDING",
    },
    updatedAt: {
      type: DataTypes.BIGINT,
    },
    proofImageUrl: {
      type: DataTypes.STRING(1024),
      allowNull: true,
    },
  },
  {
    tableName: "DebtStatus",
    timestamps: false,
  },
);

module.exports = DebtStatus;
