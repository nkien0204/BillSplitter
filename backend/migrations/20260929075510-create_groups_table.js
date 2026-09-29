"use strict";
module.exports = {
  up: async (queryInterface, Sequelize) => {
    await queryInterface.createTable("Groups", {
      id: {
        type: Sequelize.STRING,
        primaryKey: true,
        allowNull: false,
      },
      name: {
        type: Sequelize.STRING,
        allowNull: false,
      },
      inviteCode: {
        type: Sequelize.STRING,
        unique: true,
      },
      createdBy: {
        type: Sequelize.STRING,
      },
      createdAt: {
        type: Sequelize.BIGINT,
      },
    });
  },
  down: async (queryInterface, Sequelize) => {
    await queryInterface.dropTable("Groups");
  },
};
