const DebtStatus = require("../models/debtStatus.model");
const Bill = require("../models/bill.model");
const BillItem = require("../models/billItem.model");
const Member = require("../models/member.model");

// Same names as the app's domain.model.DebtStatus, so a status written here
// is read back unchanged on every device.
const STATUS = {
  PENDING: "PENDING",
  MARKED_PAID: "MARKED_PAID",
  CONFIRMED: "CONFIRMED",
  DISPUTED: "DISPUTED",
};

class PaymentService {
  // Debtor: PENDING or DISPUTED -> MARKED_PAID.
  async markAsPaid(userId, billId) {
    const bill = await Bill.findByPk(billId);
    if (!bill) throw new Error("Bill not found");

    const debtKey = `${billId}:${userId}`;
    const status = await DebtStatus.findByPk(debtKey);
    if (status && status.status === STATUS.CONFIRMED) {
      throw new Error("Payment is already confirmed");
    }

    await DebtStatus.upsert({
      debtKey,
      billId,
      status: STATUS.MARKED_PAID,
      updatedAt: Date.now(),
    });
    return { success: true };
  }

  // Payer: anything open -> CONFIRMED. From PENDING this is "received in cash".
  async confirmPayment(userId, billId, debtorId) {
    const bill = await Bill.findByPk(billId);
    if (!bill) throw new Error("Bill not found");
    if (bill.payerId !== userId)
      throw new Error("Only the payer can confirm payment");

    await DebtStatus.upsert({
      debtKey: `${billId}:${debtorId}`,
      billId,
      status: STATUS.CONFIRMED,
      updatedAt: Date.now(),
    });
    return { success: true };
  }

  // Payer: MARKED_PAID -> DISPUTED.
  async disputePayment(userId, billId, debtorId) {
    const bill = await Bill.findByPk(billId);
    if (!bill) throw new Error("Bill not found");
    if (bill.payerId !== userId)
      throw new Error("Only the payer can dispute payment");

    const status = await DebtStatus.findByPk(`${billId}:${debtorId}`);
    if (!status || status.status !== STATUS.MARKED_PAID) {
      throw new Error("Payment has not been marked as paid by the debtor");
    }

    await status.update({ status: STATUS.DISPUTED, updatedAt: Date.now() });
    return { success: true };
  }

  async getGroupBalances(groupId) {
    const bills = await Bill.findAll({ where: { groupId } });
    const members = await Member.findAll({ where: { groupId } });
    const billIds = bills.map((b) => b.id);
    const debtStatuses = billIds.length
      ? await DebtStatus.findAll({ where: { billId: billIds } })
      : [];

    // Shape matches the app's BillDetailsResponse: { bill, items }.
    const billsWithItems = await Promise.all(
      bills.map(async (bill) => {
        const items = await BillItem.findAll({
          where: { billId: bill.id },
          order: [["position", "ASC"]],
        });
        return {
          bill: bill.toJSON(),
          items: items.map((i) => i.toJSON()),
        };
      }),
    );

    return {
      bills: billsWithItems,
      members: members.map((m) => m.toJSON()),
      debtStatuses: debtStatuses.map((d) => d.toJSON()),
    };
  }
}

PaymentService.prototype.STATUS = STATUS;
module.exports = new PaymentService();
