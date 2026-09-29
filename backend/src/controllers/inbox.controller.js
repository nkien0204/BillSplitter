const inboxService = require("../services/inbox.service");

class InboxController {
  async send(req, res) {
    try {
      const { recipientId, fromId, message, billId, debtKey } = req.body;
      await inboxService.sendNotification(recipientId, fromId, message, billId, debtKey);
      res.status(201).json({ message: "Notification sent" });
    } catch (error) {
      res.status(400).json({ error: error.message });
    }
  }

  async list(req, res) {
    try {
      const userId = req.user.id;
      const notifications = await inboxService.getNotifications(userId);
      res.status(200).json(notifications);
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  }
}

module.exports = new InboxController();
