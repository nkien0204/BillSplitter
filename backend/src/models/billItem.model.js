const { DataTypes } = require("sequelize");
const sequelize = require("../config/db");

const BillItem = sequelize.define(
  "BillItem",
  {
    id: {
      type: DataTypes.INTEGER,
      primaryKey: true,
      autoIncrement: true,
    },
    billId: {
      type: DataTypes.STRING,
      allowNull: false,
    },
    position: {
      type: DataTypes.INTEGER,
      defaultValue: 0,
    },
    name: {
      type: DataTypes.STRING,
      allowNull: false,
    },
    price: {
      type: DataTypes.BIGINT,
      defaultValue: 0,
    },
    consumersCsv: {
      type: DataTypes.STRING,
      defaultValue: "",
    },
  },
  {
    timestamps: false,
  },
);

module.exports = BillItem;
