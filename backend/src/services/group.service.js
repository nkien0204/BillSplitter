const Group = require("../models/group.model");
const Member = require("../models/member.model");
const User = require("../models/user.model");
const { v4: uuidv4 } = require("uuid");

class GroupService {
  async createGroup(userId, groupData) {
    const { name } = groupData;
    const inviteCode = this._generateInviteCode();

    const group = await Group.create({
      id: uuidv4(),
      name,
      inviteCode,
      createdBy: userId,
      createdAt: Date.now(),
    });

    // Add creator as first member
    await Member.create({
      groupId: group.id,
      userId: userId,
      position: 0,
      active: true,
    });

    return group;
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
