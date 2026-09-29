"use strict";
module.exports = {
  up: async (queryInterface, Sequelize) => {
    await queryInterface.createTable("BillItems", {
      id: {
        type: Sequelize.INTEGER,
        primaryKey: true,
        autoIncrement: true,
        allowNull: false,
      },
      billId: {
        type: Sequelize.STRING,
        allowNull: false,
      },
      position: {
        type: Sequelize.INTEGER,
        defaultValue: 0,
      },
      name: {
        type: Sequelize.STRING,
        allowNull: false,
      },
      price: {
        type: Sequelize.BIGINT,
        defaultValue: 0,
      },
      consumersCsv: {
        type: Sequelize.STRING,
        defaultValue: "",
      },
    });
  },
  down: async (queryInterface, Sequelize) => {
    await queryInterface.dropTable("BillItems");
  },
};
