const { DataTypes } = require("sequelize");
const sequelize = require("../config/db");

// One row per login. The JWT carries the row id as its `sid` claim; revoking
// a session (logout) sets revokedAt, which the auth middleware checks on
// every request.
const Session = sequelize.define(
  "Session",
  {
    id: {
      type: DataTypes.STRING,
      primaryKey: true,
    },
    userId: {
      type: DataTypes.INTEGER,
      allowNull: false,
    },
    createdAt: {
      type: DataTypes.BIGINT,
      allowNull: false,
    },
    expiresAt: {
      type: DataTypes.BIGINT,
      allowNull: false,
    },
    revokedAt: {
      type: DataTypes.BIGINT,
      allowNull: true,
    },
  },
  {
    tableName: "Sessions",
    timestamps: false,
  },
);

module.exports = Session;
