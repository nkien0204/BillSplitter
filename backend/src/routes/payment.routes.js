const express = require("express");
const router = express.Router();
const paymentController = require("../controllers/payment.controller");
const authMiddleware = require("../middleware/auth.middleware");

router.use(authMiddleware);

router.post("/mark-paid", paymentController.markPaid);
router.post("/confirm", paymentController.confirm);
router.post("/dispute", paymentController.dispute);
router.get("/group/:groupId/balances", paymentController.getBalances);

module.exports = router;
