# Đưa ChiaBill lên mạng (miễn phí): Supabase + Render

Sau các bước này, mọi điện thoại có Internet đều dùng chung một máy chủ: đăng nhập là thấy nhóm,
hoá đơn và khoản nợ của nhau, không cần máy tính nào bật.

| Phần | Dịch vụ (gói miễn phí) | Giữ gì |
|---|---|---|
| Cơ sở dữ liệu | Supabase Postgres | tài khoản, nhóm, hoá đơn, trạng thái nợ |
| Ảnh | Supabase Storage | ảnh hoá đơn, ảnh chuyển khoản |
| Máy chủ API | Render Web Service | code trong `backend/` |

Lưu ý của gói miễn phí:
- Render cho máy chủ **ngủ sau ~15 phút** không ai dùng; lần mở app đầu tiên sau đó chờ khoảng 30–60 giây. App đã tự "đánh thức" máy chủ khi mở và chờ đủ lâu.
- Supabase **tạm dừng dự án sau 7 ngày** không có truy cập. Vào dashboard bấm *Restore* là chạy lại, dữ liệu còn nguyên.

## 1. Supabase: tạo cơ sở dữ liệu và chỗ chứa ảnh

1. Vào https://supabase.com → *Start your project* → đăng nhập bằng GitHub.
2. *New project*: đặt tên `chiabill`, đặt **Database password** (ghi lại), Region chọn **Southeast Asia (Singapore)**.
3. Lấy chuỗi kết nối: nút **Connect** ở đầu trang → mục *Connection string* → chọn **Session pooler** → copy chuỗi dạng
   `postgresql://postgres.xxxx:[YOUR-PASSWORD]@aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres`
   rồi thay `[YOUR-PASSWORD]` bằng mật khẩu ở bước 2. Đây là `DATABASE_URL`.
   (Dùng *pooler*, không dùng *Direct connection*: Render chỉ đi được IPv4.)
4. Tạo chỗ chứa ảnh: menu **Storage** → *New bucket* → tên `chiabill`, bật **Public bucket** → *Create*.
5. Lấy khoá: **Project Settings → API Keys**. Copy *Project URL* (dạng `https://xxxx.supabase.co`) → `SUPABASE_URL`.
   Ở thẻ **Legacy API keys**, copy khoá **service_role** (bắt đầu bằng `eyJ...`) → `SUPABASE_SERVICE_KEY`.
   Khoá *secret* mới (`sb_secret_...`) cũng dùng được. **Không** đưa khoá này vào app hay lên GitHub.

Không cần tạo bảng bằng tay: máy chủ tự chạy migration mỗi lần khởi động.

## 2. Render: chạy máy chủ

1. Vào https://render.com → đăng nhập bằng GitHub. Render cần quyền đọc repo: dùng tài khoản có quyền với repo này, hoặc **fork** repo về tài khoản của bạn rồi chọn bản fork.
2. *New* → **Blueprint** → chọn repo → Render đọc file `render.yaml` ở thư mục gốc và đề xuất dịch vụ `chiabill-api` (gói Free).
3. Điền 3 biến Render hỏi: `DATABASE_URL`, `SUPABASE_URL`, `SUPABASE_SERVICE_KEY` (từ phần 1). `JWT_SECRET` Render tự sinh.
4. *Apply*. Đợi build xong (2–4 phút). Mở `https://<tên-dịch-vụ>.onrender.com/health`, thấy `{"ok":true,...}` là xong.

Không dùng Blueprint? *New → Web Service* → chọn repo, rồi đặt:
Root Directory `backend` · Build `npm ci --omit=dev --omit=optional` · Start `npm run start:prod` · Instance *Free* ·
Environment: `NODE_ENV=production`, `JWT_SECRET=<chuỗi ngẫu nhiên dài>`, cùng 3 biến ở trên.

## 3. Trỏ app vào máy chủ và build APK

1. Nếu URL Render **không** phải `https://chiabill-api.onrender.com/` (tên đã có người dùng), sửa dòng `BACKEND_URL` trong `gradle.properties` ở thư mục gốc, giữ dấu `/` cuối và dấu ngoặc kép.
2. Build: chạy `build-apk.bat` (Windows) hoặc `./gradlew :app:assembleDebug`. Khi repo có GitHub Actions, mỗi lần push cũng tự build và để file APK trong mục *Artifacts* của lần chạy.
3. Cài APK lên các máy. Mỗi người đăng ký bằng số điện thoại của mình, rồi tạo nhóm và thêm nhau bằng số điện thoại hoặc mã mời.

## 4. Kiểm tra nhanh với 3 máy

1. Máy A tạo nhóm, thêm B và C bằng số điện thoại.
2. B và C kéo xuống ở tab Nhóm (hoặc chờ ~20 giây): nhóm hiện ra.
3. A thêm hoá đơn (thử nút *Chụp hoá đơn*). B và C nhận thông báo và thấy khoản nợ.
4. B bấm *Tôi đã chuyển*, gửi kèm ảnh chụp màn hình. A thấy ảnh và bấm *Đã nhận tiền*.

## Chạy trên máy cá nhân (không cần Supabase)

Không đặt `DATABASE_URL` thì máy chủ dùng SQLite như trước (`config/config.json`), ảnh lưu ở `backend/uploads/`.
Chạy test: `npm test` (SQLite) hoặc `DATABASE_URL=postgres://... DATABASE_SSL=false npm test` (Postgres).
Đo tải: `npm run test:perf`.
