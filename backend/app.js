const express = require("express");
const cors = require("cors");
const morgan = require("morgan");
const config = require("./config/config.json");
const authRoutes = require("./src/routes/auth.routes");
const groupRoutes = require("./src/routes/group.routes");
const billRoutes = require("./src/routes/bill.routes");
const paymentRoutes = require("./src/routes/payment.routes");
const userRoutes = require("./src/routes/user.routes");
const inboxRoutes = require("./src/routes/inbox.routes");

const app = express();

app.use(cors());
app.use(morgan("dev")); // Log all incoming HTTP requests
app.use(express.json());

// Routes
app.use("/api/auth", authRoutes);
app.use("/api/groups", groupRoutes);
app.use("/api/bills", billRoutes);
app.use("/api/payments", paymentRoutes);
app.use("/api/users", userRoutes);
app.use("/api/inbox", inboxRoutes);

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
