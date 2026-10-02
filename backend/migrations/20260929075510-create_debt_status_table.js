"use strict";
module.exports = {
  up: async (queryInterface, Sequelize) => {
    await queryInterface.createTable("DebtStatus", {
      debtKey: {
        type: Sequelize.STRING,
        primaryKey: true,
        allowNull: false,
      },
      billId: {
        type: Sequelize.STRING,
        allowNull: false,
      },
      status: {
        type: Sequelize.STRING,
        defaultValue: "PENDING",
      },
      updatedAt: {
        type: Sequelize.BIGINT,
      },
    });
  },
  down: async (queryInterface, Sequelize) => {
    await queryInterface.dropTable("DebtStatus");
  },
};
