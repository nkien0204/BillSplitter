const Inbox = require("../models/inbox.model");

class InboxService {
  async sendNotification(recipientId, fromId, message, billId, debtKey) {
    return Inbox.create({
      recipientId,
      message: `${fromId}: ${message}`,
      billId,
      debtKey,
      createdAt: Date.now(),
    });
  }

  async getNotifications(userId) {
    return Inbox.findAll({
      where: { recipientId: userId },
      order: [["createdAt", "DESC"]],
    });
  }
}

module.exports = new InboxService();
