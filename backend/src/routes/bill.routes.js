const express = require("express");
const router = express.Router();
const billController = require("../controllers/bill.controller");
const authMiddleware = require("../middleware/auth.middleware");

router.use(authMiddleware);

router.post("/", billController.create);
router.put("/:id", billController.update);
router.delete("/:id", billController.delete);
router.get("/:id", billController.getDetails);

// Group-specific bills
router.get("/group/:groupId", billController.listByGroup);

module.exports = router;
