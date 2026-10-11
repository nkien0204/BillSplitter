const Bill = require("../models/bill.model");
const BillItem = require("../models/billItem.model");
const Group = require("../models/group.model");
const Member = require("../models/member.model");
const DebtStatus = require("../models/debtStatus.model");
const { v4: uuidv4 } = require("uuid");
const { Op } = require("sequelize");

class BillService {
  async createBill(userId, billData) {
    const { groupId, title, payerId, mode, subtotal, participantsCsv, vatPercent, servicePercent, rounding, category, sharesCsv, imageUrl } = billData;

    const group = await Group.findByPk(groupId);
    if (!group) throw new Error("Group not found");

    // Verify user is in the group
    const member = await Member.findOne({ where: { groupId, userId } });
    if (!member || !member.active) throw new Error("User must be a member of the group to create a bill");

    const bill = await Bill.create({
      id: uuidv4(),
      groupId,
      title,
      payerId,
      mode: mode || "EQUAL",
      subtotal: subtotal || 0,
      participantsCsv: participantsCsv || "",
      vatPercent: vatPercent || 0,
      servicePercent: servicePercent || 0,
      rounding: rounding || 1000,
      category: category || "KHAC",
      sharesCsv: sharesCsv || "",
      imageUrl: imageUrl || null,
      createdBy: userId,
      // The bill's date as picked in the app (the day it was paid); now if missing.
      createdAt: Number(billData.createdAt) > 0 ? Number(billData.createdAt) : Date.now(),
      updatedAt: Date.now(),
    });

    // If ITEMIZED, handle bill items
    if (mode === "ITEMIZED" && billData.items) {
      const itemPromises = billData.items.map((item, i) =>
        BillItem.create({
          billId: bill.id,
          // List order is the item order; the app does not number them.
          position: i,
          name: item.name,
          price: item.price,
          consumersCsv: item.consumersCsv,
        })
      );
      await Promise.all(itemPromises);
    }

    return bill;
  }

  async updateBill(userId, billId, updateData) {
    const bill = await Bill.findByPk(billId);
    if (!bill) throw new Error("Bill not found");

    // Permission check: Only creator or payer can edit
    if (bill.createdBy !== userId && bill.payerId !== userId) {
      throw new Error("You do not have permission to edit this bill");
    }

    // Check if any payments were already confirmed (FR7)
    // This would require checking DebtStatus for "CONFIRMED"
    // I'll implement this check once I have the DebtStatus service

    // Only bill fields may change; id, group and creator stay as they are.
    const editable = ["title", "payerId", "mode", "subtotal", "participantsCsv", "vatPercent",
      "servicePercent", "rounding", "category", "sharesCsv", "imageUrl"];
    const changes = {};
    for (const key of editable) if (updateData[key] !== undefined) changes[key] = updateData[key];
    if (Number(updateData.createdAt) > 0) changes.createdAt = Number(updateData.createdAt);
    await bill.update({ ...changes, updatedAt: Date.now() });

    // The app computes shares; it names the debtors whose amount (or payer)
    // changed. Their "paid"/"disputed" status no longer applies. Confirmed stays.
    const reset = String(updateData.resetDebtorsCsv || "").split(",").map((s) => s.trim()).filter(Boolean);
    if (reset.length > 0) {
      await DebtStatus.destroy({
        where: {
          debtKey: reset.map((d) => `${bill.id}:${d}`),
          status: { [Op.ne]: "CONFIRMED" },
        },
      });
    }

    // Items only matter for ITEMIZED; switching away drops the old ones.
    if (updateData.mode && updateData.mode !== "ITEMIZED") {
      await BillItem.destroy({ where: { billId: bill.id } });
    } else if (updateData.mode === "ITEMIZED" && updateData.items) {
      await BillItem.destroy({ where: { billId: bill.id } });
      const itemPromises = updateData.items.map((item, i) =>
        BillItem.create({
          billId: bill.id,
          position: i,
          name: item.name,
          price: item.price,
          consumersCsv: item.consumersCsv,
        })
      );
      await Promise.all(itemPromises);
    }

    return bill;
  }

  async deleteBill(userId, billId) {
    const bill = await Bill.findByPk(billId);
    if (!bill) throw new Error("Bill not found");

    if (bill.createdBy !== userId && bill.payerId !== userId) {
      throw new Error("You do not have permission to delete this bill");
    }

    await Bill.destroy({ where: { id: billId } });
    await BillItem.destroy({ where: { billId: billId } });
    await DebtStatus.destroy({ where: { billId: billId } });

    return { success: true };
  }

  async getBillsByGroup(groupId) {
    const bills = await Bill.findAll({
      where: { groupId },
      order: [["createdAt", "DESC"]],
    });
    return bills;
  }

  async getBillDetails(billId) {
    const bill = await Bill.findByPk(billId);
    if (!bill) throw new Error("Bill not found");

    const items = await BillItem.findAll({
      where: { billId },
      order: [["position", "ASC"]],
    });

    return {
      ...bill.toJSON(),
      items: items.map(i => i.toJSON()),
    };
  }
}

module.exports = new BillService();
