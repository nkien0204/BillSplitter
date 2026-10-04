"use strict";
module.exports = {
  up: async (queryInterface, Sequelize) => {
    await queryInterface.createTable("Sessions", {
      id: {
        type: Sequelize.STRING,
        primaryKey: true,
        allowNull: false,
      },
      userId: {
        type: Sequelize.INTEGER,
        allowNull: false,
        references: { model: "Users", key: "id" },
        onDelete: "CASCADE",
      },
      createdAt: {
        type: Sequelize.BIGINT,
        allowNull: false,
      },
      expiresAt: {
        type: Sequelize.BIGINT,
        allowNull: false,
      },
      revokedAt: {
        type: Sequelize.BIGINT,
        allowNull: true,
      },
    });
    await queryInterface.addIndex("Sessions", ["userId"]);
  },
  down: async (queryInterface, Sequelize) => {
    await queryInterface.dropTable("Sessions");
  },
};
