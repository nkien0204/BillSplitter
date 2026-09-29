const express = require("express");
const router = express.Router();
const inboxController = require("../controllers/inbox.controller");
const authMiddleware = require("../middleware/auth.middleware");

router.use(authMiddleware);

router.post("/send", inboxController.send);
router.get("/", inboxController.list);

module.exports = router;
