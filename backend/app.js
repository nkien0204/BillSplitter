const express = require("express");
const cors = require("cors");
const morgan = require("morgan");
const authRoutes = require("./src/routes/auth.routes");
const groupRoutes = require("./src/routes/group.routes");
const billRoutes = require("./src/routes/bill.routes");
const paymentRoutes = require("./src/routes/payment.routes");
const userRoutes = require("./src/routes/user.routes");
const inboxRoutes = require("./src/routes/inbox.routes");
const syncRoutes = require("./src/routes/sync.routes");
const uploadRoutes = require("./src/routes/upload.routes");
const { LOCAL_DIR } = require("./src/services/upload.service");

const app = express();

// Render and ngrok sit in front of the server; trust them so req.protocol is
// "https" and photo URLs point at the public address.
app.set("trust proxy", true);
app.use(cors());
if (process.env.NODE_ENV !== "test") app.use(morgan("dev")); // Log all incoming HTTP requests
app.use(express.json());

// Health check: Render pings it, and the app calls it to wake a sleeping server.
app.get("/health", (req, res) => res.status(200).json({ ok: true, time: Date.now() }));

// Photos stored on this server's disk (only when Supabase Storage is not set up).
app.use("/uploads", express.static(LOCAL_DIR, { maxAge: "7d" }));

// Routes
app.use("/api/auth", authRoutes);
app.use("/api/groups", groupRoutes);
app.use("/api/bills", billRoutes);
app.use("/api/payments", paymentRoutes);
app.use("/api/users", userRoutes);
app.use("/api/inbox", inboxRoutes);
app.use("/api/sync", syncRoutes);
app.use("/api/uploads", uploadRoutes);

// Global Error Handler
app.use((err, req, res, next) => {
  console.error(err.stack);
  res.status(500).json({
    error: {
      code: "INTERNAL_SERVER_ERROR",
      message: "An unexpected error occurred on the server",
    },
  });
});

module.exports = app;
