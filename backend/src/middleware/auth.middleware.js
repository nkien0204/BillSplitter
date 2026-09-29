const jwt = require("jsonwebtoken");
const config = require("../../config/config.json");

const authMiddleware = (req, res, next) => {
  const authHeader = req.headers["authorization"];

  if (!authHeader) {
    return res.status(401).json({
      error: {
        code: "UNAUTHORIZED",
        message: "No token provided",
      },
    });
  }

  const token = authHeader.split(" ")[1]; // Bearer <token>
  if (!token) {
    return res.status(401).json({
      error: {
        code: "UNAUTHORIZED",
        message: "Token not provided",
      },
    });
  }

  try {
    const decoded = jwt.verify(token, config.app.jwtSecret);
    req.user = decoded; // Attach user info (id, etc.) to request
    next();
  } catch (err) {
    return res.status(401).json({
      error: {
        code: "INVALID_TOKEN",
        message: "Invalid or expired token",
      },
    });
  }
};

module.exports = authMiddleware;
