const express = require("express");
const router = express.Router();
const userController = require("../controllers/user.controller");
const authMiddleware = require("../middleware/auth.middleware");

router.get("/find", userController.findByPhone); // Public or auth? Probably auth for security
router.use(authMiddleware);
router.put("/rename", userController.rename);
router.put("/payment-target", userController.setPaymentTarget);
router.delete("/payment-target", userController.clearPaymentTarget);

module.exports = router;
