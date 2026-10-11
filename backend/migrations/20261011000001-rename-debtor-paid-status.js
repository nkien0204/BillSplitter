"use strict";
// The server used to store "DEBTOR_PAID" while the app reads "MARKED_PAID",
// so a debt marked as paid still showed as unpaid to the payer.
module.exports = {
  up: async (queryInterface) => {
    await queryInterface.bulkUpdate(
      "DebtStatus",
      { status: "MARKED_PAID" },
      { status: "DEBTOR_PAID" },
    );
  },
  down: async (queryInterface) => {
    await queryInterface.bulkUpdate(
      "DebtStatus",
      { status: "DEBTOR_PAID" },
      { status: "MARKED_PAID" },
    );
  },
};
