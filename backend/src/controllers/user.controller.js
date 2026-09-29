const userService = require("../services/user.service");

class UserController {
  async findByPhone(req, res) {
    try {
      const { phone } = req.query;
      const user = await userService.findUserByPhone(phone);
      res.status(200).json(user);
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  }

  async rename(req, res) {
    try {
      const userId = req.user.id;
      const { name } = req.body;
      const user = await userService.renameUser(userId, name);
      res.status(200).json(user);
    } catch (error) {
      res.status(400).json({ error: error.message });
    }
  }

  async setPaymentTarget(req, res) {
    try {
      const userId = req.user.id;
      const user = await userService.updatePaymentTarget(userId, req.body);
      res.status(200).json(user);
    } catch (error) {
      res.status(400).json({ error: error.message });
    }
  }

  async clearPaymentTarget(req, res) {
    try {
      const userId = req.user.id;
      await userService.clearPaymentTarget(userId);
      res.status(200).json({ message: "Payment target cleared" });
    } catch (error) {
      res.status(400).json({ error: error.message });
    }
  }
}

module.exports = new UserController();
