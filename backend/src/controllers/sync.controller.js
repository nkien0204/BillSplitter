const syncService = require("../services/sync.service");

class SyncController {
  async get(req, res, next) {
    try {
      res.status(200).json(await syncService.snapshotFor(req.user.id));
    } catch (error) {
      next(error);
    }
  }
}

module.exports = new SyncController();
