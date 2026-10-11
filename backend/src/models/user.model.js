const { DataTypes } = require("sequelize");
const sequelize = require("../config/db");

const User = sequelize.define(
  "User",
  {
    // Matches the autoincrement INTEGER column. As STRING, Postgres got an
    // explicit NULL id on insert and refused it.
    id: {
      type: DataTypes.INTEGER,
      primaryKey: true,
      autoIncrement: true,
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
    // A timestamp column (see the create-users migration); BIGINT here made
    // Postgres reject every registration.
    created_at: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW,
    },
  },
  {
    timestamps: false,
  },
);

module.exports = User;
