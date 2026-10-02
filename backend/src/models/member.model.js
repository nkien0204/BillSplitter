const { DataTypes } = require("sequelize");
const sequelize = require("../config/db");

const Member = sequelize.define(
  "Member",
  {
    groupId: {
      type: DataTypes.STRING,
      primaryKey: true,
    },
    userId: {
      type: DataTypes.STRING,
      primaryKey: true,
    },
    position: {
      type: DataTypes.INTEGER,
      defaultValue: 0,
    },
    active: {
      type: DataTypes.BOOLEAN,
      defaultValue: true,
    },
  },
  {
    timestamps: false,
  },
);

module.exports = Member;
