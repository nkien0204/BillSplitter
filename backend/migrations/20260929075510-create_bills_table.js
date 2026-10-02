"use strict";
module.exports = {
  up: async (queryInterface, Sequelize) => {
    await queryInterface.createTable("Bills", {
      id: {
        type: Sequelize.STRING,
        primaryKey: true,
        allowNull: false,
      },
      groupId: {
        type: Sequelize.STRING,
        allowNull: false,
      },
      title: {
        type: Sequelize.STRING,
        allowNull: false,
      },
      payerId: {
        type: Sequelize.STRING,
        allowNull: false,
      },
      mode: {
        type: Sequelize.STRING,
        defaultValue: "EQUAL",
      },
      subtotal: {
        type: Sequelize.BIGINT,
        defaultValue: 0,
      },
      participantsCsv: {
        type: Sequelize.STRING,
        defaultValue: "",
      },
      vatPercent: {
        type: Sequelize.INTEGER,
        defaultValue: 0,
      },
      servicePercent: {
        type: Sequelize.INTEGER,
        defaultValue: 0,
      },
      rounding: {
        type: Sequelize.INTEGER,
        defaultValue: 1000,
      },
      category: {
        type: Sequelize.STRING,
        defaultValue: "KHAC",
      },
      sharesCsv: {
        type: Sequelize.STRING,
        defaultValue: "",
      },
      createdBy: {
        type: Sequelize.STRING,
      },
      createdAt: {
        type: Sequelize.BIGINT,
      },
      updatedAt: {
        type: Sequelize.BIGINT,
      },
    });
  },
  down: async (queryInterface, Sequelize) => {
    await queryInterface.dropTable("Bills");
  },
};
