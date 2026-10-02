# ChiaBill — chia tiền nhóm bằng VietQR

Học phần Lập trình ứng dụng di động (ET4710) · Nhóm 03 · Java native Android

Một người trả hộ cả nhóm, nhập hoá đơn, chọn ai ăn gì. Mỗi người nhận phần của mình kèm **mã VietQR của người trả đã điền sẵn số tiền và nội dung**, quét bằng app ngân hàng là chuyển được. Người trả thấy ai đã chuyển, xác nhận đã nhận, hoặc nhắc.

Bản này là **bản demo offline**: mọi tài khoản demo nằm chung một CSDL trên máy, nên các tài khoản thật sự tương tác với nhau (Minh tạo hoá đơn thì Đạt thấy khoản nợ; Đạt bấm "Tôi đã chuyển" thì Minh nhận thông báo). Thiết kế đầy đủ: `docs/project-brief.md`.

## Getting started (app + backend)

The app now talks to a Node.js backend (login, groups, bills, payments). **The backend must be running before you open the app.** The offline demo walkthrough further down describes the older local-only mode.

### Prerequisites

| Tool | Version | Used for |
|---|---|---|
| Node.js + npm | 18+ | backend |
| JDK | 17 | Gradle / Android build |
| Android SDK | platform 35 (Android Studio installs it) | app build |
| Android device or emulator | Android 8.0+ (API 26) | running the app |

### 1. Set up and run the backend

```bash
cd backend
npm install
cp config/config.json.sample config/config.json   # then edit it, see below
npx sequelize-cli db:migrate                      # creates backend/database.sqlite
npm run dev                                       # or: npm start
```

- In `config/config.json`, set `app.jwtSecret` to any long random string. `app.port` is the port the server listens on (3000 in the sample). The server binds to `0.0.0.0`, so other devices on your network can reach it.
- Only `config/config.json` is read. `.env.example` is not used by the code.
- `config.json` and `database.sqlite` are gitignored, so each developer has their own copy and data.
- The database starts **empty**: there is no seed data. You create accounts from the app (step 4).
- Do not use `sequelize.sync()`. The schema changes only through migrations. After pulling new code, run `npx sequelize-cli db:migrate` again.
- Reset your data by stopping the server, deleting `backend/database.sqlite`, and re-running the migrations.

Check it works: `curl -i http://localhost:3000/api/users/find?phone=0900000000` should answer with HTTP 200 and a body of `null`.

### 2. Point the app at the backend

The app reads its server address from `BACKEND_URL` in the root `gradle.properties`. It must end with `/` and keep the quotes:

```
BACKEND_URL="http://<address>:<port>/"
```

