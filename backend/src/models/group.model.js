const { DataTypes } = require("sequelize");
const sequelize = require("../config/db");

const Group = sequelize.define(
  "Group",
  {
    id: {
      type: DataTypes.STRING,
      primaryKey: true,
    },
    name: {
      type: DataTypes.STRING,
      allowNull: false,
    },
    inviteCode: {
      type: DataTypes.STRING,
      unique: true,
    },
    createdBy: {
      type: DataTypes.STRING,
    },
    createdAt: {
      type: DataTypes.BIGINT,
    },
  },
  {
    timestamps: false,
  },
);

module.exports = Group;
