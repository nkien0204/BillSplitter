const Group = require("../models/group.model");
const Member = require("../models/member.model");
const User = require("../models/user.model");
const Bill = require("../models/bill.model");
const BillItem = require("../models/billItem.model");
const DebtStatus = require("../models/debtStatus.model");
const Inbox = require("../models/inbox.model");
const sequelize = require("../config/db");
const { v4: uuidv4 } = require("uuid");

class GroupService {
  async createGroup(userId, groupData) {
    const { name } = groupData;
    const inviteCode = this._generateInviteCode();

    // Unique, non-empty member ids, excluding the creator (always position 0).
    const rawIds = Array.isArray(groupData.memberIds) ? groupData.memberIds : [];
    const memberIds = [...new Set(rawIds)].filter(
      (id) => typeof id === "string" && id && id !== userId,
    );

    // Group and all memberships are written atomically: if any insert fails,
    // everything rolls back so no orphaned or half-populated group is left.
    return sequelize.transaction(async (transaction) => {
      if (memberIds.length > 0) {
        const found = await User.count({
          where: { id: memberIds },
          transaction,
        });
        if (found !== memberIds.length) {
          throw new Error("One or more members do not exist");
        }
      }

      const group = await Group.create(
        {
          id: uuidv4(),
          name,
          inviteCode,
          createdBy: userId,
          createdAt: Date.now(),
        },
        { transaction },
      );

      // Add creator as first member
      await Member.create(
        {
          groupId: group.id,
          userId: userId,
          position: 0,
          active: true,
        },
        { transaction },
      );

      // Remaining members keep the order they were given.
      if (memberIds.length > 0) {
        await Member.bulkCreate(
          memberIds.map((id, i) => ({
            groupId: group.id,
            userId: id,
            position: i + 1,
            active: true,
          })),
          { transaction },
        );
      }

      return group;
    });
  }

  async joinGroup(userId, inviteCode) {
    const group = await Group.findOne({ where: { inviteCode } });
    if (!group) {
      throw new Error("Group not found");
    }

    const existingMember = await Member.findOne({
      where: { groupId: group.id, userId: userId },
    });

    if (existingMember) {
      if (existingMember.active) {
        throw new Error("User is already a member of this group");
      }
      // Re-activate member
      await Member.update(
        { active: true },
        { where: { groupId: group.id, userId: userId } },
      );
    } else {
      // Find current max position to set the new member's position
      const members = await Member.findAll({ where: { groupId: group.id } });
      const position = members.length;

      await Member.create({
        groupId: group.id,
        userId: userId,
        position: position,
        active: true,
      });
    }

    return group;
  }

  async getUserGroups(userId) {
    const memberships = await Member.findAll({
      where: { userId, active: true },
    });

    const groupIds = memberships.map((m) => m.groupId);
    return Group.findAll({
      where: { id: groupIds },
    });
  }

  async getGroupMembers(groupId) {
    const members = await Member.findAll({
      where: { groupId, active: true },
      order: [["position", "ASC"]],
    });

    const userIds = members.map((m) => m.userId);
    const users = await User.findAll({
      where: { id: userIds },
    });

    // Map users back to members to maintain position order
    return members.map((m) => {
      const user = users.find((u) => u.id === m.userId);
      return {
        ...m.toJSON(),
        user: user ? user.toJSON() : null,
      };
    });
  }

  async removeMember(groupId, userId, requestUserId) {
    const group = await Group.findOne({ where: { id: groupId } });
    if (!group) throw new Error("Group not found");
    if (group.createdBy !== requestUserId) {
      throw new Error("Only the group creator can remove members");
    }

    await Member.update({ active: false }, { where: { groupId, userId } });
    return { success: true };
  }

  async addMember(groupId, userId, actorId) {
    const group = await Group.findByPk(groupId);
    if (!group) throw new Error("Group not found");

    // Check if actor is in the group
    const actor = await Member.findOne({ where: { groupId, userId: actorId } });
    if (!actor || !actor.active)
      throw new Error("Only group members can add other members");

    const existingMember = await Member.findOne({ where: { groupId, userId } });
    if (existingMember) {
      if (existingMember.active) throw new Error("User is already a member");
      await Member.update({ active: true }, { where: { groupId, userId } });
    } else {
      const members = await Member.findAll({ where: { groupId } });
      await Member.create({
        groupId,
        userId,
        position: members.length,
        active: true,
      });
    }
    return { success: true };
  }

  /**
   * Deletes a group with all its bills, items, debt statuses, related inbox
   * messages and memberships. Only the creator may do it. Everything is removed
   * in one transaction; active members are notified in the same transaction.
   *
   * Money checks (e.g. unsettled debts) are done by the client: shares are
   * derived from bills by the domain split engine, which the server lacks.
   */
  async deleteGroup(groupId, actorId) {
    return sequelize.transaction(async (transaction) => {
      const group = await Group.findByPk(groupId, { transaction });
      if (!group) throw this._error(404, "Group not found");
      if (group.createdBy !== actorId)
        throw this._error(403, "Only the group creator can delete the group");

      const members = await Member.findAll({ where: { groupId }, transaction });
      const bills = await Bill.findAll({
        where: { groupId },
        attributes: ["id"],
        transaction,
      });
      const billIds = bills.map((b) => b.id);

      if (billIds.length > 0) {
        await BillItem.destroy({ where: { billId: billIds }, transaction });
        await DebtStatus.destroy({ where: { billId: billIds }, transaction });
        await Inbox.destroy({ where: { billId: billIds }, transaction });
        await Bill.destroy({ where: { id: billIds }, transaction });
      }
      await Member.destroy({ where: { groupId }, transaction });
      await Group.destroy({ where: { id: groupId }, transaction });

      const actor = await User.findByPk(actorId, { transaction });
      const now = Date.now();
      const recipients = members.filter((m) => m.active && m.userId !== actorId);
      if (recipients.length > 0) {
        await Inbox.bulkCreate(
          recipients.map((m) => ({
            recipientId: m.userId,
            message: `${actor ? actor.name : "Người tạo nhóm"} đã xoá nhóm “${group.name}”.`,
            createdAt: now,
          })),
          { transaction },
        );
      }
      return { success: true };
    });
  }

  _error(status, message) {
    const err = new Error(message);
    err.status = status;
    return err;
  }

  async leaveGroup(groupId, userId) {
    const member = await Member.findOne({ where: { groupId, userId } });
    if (!member) throw new Error("User is not a member of this group");

    // In a real app, we'd check if user has debts here
    await Member.update({ active: false }, { where: { groupId, userId } });
    return { success: true };
  }

  _generateInviteCode() {
    const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    let result = "";
    for (let i = 0; i < 6; i++) {
      result += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return result;
  }
}

module.exports = new GroupService();
