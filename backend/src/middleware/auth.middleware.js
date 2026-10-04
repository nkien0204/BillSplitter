const jwt = require("jsonwebtoken");
const config = require("../../config/config.json");
const AuthService = require("../services/auth.service");

const invalidToken = (res) =>
  res.status(401).json({
    error: {
      code: "INVALID_TOKEN",
      message: "Invalid or expired token",
    },
  });

const authMiddleware = async (req, res, next) => {
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
    if (
      !decoded.sid ||
      !(await AuthService.isSessionActive(decoded.sid, decoded.userId))
    ) {
      return invalidToken(res);
    }
    // The token carries `userId` (a number: Users.id is an integer column);
    // controllers read `req.user.id` and compare it with string id columns
    // such as Bills.payerId, so expose it as a string.
    req.user = { ...decoded, id: String(decoded.userId), sessionId: decoded.sid };
    next();
  } catch (err) {
    return invalidToken(res);
  }
};

module.exports = authMiddleware;
