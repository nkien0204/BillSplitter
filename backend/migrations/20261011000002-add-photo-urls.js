"use strict";
// v0.4.0: a photo of the bill, and a screenshot of the transfer sent with
// "Tôi đã chuyển". Both are URLs; the files live in storage, not in the DB.
module.exports = {
  up: async (queryInterface, Sequelize) => {
    await queryInterface.addColumn("Bills", "imageUrl", {
      type: Sequelize.STRING(1024),
      allowNull: true,
    });
    await queryInterface.addColumn("DebtStatus", "proofImageUrl", {
      type: Sequelize.STRING(1024),
      allowNull: true,
    });
  },
  down: async (queryInterface) => {
    await queryInterface.removeColumn("Bills", "imageUrl");
    await queryInterface.removeColumn("DebtStatus", "proofImageUrl");
  },
};
