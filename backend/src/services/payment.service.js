const DebtStatus = require("../models/debtStatus.model");
const Bill = require("../models/bill.model");
const BillItem = require("../models/billItem.model");
const Member = require("../models/member.model");
const User = require("../models/user.model");

class PaymentService {
  async markAsPaid(userId, billId) {
    const debtKey = `${billId}:${userId}`;
    const status = await DebtStatus.findByPk(debtKey);

    if (!status) {
      // Create status if it doesn't exist (first time marking paid)
      await DebtStatus.create({
        debtKey,
        billId,
        status: "DEBTOR_PAID",
        updatedAt: Date.now(),
      });
    } else {
      await status.update({
        status: "DEBTOR_PAID",
        updatedAt: Date.now(),
      });
    }

    return { success: true };
  }

  async confirmPayment(userId, billId, debtorId) {
    const bill = await Bill.findByPk(billId);
    if (!bill) throw new Error("Bill not found");
    if (bill.payerId !== userId)
      throw new Error("Only the payer can confirm payment");

    const debtKey = `${billId}:${debtorId}`;
    const status = await DebtStatus.findByPk(debtKey);

    if (!status || status.status !== "DEBTOR_PAID") {
      throw new Error("Payment has not been marked as paid by the debtor");
    }

    await status.update({
      status: "CONFIRMED",
      updatedAt: Date.now(),
    });

    return { success: true };
  }

  async disputePayment(userId, billId, debtorId) {
    const bill = await Bill.findByPk(billId);
    if (!bill) throw new Error("Bill not found");
    if (bill.payerId !== userId)
      throw new Error("Only the payer can dispute payment");

    const debtKey = `${billId}:${debtorId}`;
    const status = await DebtStatus.findByPk(debtKey);

    if (!status) throw new Error("No payment record found");

    await status.update({
      status: "DISPUTED",
      updatedAt: Date.now(),
    });

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

module.exports = new PaymentService();
