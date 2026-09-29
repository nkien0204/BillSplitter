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
    // In a stateless JWT architecture, logout is handled by the client deleting the token.
    // We provide an endpoint for compatibility or for future blacklist implementation.
    res.status(200).json({ message: 'Logged out successfully. Please delete your token on the client.' });
  }
}

module.exports = new AuthController();
