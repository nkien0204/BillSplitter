const paymentService = require("../services/payment.service");

class PaymentController {
  async markPaid(req, res) {
    try {
      const userId = req.user.id;
      const { billId } = req.body;
      await paymentService.markAsPaid(userId, billId);
      res.status(200).json({ message: "Payment marked as paid" });
    } catch (error) {
      res.status(400).json({ error: error.message });
    }
  }

  async confirm(req, res) {
    try {
      const userId = req.user.id;
      const { billId, debtorId } = req.body;
      await paymentService.confirmPayment(userId, billId, debtorId);
      res.status(200).json({ message: "Payment confirmed" });
    } catch (error) {
      res.status(400).json({ error: error.message });
    }
  }

  async dispute(req, res) {
    try {
      const userId = req.user.id;
      const { billId, debtorId } = req.body;
      await paymentService.disputePayment(userId, billId, debtorId);
      res.status(200).json({ message: "Payment disputed" });
    } catch (error) {
      res.status(400).json({ error: error.message });
    }
  }

  async getBalances(req, res) {
    try {
      const { groupId } = req.params;
      const data = await paymentService.getGroupBalances(groupId);
      res.status(200).json(data);
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  }
}

module.exports = new PaymentController();
