const Bill = require("../models/bill.model");
const BillItem = require("../models/billItem.model");
const Group = require("../models/group.model");
const Member = require("../models/member.model");
const { v4: uuidv4 } = require("uuid");

class BillService {
  async createBill(userId, billData) {
    const { groupId, title, payerId, mode, subtotal, participantsCsv, vatPercent, servicePercent, rounding, category, sharesCsv } = billData;

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
      createdBy: userId,
      createdAt: Date.now(),
      updatedAt: Date.now(),
    });

    // If ITEMIZED, handle bill items
    if (mode === "ITEMIZED" && billData.items) {
      const itemPromises = billData.items.map((item) =>
        BillItem.create({
          billId: bill.id,
          position: item.position,
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

    await bill.update({
      ...updateData,
      updatedAt: Date.now(),
    });

    // If mode changed to ITEMIZED or items updated
    if (updateData.mode === "ITEMIZED" && updateData.items) {
      await BillItem.destroy({ where: { billId: bill.id } });
      const itemPromises = updateData.items.map((item) =>
        BillItem.create({
          billId: bill.id,
          position: item.position,
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
    // Also destroy associated debt statuses
    // await DebtStatus.destroy({ where: { billId: billId } });

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
