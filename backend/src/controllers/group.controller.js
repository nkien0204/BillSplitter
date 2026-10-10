const groupService = require("../services/group.service");

class GroupController {
  async create(req, res) {
    try {
      const userId = req.user.id;
      const group = await groupService.createGroup(userId, req.body);
      res.status(201).json(group);
    } catch (error) {
      res.status(400).json({ error: error.message });
    }
  }

  async join(req, res) {
    try {
      const userId = req.user.id;
      const { inviteCode } = req.body;
      const group = await groupService.joinGroup(userId, inviteCode);
      res.status(200).json(group);
    } catch (error) {
      res.status(400).json({ error: error.message });
    }
  }

  async list(req, res) {
    try {
      const userId = req.user.id;
      const groups = await groupService.getUserGroups(userId);
      res.status(200).json(groups);
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  }

  async getMembers(req, res) {
    try {
      const { groupId } = req.params;
      const members = await groupService.getGroupMembers(groupId);
      res.status(200).json(members);
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  }

  async removeMember(req, res) {
    try {
      const { groupId, userId } = req.params;
      const requestUserId = req.user.id;
      await groupService.removeMember(groupId, userId, requestUserId);
      res.status(200).json({ message: "Member removed successfully" });
    } catch (error) {
      res.status(400).json({ error: error.message });
    }
  }

  async addMember(req, res) {
    try {
      const { groupId, userId } = req.body;
      const actorId = req.user.id;
      await groupService.addMember(groupId, userId, actorId);
      res.status(200).json({ message: "Member added successfully" });
    } catch (error) {
      res.status(400).json({ error: error.message });
    }
  }

  async delete(req, res) {
    try {
      const { groupId } = req.params;
      await groupService.deleteGroup(groupId, req.user.id);
      res.status(200).json({ message: "Group deleted successfully" });
    } catch (error) {
      res.status(error.status || 400).json({ error: error.message });
    }
  }

  async leave(req, res) {
    try {
      const { groupId } = req.params;
      const userId = req.user.id;
      await groupService.leaveGroup(groupId, userId);
      res.status(200).json({ message: "Left group successfully" });
    } catch (error) {
      res.status(400).json({ error: error.message });
    }
  }
}

module.exports = new GroupController();
