# Changelog

## 0.4.0 — online

**Mới**
- Các máy dùng chung một máy chủ: Render (API) + Supabase (Postgres, ảnh). Hướng dẫn: `backend/DEPLOY.md`, cấu hình: `render.yaml`.
- Đồng bộ: `GET /api/sync` trả toàn bộ dữ liệu của người dùng; app thay cache Room trong một transaction. Tự đồng bộ mỗi 20 giây khi app mở, kéo xuống để làm mới, đồng bộ ngay sau mỗi thao tác. Thông báo khi có tin mới trong hộp thư.
- Chụp hoá đơn để điền tự động (ML Kit trên máy + `ReceiptParser` trong `:domain`): tổng tiền, tên quán, các món. Ảnh hoá đơn lưu kèm hoá đơn.
- "Tôi đã chuyển" kèm ảnh chụp màn hình chuyển khoản; người nhận xem được ảnh.
- Nút "Gửi qua Zalo" mở thẳng Zalo.
- Icon mới (thích ứng, có lớp đơn sắc cho Android 13+). Nguồn: `docs/icon/`.
- `POST /api/uploads`, `GET /health`, cột `Bills.imageUrl`, `DebtStatus.proofImageUrl`.

**Sửa**
- App và máy chủ dùng chung tên trạng thái `MARKED_PAID` (máy chủ trước đây lưu `DEBTOR_PAID`, app đọc thành "chưa trả"). Migration đổi dữ liệu cũ.
- Nhóm và hoá đơn của người khác không bao giờ hiện trên máy mình (chỉ đồng bộ khi mở đúng màn nhóm).
- Sửa hoá đơn đặt lại trạng thái của người bị đổi số tiền (`resetDebtorsCsv`), giữ nguyên khoản đã xác nhận.
- Người nhận xác nhận được tiền mặt từ trạng thái "chưa trả".
- Đăng ký thất bại trên Postgres (kiểu cột `id`, `created_at`).

**Bỏ**
- VAT, phí dịch vụ, làm tròn trong form hoá đơn (hoá đơn cũ vẫn tính đúng theo giá trị đã lưu).
- Bộ lọc lịch sử, ô dán payload QR và dữ liệu mẫu, mục thành viên khách, bảng payload VietQR kỹ thuật.

**Kiểm thử**
- 15 test hệ thống backend (3 tài khoản qua HTTP), chạy trên SQLite và Postgres.
- 9 test `ReceiptParser` (tổng 61 test `:domain`).
- Test tải `/api/sync`: 168 req/s, p99 33 ms với 3 kết nối (Postgres cục bộ, 200 hoá đơn).
- GitHub Actions: test backend trên Postgres, test domain, build APK debug.

## 0.3.0 — offline demo

Bản demo một máy: mọi tài khoản trong một CSDL Room.
