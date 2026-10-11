const express = require("express");
const multer = require("multer");
const router = express.Router();
const authMiddleware = require("../middleware/auth.middleware");
const uploadService = require("../services/upload.service");

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: uploadService.maxBytes },
});

// POST /api/uploads?kind=bill|proof, multipart field "image" -> { url }
router.post("/", authMiddleware, (req, res) => {
  upload.single("image")(req, res, async (err) => {
    if (err) {
      const tooBig = err.code === "LIMIT_FILE_SIZE";
      return res.status(tooBig ? 413 : 400).json({ error: tooBig ? "Image is larger than 5 MB" : err.message });
    }
    try {
      const baseUrl = `${req.protocol}://${req.get("host")}`;
      const url = await uploadService.save(req.file, req.query.kind, req.user.id, baseUrl);
      res.status(201).json({ url });
    } catch (e) {
      res.status(e.status || 500).json({ error: e.message });
    }
  });
});

module.exports = router;
