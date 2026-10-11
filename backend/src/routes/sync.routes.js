const express = require("express");
const router = express.Router();
const syncController = require("../controllers/sync.controller");
const authMiddleware = require("../middleware/auth.middleware");

router.get("/", authMiddleware, syncController.get);

module.exports = router;
