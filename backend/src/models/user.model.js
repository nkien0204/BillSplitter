const { DataTypes } = require("sequelize");
const sequelize = require("../config/db");

const User = sequelize.define(
  "User",
  {
    id: {
      type: DataTypes.STRING,
      primaryKey: true,
    },
    name: {
      type: DataTypes.STRING,
      allowNull: false,
    },
    phone: {
      type: DataTypes.STRING,
      allowNull: false,
      unique: true,
    },
    password_hash: {
      type: DataTypes.STRING,
      allowNull: false,
    },
    colorIndex: {
      type: DataTypes.INTEGER,
      defaultValue: 0,
    },
    guest: {
      type: DataTypes.BOOLEAN,
      defaultValue: false,
    },
    bankBin: {
      type: DataTypes.STRING,
      allowNull: true,
    },
    accountNo: {
      type: DataTypes.STRING,
      allowNull: true,
    },
    accountName: {
      type: DataTypes.STRING,
      allowNull: true,
    },
    qrUpdatedAt: {
      type: DataTypes.BIGINT,
      defaultValue: 0,
    },
    created_at: {
      type: DataTypes.BIGINT,
      defaultValue: DataTypes.NOW,
    },
  },
  {
    timestamps: false,
  },
);

module.exports = User;
