const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const User = require("../models/user.model");
const config = require("../../config/config.json");
const Logger = require("../util/logger");

const JWT_SECRET = config.app.jwtSecret;

class AuthService {
  async register(userData) {
    const { name, phone, password } = userData;

    const existingUser = await User.findOne({ where: { phone } });
    if (existingUser) {
      Logger.warn("Registration attempt with existing phone", { phone });
      throw {
        code: "USER_ALREADY_EXISTS",
        message: "Số điện thoại này đã được đăng ký.",
      };
    }

    const passwordHash = await bcrypt.hash(password, 10);
    const user = await User.create({
      name,
      phone,
      password_hash: passwordHash,
    });

    // Generate a token so the user is automatically logged in after registration
    const token = jwt.sign({ userId: user.id, phone: user.phone }, JWT_SECRET, {
      expiresIn: "24h",
    });

    Logger.userEvent(user.id, "REGISTER", { phone, name });
    return {
      token,
      user: { id: user.id, name: user.name, phone: user.phone },
    };
  }

  async login(credentials) {
    const { phone, password } = credentials;

    const user = await User.findOne({ where: { phone } });
    if (!user) {
      Logger.warn("Login attempt with non-existent phone", { phone });
      throw {
        code: "INVALID_CREDENTIALS",
        message: "Invalid phone number or password",
      };
    }

    const isMatch = await bcrypt.compare(password, user.password_hash);
    if (!isMatch) {
      Logger.warn("Login attempt with wrong password", {
        userId: user.id,
        phone,
      });
      throw {
        code: "INVALID_CREDENTIALS",
        message: "Số điện thoại hoặc mật khẩu không chính xác.",
      };
    }

    const token = jwt.sign({ userId: user.id, phone: user.phone }, JWT_SECRET, {
      expiresIn: "24h",
    });

    Logger.userEvent(user.id, "LOGIN", { phone });
    return {
      token,
      user: { id: user.id, name: user.name, phone: user.phone },
    };
  }
}

module.exports = new AuthService();
