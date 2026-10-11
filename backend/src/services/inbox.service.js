const Inbox = require("../models/inbox.model");

class InboxService {
  // The app writes the full sentence (it already names the sender), so the
  // message is stored as is and shown on the recipient's devices.
  async sendNotification(recipientId, message, billId, debtKey) {
    if (!recipientId || !message) throw new Error("recipientId and message are required");
    return Inbox.create({
      recipientId: String(recipientId),
      message: String(message).slice(0, 250),
      billId: billId || null,
      debtKey: debtKey || null,
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
