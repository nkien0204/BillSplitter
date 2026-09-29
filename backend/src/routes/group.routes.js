const express = require("express");
const router = express.Router();
const groupController = require("../controllers/group.controller");
const authMiddleware = require("../middleware/auth.middleware");

router.use(authMiddleware);

router.post("/", groupController.create);
router.post("/join", groupController.join);
router.post("/add-member", groupController.addMember);
router.get("/", groupController.list);
router.get("/:groupId/members", groupController.getMembers);
router.delete("/members/:groupId/:userId", groupController.removeMember);
router.delete("/:groupId/leave", groupController.leave);

module.exports = router;
