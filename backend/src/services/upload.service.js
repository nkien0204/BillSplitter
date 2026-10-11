const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const settings = require("../config/settings");

const TYPES = { "image/jpeg": "jpg", "image/png": "png", "image/webp": "webp" };
const KINDS = ["bill", "proof"];
const LOCAL_DIR = path.resolve(__dirname, "../../uploads");

class UploadService {
  get maxBytes() {
    return 5 * 1024 * 1024;
  }

  /**
   * Stores one photo and returns its public URL. With Supabase configured it
   * goes to Supabase Storage (survives restarts of a free host); otherwise to
   * backend/uploads, served by this server, which is enough on a laptop.
   */
  async save(file, kind, userId, baseUrl) {
    if (!file) throw this._error(400, "Missing image");
    const ext = TYPES[file.mimetype];
    if (!ext) throw this._error(400, "Only JPEG, PNG or WebP images are allowed");
    const folder = KINDS.includes(kind) ? kind : "bill";
    const name = `${folder}/${userId}-${Date.now()}-${crypto.randomBytes(4).toString("hex")}.${ext}`;

    if (settings.supabase.url && settings.supabase.serviceKey) {
      return this._toSupabase(name, file);
    }
    const target = path.join(LOCAL_DIR, name);
    await fs.promises.mkdir(path.dirname(target), { recursive: true });
    await fs.promises.writeFile(target, file.buffer);
    return `${baseUrl}/uploads/${name}`;
  }

  async _toSupabase(name, file) {
    const { url, serviceKey, bucket } = settings.supabase;
    const headers = { apikey: serviceKey, "Content-Type": file.mimetype, "x-upsert": "true" };
    // Legacy service_role keys are JWTs and go in Authorization too; the newer
    // "sb_secret_..." keys are not JWTs and are accepted in apikey alone.
    if (serviceKey.startsWith("eyJ")) headers.Authorization = `Bearer ${serviceKey}`;
    const res = await fetch(`${url}/storage/v1/object/${bucket}/${name}`, {
      method: "POST",
      headers,
      body: file.buffer,
    });
    if (!res.ok) {
      const text = await res.text().catch(() => "");
      throw this._error(502, `Storage upload failed (${res.status}) ${text}`.trim());
    }
    return `${url}/storage/v1/object/public/${bucket}/${name}`;
  }

  _error(status, message) {
    const err = new Error(message);
    err.status = status;
    return err;
  }
}

UploadService.LOCAL_DIR = LOCAL_DIR;
module.exports = new UploadService();
module.exports.LOCAL_DIR = LOCAL_DIR;
