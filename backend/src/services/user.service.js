const User = require("../models/user.model");

class UserService {
  async findUserByPhone(phone) {
    return User.findOne({
      where: { phone },
      attributes: { exclude: ["password_hash"] },
    });
  }

  async renameUser(userId, name) {
    const user = await User.findByPk(userId);
    if (!user) throw new Error("User not found");
    await user.update({ name });
    return user;
  }

  async updatePaymentTarget(userId, targetData) {
    const user = await User.findByPk(userId);
    if (!user) throw new Error("User not found");

    await user.update({
      bankBin: targetData.bankBin,
      accountNo: targetData.accountNo,
      accountName: targetData.accountName,
      qrUpdatedAt: Date.now(),
    });

    return user;
  }

  async clearPaymentTarget(userId) {
    const user = await User.findByPk(userId);
    if (!user) throw new Error("User not found");

    await user.update({
      bankBin: null,
      accountNo: null,
      accountName: null,
    });

    return user;
  }
}

module.exports = new UserService();
