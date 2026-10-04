const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const crypto = require("crypto");
const User = require("../models/user.model");
const Session = require("../models/session.model");
const config = require("../../config/config.json");
const Logger = require("../util/logger");

const JWT_SECRET = config.app.jwtSecret;
const TOKEN_TTL_SECONDS = 24 * 60 * 60;

class AuthService {
  // Creates a session row and a JWT bound to it via the `sid` claim.
  async issueToken(user) {
    const now = Date.now();
    const session = await Session.create({
      id: crypto.randomUUID(),
      userId: user.id,
      createdAt: now,
      expiresAt: now + TOKEN_TTL_SECONDS * 1000,
    });
    return jwt.sign(
      { userId: user.id, phone: user.phone, sid: session.id },
      JWT_SECRET,
      { expiresIn: TOKEN_TTL_SECONDS },
    );
  }

  async revokeSession(sessionId) {
    await Session.update(
      { revokedAt: Date.now() },
      { where: { id: sessionId, revokedAt: null } },
    );
  }

  async isSessionActive(sessionId, userId) {
    const session = await Session.findOne({
      where: { id: sessionId, userId },
    });
    return !!session && !session.revokedAt && session.expiresAt > Date.now();
  }

  async me(userId) {
    const user = await User.findByPk(userId);
    if (!user) {
      throw { code: "USER_NOT_FOUND", message: "User not found" };
    }
    return { id: user.id, name: user.name, phone: user.phone };
  }

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
    await User.create({
      name,
      phone,
      password_hash: passwordHash,
    });
    // The model declares id as STRING while the column is an autoincrement
    // INTEGER, so create() does not return the generated id; re-read it.
    const user = await User.findOne({ where: { phone } });

    // Generate a token so the user is automatically logged in after registration
    const token = await this.issueToken(user);

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

    const token = await this.issueToken(user);

    Logger.userEvent(user.id, "LOGIN", { phone });
    return {
      token,
      user: { id: user.id, name: user.name, phone: user.phone },
    };
  }
}

module.exports = new AuthService();
