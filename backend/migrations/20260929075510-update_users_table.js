"use strict";
module.exports = {
  up: async (queryInterface, Sequelize) => {
    await queryInterface.addColumn("Users", "colorIndex", {
      type: Sequelize.INTEGER,
      defaultValue: 0,
    });
    await queryInterface.addColumn("Users", "guest", {
      type: Sequelize.BOOLEAN,
      defaultValue: false,
    });
    await queryInterface.addColumn("Users", "bankBin", {
      type: Sequelize.STRING,
      allowNull: true,
    });
    await queryInterface.addColumn("Users", "accountNo", {
      type: Sequelize.STRING,
      allowNull: true,
    });
    await queryInterface.addColumn("Users", "accountName", {
      type: Sequelize.STRING,
      allowNull: true,
    });
    await queryInterface.addColumn("Users", "qrUpdatedAt", {
      type: Sequelize.BIGINT,
      defaultValue: 0,
    });
  },
  down: async (queryInterface, Sequelize) => {
    await queryInterface.removeColumn("Users", "colorIndex");
    await queryInterface.removeColumn("Users", "guest");
    await queryInterface.removeColumn("Users", "bankBin");
    await queryInterface.removeColumn("Users", "accountNo");
    await queryInterface.removeColumn("Users", "accountName");
    await queryInterface.removeColumn("Users", "qrUpdatedAt");
  },
};
