const Group = require("../models/group.model");
const Member = require("../models/member.model");
const User = require("../models/user.model");
const Bill = require("../models/bill.model");
const BillItem = require("../models/billItem.model");
const DebtStatus = require("../models/debtStatus.model");
const Inbox = require("../models/inbox.model");

const INBOX_LIMIT = 100;

class SyncService {
  /**
   * Everything one account needs on its device, in one response: the groups it
   * is in, every member those groups ever had (old bills are split over all of
   * them), the users behind those members, bills with items, debt statuses and
   * the latest inbox messages. The app replaces its local copy with this, so
   * deletions and other people's changes show up too.
   */
  async snapshotFor(userId) {
    const memberships = await Member.findAll({ where: { userId, active: true } });
    const groupIds = memberships.map((m) => m.groupId);

    const [groups, members, bills] = groupIds.length
      ? await Promise.all([
          Group.findAll({ where: { id: groupIds } }),
          Member.findAll({ where: { groupId: groupIds }, order: [["position", "ASC"]] }),
          Bill.findAll({ where: { groupId: groupIds }, order: [["createdAt", "ASC"]] }),
        ])
      : [[], [], []];

    const billIds = bills.map((b) => b.id);
    const [items, statuses] = billIds.length
      ? await Promise.all([
          BillItem.findAll({ where: { billId: billIds }, order: [["position", "ASC"]] }),
          DebtStatus.findAll({ where: { billId: billIds } }),
        ])
      : [[], []];

    const userIds = [...new Set([String(userId), ...members.map((m) => String(m.userId))])];
    const users = await User.findAll({
      where: { id: userIds },
      attributes: { exclude: ["password_hash"] },
    });

    const inbox = await Inbox.findAll({
      where: { recipientId: String(userId) },
      order: [["id", "DESC"]],
      limit: INBOX_LIMIT,
    });

    const itemsByBill = {};
    for (const it of items) {
      (itemsByBill[it.billId] = itemsByBill[it.billId] || []).push(it.toJSON());
    }

    return {
      serverTime: Date.now(),
      groups: groups.map((g) => g.toJSON()),
      members: members.map((m) => m.toJSON()),
      users: users.map((u) => u.toJSON()),
      bills: bills.map((b) => ({ bill: b.toJSON(), items: itemsByBill[b.id] || [] })),
      debtStatuses: statuses.map((s) => s.toJSON()),
      inbox: inbox.map((i) => i.toJSON()),
    };
  }
}

module.exports = new SyncService();
