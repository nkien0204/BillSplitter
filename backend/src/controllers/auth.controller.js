const AuthService = require('../services/auth.service');

class AuthController {
  async register(req, res) {
    try {
      const result = await AuthService.register(req.body);
      res.status(201).json(result);
    } catch (error) {
      res.status(400).json({
        error: {
          code: error.code || 'BAD_REQUEST',
          message: error.message || 'Registration failed',
        },
      });
    }
  }

  async login(req, res) {
    try {
      const result = await AuthService.login(req.body);
      res.status(200).json(result);
    } catch (error) {
      res.status(401).json({
        error: {
          code: error.code || 'UNAUTHORIZED',
          message: error.message || 'Login failed',
        },
      });
    }
  }

  async logout(req, res) {
    try {
      await AuthService.revokeSession(req.user.sessionId);
      res.status(200).json({ message: 'Logged out successfully.' });
    } catch (error) {
      res.status(500).json({
        error: { code: 'INTERNAL_SERVER_ERROR', message: 'Logout failed' },
      });
    }
  }

  // Lets the client check that its stored token is still valid (auto-login).
  async me(req, res) {
    try {
      res.status(200).json({ user: await AuthService.me(req.user.userId) });
    } catch (error) {
      res.status(401).json({
        error: {
          code: error.code || 'UNAUTHORIZED',
          message: error.message || 'Unauthorized',
        },
      });
    }
  }
}

module.exports = new AuthController();
