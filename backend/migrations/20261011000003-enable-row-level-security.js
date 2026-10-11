"use strict";
// On Supabase every table in "public" is also reachable through its REST API
// with the project's public key. Turning row level security on with no
// policies closes that door. The server is unaffected: it connects as the
// role that owns these tables, and owners bypass row level security.
const TABLES = ["Users", "Sessions", "Groups", "Members", "Bills", "BillItems", "DebtStatus", "Inbox", "SequelizeMeta"];

module.exports = {
  up: async (queryInterface) => {
    if (queryInterface.sequelize.getDialect() !== "postgres") return;
    for (const table of TABLES) {
      await queryInterface.sequelize.query(`ALTER TABLE "${table}" ENABLE ROW LEVEL SECURITY`);
    }
  },
  down: async (queryInterface) => {
    if (queryInterface.sequelize.getDialect() !== "postgres") return;
    for (const table of TABLES) {
      await queryInterface.sequelize.query(`ALTER TABLE "${table}" DISABLE ROW LEVEL SECURITY`);
    }
  },
};
