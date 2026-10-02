const billService = require("../services/bill.service");

class BillController {
  async create(req, res) {
    try {
      const userId = req.user.id;
      const bill = await billService.createBill(userId, req.body);
      res.status(201).json(bill);
    } catch (error) {
      res.status(400).json({ error: error.message });
    }
  }

  async update(req, res) {
    try {
      const userId = req.user.id;
      const { id } = req.params;
      const bill = await billService.updateBill(userId, id, req.body);
      res.status(200).json(bill);
    } catch (error) {
      res.status(400).json({ error: error.message });
    }
  }

  async delete(req, res) {
    try {
      const userId = req.user.id;
      const { id } = req.params;
      await billService.deleteBill(userId, id);
      res.status(200).json({ message: "Bill deleted successfully" });
    } catch (error) {
      res.status(400).json({ error: error.message });
    }
  }

  async listByGroup(req, res) {
    try {
      const { groupId } = req.params;
      const bills = await billService.getBillsByGroup(groupId);
      res.status(200).json(bills);
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  }

  async getDetails(req, res) {
    try {
      const { id } = req.params;
      const bill = await billService.getBillDetails(id);
      res.status(200).json(bill);
    } catch (error) {
      res.status(404).json({ error: error.message });
    }
  }
}

module.exports = new BillController();