| You run the app on | Use `<address>` |
|---|---|
| Android emulator | `10.0.2.2` (the emulator's name for your computer) |
| Real phone | your computer's LAN IP, e.g. `192.168.1.20` (`ipconfig getifaddr en0` on macOS, `ipconfig` on Windows). Phone and computer must be on the same Wi-Fi, and the firewall must allow the port |

`<port>` is `app.port` from `config.json`.

Android blocks plain `http://` unless the host is allowed. Add the same address to `app/src/main/res/xml/network_security_config.xml`:

```xml
<domain includeSubdomains="true">192.168.1.20</domain>
```

`10.0.2.2` and `localhost` are already listed. Do not commit your personal IP; keep that change local or revert it before committing.

### 3. Build and install the app

Create `local.properties` in the project root if Android Studio has not already (`sdk.dir=/path/to/Android/sdk`), then:

```bash
./gradlew :domain:test                # 52 domain tests, no device needed
./gradlew :app:installDebug           # builds and installs on the connected device/emulator
```

On Windows use `gradlew.bat`, or double-click `build-apk.bat`. Or open the folder in Android Studio and press **Run** on the `app` configuration. Changing `BACKEND_URL` needs a rebuild.

If installing fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the app was installed from another machine with a different debug key. Uninstall it first: `adb uninstall vn.nhom03.chiabill`.

### 4. First run: create two accounts

1. In the app tap register, enter a name, a phone number such as `0901 111 111`, and a password.
2. Create a second account the same way, either on another device or after **Tôi → Đổi tài khoản**. Each login gets its own token, so switch accounts by logging in again.
3. As account A: **Tạo nhóm**, type account B's phone number, tap **Thêm**, then **Lưu**.
4. Add a bill, then as account B mark it paid, and as account A confirm it.

### Troubleshooting

| Symptom | Likely cause |
|---|---|
| Network error, timeout, or "Cleartext HTTP traffic not permitted" | Wrong `BACKEND_URL`, address missing from `network_security_config.xml`, backend not running, different Wi-Fi, or firewall |
| 404 on every call | `BACKEND_URL` points at the wrong host or port |
| 401 | Token missing or expired: log out and log in again |
| Backend crashes at start with `no such table` | Migrations not applied: `npx sequelize-cli db:migrate` |
| Works on emulator but not on phone | `10.0.2.2` only works on the emulator; use your LAN IP |

## Build và chạy

Yêu cầu: Android Studio (JDK 17, Android SDK 35), máy hoặc emulator Android 8.0+.

1. Android Studio → **Open** → chọn thư mục `ChiaBill`. Đợi Gradle sync (lần đầu tải AGP 8.7.3, Gradle 8.9 và thư viện).
2. Chọn cấu hình `app` → **Run**.

Hoặc nhấp đúp **`build-apk.bat`**: tự tạo `local.properties`, chạy test, build APK, và xử lý được đường dẫn có chữ tiếng Việt (gán tạm thư mục thành ổ ảo bằng `subst`, vì Java trên Windows đọc sai tham số có dấu).

Hoặc dòng lệnh (Windows, thư mục không dấu):

```
gradlew.bat :domain:test              # 52 test lõi, không cần thiết bị
gradlew.bat :app:assembleDebug        # APK ở app/build/outputs/apk/debug/
gradlew.bat :app:connectedDebugAndroidTest   # test Room, cần máy/emulator đang cắm
```

## Kịch bản demo (một máy)

Dữ liệu mẫu: 4 tài khoản có số điện thoại, 1 khách (**Hùng**, chưa cài app), nhóm "Đà Lạt cuối tuần" (4 hoá đơn, mã mời `DAL-AT7`) và nhóm "Ăn trưa phòng lab" (2 hoá đơn, mã mời `LAB-234`). Sáu hoá đơn mẫu dùng đủ 4 cách chia: đều, theo món, theo %, số tiền tự nhập. Số điện thoại và STK trong dữ liệu mẫu đều là giả.

| Tài khoản | Số điện thoại | Ở nhóm |
|---|---|---|
| Đạt | 0901 111 111 | Đà Lạt, Ăn trưa |
| Minh | 0902 222 222 | Đà Lạt, Ăn trưa |
| Lan | 0903 333 333 | Đà Lạt, Ăn trưa |
| Tuấn | 0904 444 444 | chưa ở nhóm nào (để demo tìm và thêm, hoặc vào bằng mã mời) |

**Tìm nhau bằng số điện thoại.** Tạo tài khoản cần tên + số điện thoại (chuẩn hoá: `0904 444 444`, `+84904444444`, `84904444444` là cùng một số; không cho trùng). Khi tạo nhóm hoặc ở màn chi tiết nhóm, gõ đúng số của người kia để tìm rồi bấm Thêm. App chỉ tìm khớp chính xác cả số, không liệt kê hay gợi ý người dùng, để không ai dò được danh bạ. Người được thêm nhận thông báo.

1. Mở app, chọn **Đạt**. Tab Nhóm → Đà Lạt cuối tuần → **Lẩu gà lá é**: 1.140.000đ + VAT 8% chia 4 người. Ba người nợ mỗi người 308.000đ, Minh (người trả) gánh 307.200đ, tổng vẫn khớp 1.231.200đ.
2. Bấm dòng của Đạt → màn khoản nợ có **VietQR của Minh** với đúng 308.000đ và nội dung `CB BLAU DAT`. Bấm **Tôi đã chuyển**.
3. Thông báo "Gửi tới Minh" hiện ra. Bấm vào thông báo: app **tự đổi sang tài khoản Minh** và mở đúng khoản nợ. Bấm **Đã nhận tiền**. Đạt nhận thông báo xác nhận.
4. Minh vào nhóm → **Thêm hoá đơn** → **Theo món**: nhập món, gán người dùng, bật VAT/phí phục vụ, đổi làm tròn. Bảng xem trước tính lại ngay; món chưa gán thì không lưu được.
5. Nhóm → **Tất toán nhóm**: các khoản chưa trả được gộp thành ít lần chuyển nhất.
6. Tab Tôi → **Khai QR nhận tiền**: chọn ảnh chụp màn hình QR từ app ngân hàng, quét camera, dán chuỗi, hoặc nhập tay. Thử **Mẫu hỏng CRC** để xem lỗi `BAD_CRC`.
7. Thử thật với app ngân hàng: khai STK thật của bạn cho một tài khoản, đổi sang tài khoản đang nợ tài khoản đó, mở khoản nợ, **Lưu ảnh QR** rồi mở app ngân hàng → Quét QR → chọn ảnh. Chỉ cần tới màn xác nhận, không cần chuyển.
8. Khách (Hùng): đăng nhập bằng Minh hoặc Lan, mở khoản nợ của Hùng → **Gửi QR qua…** để gửi ảnh QR + tin nhắn qua Zalo/Messenger.

9. Tìm và thêm: đăng nhập Đạt → nhóm Đà Lạt → **Thêm thành viên bằng số điện thoại** → gõ `0904 444 444` → Thêm Tuấn. Đổi sang Tuấn: hộp thư có "Đạt đã thêm bạn vào nhóm", tab Nhóm thấy nhóm Đà Lạt.

10. Vào nhóm bằng mã mời: đăng nhập Tuấn → tab Nhóm → **Vào nhóm bằng mã mời** → gõ `lab234` (gõ thường, có hay không gạch nối đều được). Các thành viên cũ nhận thông báo.
11. Bốn cách chia: thêm hoá đơn, chọn **Số tiền** (mỗi người tự nhập, làm tròn tự về 1đ) hoặc **Theo %** (chia đều sẵn, sửa lại; tổng khác 100% thì không lưu được). Xem mẫu: hoá đơn "Homestay 2 đêm" (30/30/20/20%) và "Trà sữa chiều".
12. Danh mục, ngày, lọc: khi thêm hoá đơn chọn danh mục (Ăn uống, Đi lại, Lưu trú…) và ngày chi. Trong nhóm, các chip **Danh mục / Người / Thời gian** lọc lịch sử, dòng dưới cho biết đang hiện bao nhiêu hoá đơn và tổng tiền.
13. Quản lý thành viên: người tạo nhóm bấm vào tên một thành viên để xoá; ai cũng có **Rời nhóm** ở cuối màn nhóm. Chỉ làm được khi người đó không còn khoản nào chưa xác nhận trong nhóm; hoá đơn cũ giữ nguyên phần của họ.
14. Tab Tôi → **Sửa tên**.

Tab Tôi có **Đổi tài khoản** và **Đặt lại dữ liệu demo**. Bản 0.3.0 nâng CSDL lên phiên bản 3, nên lần đầu mở sau khi cài đè, dữ liệu cũ bị xoá và dữ liệu demo được nạp lại.

## Demo trên nhiều máy thật thì cần gì

Bản offline không cần connector hay dịch vụ ngoài nào. Muốn hai điện thoại thấy dữ liệu của nhau thì cần backend, và nó **không phải connector của Claude** mà là một dự án Firebase của nhóm:

1. Tạo project trên Firebase Console, bật **Authentication** (Email/Password, Google) và **Cloud Firestore**.
2. Thêm app Android `vn.nhom03.chiabill`, tải `google-services.json` vào thư mục `app/` (không commit file này lên repo công khai nếu có cấu hình nhạy cảm; rules Firestore mới là lớp bảo vệ thật).
3. Hiện thực `FirebaseLedgerRepository implements LedgerRepository` và đổi một dòng trong `ChiaBillApp`. UI không phải sửa, vì mọi màn chỉ đọc `LedgerRepository.snapshot()`.
4. Viết Firestore Security Rules theo bảng threat model ở mục 13.2 của `docs/project-brief.md` (chỉ thành viên nhóm đọc được nhóm; chỉ chủ tài khoản sửa QR của mình; chỉ người trả được xác nhận).

## Kiến trúc

```
:domain  (java-library thuần, không phụ thuộc Android)
  split/   Allocator (phần dư lớn nhất) · SplitEngine (chia đều / theo món / số tiền / theo %, VAT, phí, làm tròn)
  qr/      VietQrCodec (TLV EMVCo + CRC-16/CCITT) · TransferContent · BankDirectory
  ledger/  Ledger (khoản nợ, số dư) · DebtSimplifier (tối giản nợ) · PaymentStateMachine
  user/    PhoneNumber (chuẩn hoá SĐT) · InviteCode (mã mời 6 ký tự)
:app
  data/db     Room: users, bill_groups, members, bills, bill_items, debt_status, inbox
  data/repo   LedgerRepository (interface) · LocalLedgerRepository · AppSnapshot · DemoSeeder
  ui/         Login, Main (3 tab), nhóm, hoá đơn, khoản nợ + QR, tất toán, khai QR
  util/       QR ảnh (ZXing), lưu/chia sẻ ảnh, thông báo, session
```

- Tiền luôn là `long` VND; mọi phép chia dùng số nguyên.
- Phần từng người **không lưu** mà tính lại bằng `SplitEngine` (tất định). Trạng thái khoản nợ lưu theo khoá `billId:debtorId`, nên ghi lặp không sinh bản trùng.
- QR sinh **ngay trên máy**, không gọi dịch vụ ngoài, không gửi số tài khoản đi đâu.
- Chỉ người tạo hoặc người trả sửa/xoá được hoá đơn; mỗi bước của máy trạng thái thanh toán chỉ một bên làm được (kiểm ở repository, không tin UI).

## Kiểm thử

- `domain/src/test`: 52 test JUnit 5, gồm 6 bộ property test có seed cố định: 10.000 hoá đơn ngẫu nhiên (đủ 4 cách chia) không lệch một đồng và không có phần âm; 5.000 mã mời sinh ra luôn đọc lại được; 5.000 lần encode→parse VietQR ra đúng dữ liệu; 5.000 bộ số dư tối giản về 0 với tối đa n−1 giao dịch; 20.000 lần phân bổ khớp tổng; 5.000 số điện thoại chuẩn hoá luỹ đẳng.
- `app/src/androidTest`: Room in-memory + dữ liệu demo + SplitEngine.
- Thủ công trên máy thật: quét QR sinh ra bằng app ngân hàng; thông báo; lưu ảnh; chia sẻ sang Zalo.

## Giới hạn đã biết

- Dữ liệu chỉ nằm trên một máy; chưa có đăng nhập thật và đồng bộ nhiều máy. Mã mời chỉ dùng được giữa các tài khoản trên cùng máy; chưa có link mời.
- Ảnh đại diện là chữ cái đầu trên màu cố định, chưa tải ảnh lên được.
- Người bị xoá vẫn vào lại được nếu biết mã mời (chưa có nút đổi mã).
- App không xác minh được giao dịch ngân hàng: "đã thanh toán" dựa vào xác nhận của người trả.
- QR của ví không theo chuẩn NAPAS VietQR không đọc được; khi đó nhập tay.
- Tối giản nợ dùng thuật toán tham lam, không hứa số lần chuyển ít nhất tuyệt đối.
- Bảng BIN ngân hàng đóng gói sẵn 12 ngân hàng phổ biến.

## Khai báo dùng AI

Mã nguồn và tài liệu được soạn với sự hỗ trợ của Claude (Anthropic). Nhóm chịu trách nhiệm kiểm tra, build, kiểm thử trên thiết bị và bảo vệ nội dung.
