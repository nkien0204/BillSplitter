# Thiết kế & đánh giá khả thi — App chia tiền nhóm bằng VietQR

Học phần: **Lập trình ứng dụng di động (ET4710)** · Nhóm 3 người · Đối chiếu theo Syllabus + Chương 1 + Hướng dẫn repository nhóm
Phiên bản: v1.0 · Ngày: 27/09/2026
Đề tài đề xuất, cân nhắc thay cho: *TimeFit (trợ lý lịch trình)*
Tên sản phẩm đề xuất: **ChiaBill** · Repo: `ET4710-Nhom03-ChiaBill`

---

## 0. Kết luận nhanh

**Đề tài khả thi cho 3 người trong một học kỳ, và là đề tài phủ Chương 3 tốt nhất trong ba ý tưởng đã xét.** Chia tiền bản chất là bài toán nhiều người dùng: phải có tài khoản, dữ liệu dùng chung, đồng bộ, và xử lý khi hai người cùng thao tác. Đây đúng là chỗ TimeFit yếu (app một người dùng) và MeetBrief phải gượng thêm BaaS mới có.

Lõi kỹ thuật có ba phần logic thuần, đều unit test và property test được mà không cần thiết bị:

1. **Bộ chia tiền**: chia đều, chia theo món, theo tỉ lệ, số tiền tuỳ chỉnh; phân bổ VAT/phí phục vụ/giảm giá theo tỉ lệ; làm tròn VND sao cho tổng các phần **luôn bằng đúng** tổng hoá đơn.
2. **Codec VietQR**: đọc QR ngân hàng của thành viên (chuẩn EMVCo/NAPAS), tách ra mã ngân hàng + số tài khoản, rồi **sinh lại QR có sẵn số tiền và nội dung chuyển khoản** cho từng khoản nợ. Người nợ quét là app ngân hàng điền sẵn mọi thứ.
3. **Tối giản nợ** (P1): gộp nhiều hoá đơn trong một chuyến đi thành số lần chuyển khoản ít nhất.

Điểm (2) là thứ khiến đề tài khác các app chia tiền sẵn có: không dừng ở "A nợ B 150k" mà đưa luôn QR đúng số tiền của B.

**Ba việc phải xử lý trước khi chốt:**

1. **Đây là lần đổi đề tài thứ hai.** TimeFit đã có 79 file Java / 6.712 dòng code và solver đã verify. Đổi sang ChiaBill là bỏ phần lớn số code đó (mục 17 nói rõ cái gì giữ được). Trước khi quyết, cả nhóm nên đồng thuận, và nếu G0 đã nộp repo thì phải xin giảng viên — lần xin thứ hai khó hơn lần đầu.
2. **Lập luận "phải dùng Java native" yếu đi hẳn.** TimeFit có lý do chính đáng (`CalendarContract`, `AlarmManager`). ChiaBill chỉ cần camera, share sheet và notification, React Native làm tốt cả ba. Nếu đổi sang đề tài này thì **nên theo luôn React Native + TypeScript** như syllabus, bỏ được một việc phải xin phê duyệt.
3. **App không giữ tiền và không xác minh được giao dịch.** Tiền đi thẳng từ ngân hàng người nợ sang ngân hàng người trả; app chỉ ghi nhận. Đây là quyết định có chủ đích (không cần giấy phép trung gian thanh toán, không cần API ngân hàng), nhưng nghĩa là trạng thái "đã trả" dựa vào xác nhận của hai bên. Phải nói rõ khi bảo vệ.

---

## 1. Đối chiếu yêu cầu môn học

| Trục của môn | Yêu cầu | App đáp ứng thế nào | Mức |
|---|---|---|---|
| **CLO1 / Ch1** — phân tích vấn đề, chọn công nghệ | Problem statement, user story, phi chức năng, MVP, rủi ro | Đủ (mục 2–6) | ✅ Đủ |
| **CLO2 / Ch2** — kiến trúc, state, UI | Clean Architecture/MVVM, design system, điều hướng | Phân lớp presentation–domain–data; domain là TypeScript thuần, không import React Native (mục 7) | ✅ Mạnh |
| **CLO3 / Ch3** — dữ liệu, mạng, offline-first | API có kiểu, auth, lưu trữ cục bộ, đồng bộ, offline | Đăng nhập, nhóm dùng chung, đồng bộ realtime, tạo hoá đơn offline rồi đẩy lên, security rules theo thành viên nhóm (mục 11) | ✅ **Mạnh** |
| **CLO3 / Ch4** — tích hợp thiết bị | ≥1 tích hợp có giá trị, ma trận quyền, test máy thật | Camera/thư viện ảnh quét QR · lưu QR vào máy · share sheet sang Zalo/Messenger · notification (mục 12) | ✅ Mạnh |
| **CLO4 / Ch5** — kiểm thử, bảo mật, hiệu năng | Test pyramid, threat model, đo hiệu năng | Property test bộ chia tiền và codec VietQR; threat model có một mối đe doạ thật (tráo QR, mục 13.2) | ✅ Mạnh |
| **CLO5 / Ch6** — DevOps, phát hành | Build ký, CI, release package, tài liệu | EAS Build + GitHub Action lint/test + README + release note (mục 14) | ✅ Khả thi |
| **CLO6** — cộng tác, minh chứng cá nhân | Git workflow, issue/PR/review, portfolio | 3 mảng độc lập (mục 15) | ✅ Đủ |
| **Phi chức năng đo được** (bắt buộc ≥1) | Có chỉ số, cách đo, ngưỡng | 8 chỉ tiêu (mục 6) | ✅ Đủ |

Không trục nào ở mức "cần bù". Đây là khác biệt chính so với hai đề tài trước.

---

## 2. Bài toán, người dùng, giá trị (cổng G0)

**Problem statement.** Nhóm bạn đi ăn, đi du lịch, góp tiền mua đồ chung: thường một người quẹt thẻ hoặc chuyển khoản trả hết, rồi phải tự tính mỗi người bao nhiêu, nhắn vào nhóm chat kèm số tài khoản, và nhớ ai đã trả ai chưa. Ba chỗ hay hỏng:

- **Tính sai hoặc tính không công bằng**: người gọi món đắt chia đều với người gọi món rẻ; VAT và phí phục vụ không ai tính vào; số lẻ khi chia ba.
- **Chuyển khoản mất công và dễ nhầm**: người nợ phải gõ lại số tài khoản, chọn ngân hàng, gõ số tiền, gõ nội dung. Gõ sai số tài khoản là tiền đi mất.
- **Không ai nhớ ai đã trả**: người trả trước ngại đòi, người nợ quên. Sau chuyến đi 3 ngày với 15 hoá đơn thì không ai đối chiếu nổi.

**Người dùng mục tiêu.** Nhóm 3–10 người quen nhau (bạn học, đồng nghiệp, nhóm du lịch), ai cũng có app ngân hàng hỗ trợ quét VietQR. Vai chính là **người trả trước**: người cần app nhất vì họ là người bị nợ.

**Product vision.** Người trả chụp/nhập hoá đơn, chọn ai ăn gì; mỗi người nhận ngay phần của mình cùng một mã QR đã điền sẵn số tiền và nội dung, quét bằng app ngân hàng là xong. Người trả thấy ai đã chuyển, ai chưa, và nhắc được mà không phải tự nhắn.

**Giá trị chính (theo mẫu README).** Ứng dụng giúp *nhóm bạn đi ăn/đi chơi chung* *chia tiền đúng và trả lại người đã trả trước chỉ bằng một lần quét* bằng cách *tính phần của từng người và sinh mã VietQR có sẵn số tiền, số tài khoản và nội dung chuyển khoản*.

**Khác biệt so với app chia tiền có sẵn (Splitwise, Tricount…) và so với tự nhắn trong Zalo.**

1. Sinh **VietQR động có số tiền** cho từng khoản nợ. App nước ngoài chỉ ghi nợ, không gắn được với chuyển khoản ngân hàng Việt Nam.
2. Làm tròn theo thói quen Việt Nam (tròn nghìn), phần lẻ dồn cho người trả, tổng luôn khớp.
3. Người không cài app vẫn trả được: người trả gửi ảnh QR + tin nhắn qua share sheet.

---

## 3. User stories P0 + tiêu chí chấp nhận

**US-1 — Khai QR nhận tiền của mình.**
*Là thành viên nhóm, tôi muốn khai mã QR ngân hàng của mình một lần, để lần nào tôi trả trước thì người khác cũng chuyển lại được ngay.*

- Chọn ảnh QR từ thư viện (ảnh chụp màn hình QR trong app ngân hàng) hoặc quét trực tiếp bằng camera.
- App đọc được QR chuẩn VietQR, hiện lại **tên ngân hàng + số tài khoản (che giữa) + tên chủ tài khoản nếu có trong mã** để người dùng xác nhận.
- QR không đúng chuẩn VietQR (hỏng CRC, sai định dạng, QR của ví không theo chuẩn) thì báo rõ, cho nhập tay ngân hàng + số tài khoản, hoặc lưu ảnh gốc ở chế độ "chỉ hiển thị ảnh, không điền được số tiền".
- Chỉ chủ tài khoản sửa được QR của chính mình.

**US-2 — Tạo hoá đơn và chia.**
*Là người vừa trả tiền cho cả nhóm, tôi muốn nhập hoá đơn và chọn cách chia, để mỗi người biết đúng phần của mình.*

- Nhập: tiêu đề, tổng tiền, người trả (mặc định là mình), thành viên tham gia, ngày.
- Chọn cách chia: **chia đều**, **theo món** (mỗi món gán cho một hoặc nhiều người), **số tiền tuỳ chỉnh**.
- Thêm được VAT %, phí phục vụ %, giảm giá (số tiền hoặc %); các khoản này phân bổ theo tỉ lệ phần gốc của từng người.
- Chọn mức làm tròn: 1đ hoặc 1.000đ. Phần chênh do làm tròn dồn vào phần của người trả.
- **Tổng các phần luôn bằng đúng tổng hoá đơn.** Chia theo món mà còn món chưa gán ai thì không cho lưu, chỉ rõ món nào.
- Tạo được khi mất mạng; có mạng thì tự đồng bộ cho cả nhóm.

**US-3 — Nhận phần của mình kèm QR của người trả.**
*Là người được trả hộ, tôi muốn thấy mình nợ bao nhiêu và có QR để chuyển ngay, để khỏi phải hỏi số tài khoản.*

- Mở hoá đơn thấy: phần của mình, cách tính (món nào, bao nhiêu VAT/phí), và **QR của người trả đã điền sẵn đúng số tiền của mình và nội dung chuyển khoản**.
- Có nút lưu QR vào thư viện ảnh (để mở app ngân hàng, chọn "quét từ ảnh") và nút sao chép số tài khoản / số tiền / nội dung.
- Bấm "Tôi đã chuyển" thì khoản nợ sang trạng thái *chờ xác nhận*; người trả nhận thông báo và bấm xác nhận thì thành *đã thanh toán*.
- Người trả chưa khai QR thì vẫn hiện số tiền và báo rõ "người trả chưa khai QR".

---

## 4. Luồng sử dụng chính

```
Đăng nhập ─► khai QR nhận tiền (một lần) ─► tạo/nhận lời mời vào nhóm
                                                 │
                                                 ▼
                                   Nhóm: danh sách hoá đơn + số dư
                                                 │
                          Người trả bấm "Thêm hoá đơn"
                                                 ▼
          Nhập tổng / nhập từng món ─► chọn người ─► VAT, phí, giảm giá, làm tròn
                                                 │
                                                 ▼
                         SplitEngine: phần từng người (tổng luôn khớp)
                                                 │ lưu (offline được → outbox)
                                                 ▼
                              Đồng bộ lên server ─► thông báo cho thành viên
                                                 │
            ┌────────────────────────────────────┴───────────────────┐
            ▼                                                        ▼
   Thành viên có app                                     Thành viên không cài app
   mở khoản nợ ─► VietQR động                             người trả bấm "Gửi" ─► share sheet
   (STK người trả + số tiền + nội dung)                   ảnh QR + tin nhắn vào Zalo/Messenger
            │ lưu ảnh / quét bằng app ngân hàng
            ▼
   "Tôi đã chuyển" ─► chờ xác nhận ─► người trả xác nhận ─► đã thanh toán
                                   └► người trả báo "chưa nhận" ─► tranh chấp
```

Lõi để demo: **hoá đơn thô vào → mỗi người nhận một QR đúng số tiền → quét bằng app ngân hàng thật, thấy đúng số tài khoản và số tiền.** Đoạn cuối phải demo trên máy thật với app ngân hàng thật.

---

## 5. Phạm vi MVP

**P0 — Luồng cốt lõi (thiếu là MVP không chạy). Must-have.**

- Đăng ký/đăng nhập (email + mật khẩu hoặc Google).
- Hồ sơ: tên hiển thị, **QR nhận tiền** (quét từ ảnh/camera, parse, xác nhận, fallback nhập tay).
- Nhóm: tạo nhóm, mời bằng link/mã, rời nhóm; thêm được **thành viên khách** (chỉ có tên, chưa có tài khoản).
- Hoá đơn: CRUD; chia đều / theo món / tuỳ chỉnh; VAT, phí phục vụ, giảm giá; làm tròn 1đ hoặc 1.000đ.
- Khoản nợ theo hoá đơn: người nợ → người trả, có trạng thái `PENDING → MARKED_PAID → CONFIRMED` (và `DISPUTED`).
- **Sinh VietQR động** cho từng khoản nợ, lưu ảnh QR vào máy, sao chép thông tin chuyển khoản.
- Gửi phần nợ + QR qua share sheet (cho thành viên khách).
- Số dư của mình trong từng nhóm: đang nợ ai bao nhiêu, ai đang nợ mình.
- Offline-first: xem mọi thứ đã tải khi mất mạng; tạo/sửa hoá đơn khi mất mạng, tự đồng bộ khi có mạng.
- Notification khi có hoá đơn mới liên quan tới mình và khi khoản nợ đổi trạng thái (khi app đang chạy nền: listener realtime + local notification; push thật khi app đóng là P1).

**P1 — Tăng độ tin cậy và chiều sâu. Nên có.**

- **Tất toán nhóm với tối giản nợ**: gộp mọi hoá đơn chưa trả trong nhóm, đề xuất số lần chuyển khoản ít nhất (mục 10.3).
- Push notification khi app đóng (FCM) + nhắc nợ: người trả bấm "Nhắc", hoặc tự nhắc sau N ngày.
- Chia theo tỉ lệ/phần (vd người lớn 1 phần, trẻ em 0,5 phần).
- Lịch sử thay đổi hoá đơn (ai sửa gì, lúc nào) — cần cho tranh chấp.
- Ảnh hoá đơn đính kèm.
- GitHub Action chạy lint + unit/property test khi mở PR (gitleaks bật từ G0).

**P2 — Cải thiện trải nghiệm.**

- Đọc hoá đơn từ ảnh (OCR) để điền sẵn danh sách món. Cần spike vì chất lượng nhận dạng tiếng Việt trên hoá đơn in nhiệt không đảm bảo.
- Nhiều tiền tệ khi đi du lịch nước ngoài (tỉ giá nhập tay).
- Thống kê chi tiêu theo nhóm/tháng.
- Chế độ tối, đa ngôn ngữ, widget "đang nợ".

**Later — ghi nhận, chưa cam kết.**

- Tự xác nhận đã nhận tiền qua webhook biến động số dư (dịch vụ trung gian đọc giao dịch ngân hàng). Bỏ ở học kỳ này vì phải trao quyền đọc tài khoản ngân hàng cho bên thứ ba, rủi ro bảo mật và pháp lý lớn hơn giá trị.
- Deep link mở thẳng app ngân hàng với thông tin điền sẵn (không có chuẩn chung giữa các ngân hàng).
- Giữ tiền hộ / ví trong app. **Không làm**: đó là hoạt động trung gian thanh toán, cần giấy phép.

---

## 6. Yêu cầu phi chức năng (đo được)

| Loại | Chỉ số | Cách đo | Ngưỡng |
|---|---|---|---|
| **Đúng đắn chia tiền** | Số hoá đơn mà tổng các phần ≠ tổng hoá đơn, hoặc có phần âm | Property test: 10.000 hoá đơn sinh ngẫu nhiên (mọi chế độ chia, VAT/phí/giảm giá, 2–15 người) | **0** |
| **Đúng đắn VietQR** | QR sinh ra được app ngân hàng thật đọc đúng số tài khoản, số tiền, nội dung | Quét bằng ≥5 app ngân hàng khác nhau của thành viên nhóm, mỗi app 3 khoản | **100 %** |
| Tỉ lệ parse QR thật | QR nhận tiền chụp từ app ngân hàng của thành viên đọc được thành ngân hàng + STK | ≥15 ảnh QR thật từ ≥5 ngân hàng/ví | ≥ 95 % |
| Hiệu năng (thao tác) | Thời gian tạo một hoá đơn chia đều 4 người, từ bấm "Thêm" tới lưu | Test với ≥5 người thử | trung vị < 30 s |
| Đồng bộ | Từ lúc người trả lưu (đang online) tới lúc máy thành viên khác thấy | 20 lần, 2 máy, Wi-Fi | p95 < 3 s |
| Offline | Hoá đơn tạo khi máy bay mode được đồng bộ đủ và không trùng khi có mạng lại | 10 lần, có tắt/mở mạng giữa chừng | 10/10, 0 bản trùng |
| Tin cậy | Tỉ lệ phiên không crash | Crash log đợt test | ≥ 99 % |
| Bảo mật | Secret trong repo; truy cập được dữ liệu nhóm mình không thuộc | gitleaks + test security rules (mục 13) | 0 · 0 |

Hai chỉ tiêu đầu là hai chỉ tiêu đáng bảo vệ nhất: một cái kiểm tự động bằng máy, một cái kiểm bằng app ngân hàng thật.

---

## 7. Kiến trúc & công nghệ

Kiến trúc phân lớp theo Chương 2. Mọi logic tiền nằm ở lớp domain, là TypeScript thuần, không import `react-native` hay SDK backend.

```
Presentation  ─ Screens + hook/store (Zustand)
                Groups · GroupDetail · BillEdit · BillDetail · DebtDetail
                Profile · QrSetup · SettleUp · Invite
                4 trạng thái mỗi màn: loading / data / empty / error
      │
      │ gọi use case
      ▼
Domain        ─ Use case: CreateBill · UpdateBill · ComputeShares ·
                  BuildPaymentQr · MarkPaid · ConfirmPaid · SettleGroup
                Engine (thuần):
                  SplitEngine · Allocator (largest remainder) · Rounding
                  VietQrCodec (TLV parse/encode + CRC16) · BankDirectory
                  DebtSimplifier · PaymentStateMachine
                Model: Money (số nguyên VND) · Member · Bill · BillItem ·
                  Share · Debt · PaymentProfile
                Interface repository
      │
      │ hiện thực qua
      ▼
Data          ─ Backend SDK (Auth + DB realtime)
                Local cache (SQLite) + Outbox
                QrScanner (camera / ảnh thư viện)
                QrRenderer (sinh ảnh QR cục bộ)
                MediaStore (lưu ảnh QR) · ShareService · NotificationService
```

**Ràng buộc biên:** mọi dữ liệu từ server và mọi chuỗi đọc được từ QR là `unknown`, qua schema (zod) hoặc qua `VietQrCodec.parse` (có kiểm CRC) rồi mới thành kiểu miền.

**Tiền là số nguyên VND, không bao giờ là `number` dấu phẩy động trong phép chia.** Tỉ lệ tính bằng phân số nguyên hoặc trên đơn vị nhỏ nhất rồi phân bổ phần dư. Đây là một ADR.

**Tech stack:**

| Thành phần | React Native + TypeScript *(khuyến nghị cho đề tài này)* | Java native *(chỉ khi giảng viên duyệt)* |
|---|---|---|
| Điều hướng | React Navigation (Bottom Tab + Stack) | Navigation Component |
| State | Zustand | ViewModel + LiveData |
| Backend | **Firebase** (Auth + Firestore) hoặc **Supabase** (Auth + Postgres + RLS) — mục 11.1 | như cột trái |
| Cache cục bộ | Firestore offline persistence, hoặc expo-sqlite + outbox nếu dùng Supabase | Room |
| Quét QR | react-native-vision-camera (code scanner) + decode QR từ ảnh thư viện | ML Kit Barcode Scanning |
| Sinh ảnh QR | react-native-qrcode-svg (cục bộ, không gọi API) | ZXing |
| Lưu ảnh / chia sẻ | expo-media-library · expo-sharing | MediaStore · `Intent.ACTION_SEND` |
| Thông báo | expo-notifications (+ FCM ở P1) | NotificationManager + FCM |
| Validate | zod | validator tay |
| Test logic thuần | Jest + fast-check (property test) | JUnit 5 + jqwik |
| Build/phát hành | EAS Build → APK/AAB | Gradle ký keystore |

Khác với TimeFit, ở đây không có API Android thuần nào bắt buộc phải làm native. Mặc định theo cột trái.

---

## 8. Mô hình dữ liệu

Mô tả theo thực thể; ánh xạ sang collection (Firestore) hay bảng (Postgres) ở ADR backend.

```
User
  id                string            // uid từ Auth
  displayName       string
  paymentProfile    PaymentProfile?   // chỉ chủ tài khoản ghi được
  createdAt

PaymentProfile
  kind              VIETQR | RAW_IMAGE | MANUAL
  bankBin           string?           // 6 số, vd 970436
  accountNo         string?
  accountName       string?           // chỉ khi có trong mã hoặc người dùng tự nhập
  rawPayload        string?           // chuỗi EMVCo gốc (đã kiểm CRC)
  rawImagePath      string?           // chỉ khi kind = RAW_IMAGE
  updatedAt                           // hiển thị cảnh báo "vừa đổi QR"

Group
  id
  name
  createdBy         → User.id
  inviteToken       string            // ngẫu nhiên ≥128 bit, có hạn, thu hồi được
  inviteExpiresAt
  rounding          1 | 1000          // mặc định của nhóm
  createdAt

Member                                // 1 Group – n Member
  id
  groupId           → Group.id
  userId            → User.id?        // null = thành viên khách
  displayName       string
  role              OWNER | MEMBER
  leftAt            timestamp?        // rời nhóm, giữ lịch sử

Bill                                  // 1 Group – n Bill
  id                string            // UUID sinh ở client → ghi lặp không trùng
  groupId           → Group.id
  title
  payerMemberId     → Member.id
  createdByUserId   → User.id         // chỉ người tạo (hoặc người trả) được sửa
  date
  splitMode         EQUAL | ITEMIZED | CUSTOM | SHARES(P1)
  subtotal          int               // VND
  vatPercent        decimal?          // lưu dạng ‰ nguyên để khỏi dấu phẩy động
  servicePercent    decimal?
  discount          { kind: AMOUNT|PERCENT, value }?
  total             int               // VND, số trên hoá đơn thật
  rounding          1 | 1000
  version           int               // tăng mỗi lần sửa
  deleted           bool
  updatedAt

BillItem                              // chỉ khi ITEMIZED
  id
  billId            → Bill.id
  name
  price             int
  quantity          int
  consumerMemberIds Member.id[]       // ≥1

Share                                 // kết quả SplitEngine, 1 Bill – n Share
  billId            → Bill.id
  memberId          → Member.id
  amount            int               // tổng mọi Share của một Bill = Bill.total
  breakdown         { base, vat, service, discount, roundingAdj }

Debt                                  // 1 Share (trừ người trả) – 1 Debt
  id                = billId + ":" + debtorMemberId   // khoá tất định
  billId            → Bill.id
  debtorMemberId    → Member.id
  creditorMemberId  → Member.id       // = payer
  amount            int
  status            PENDING | MARKED_PAID | CONFIRMED | DISPUTED | VOID
  markedPaidAt / confirmedAt
  transferRef       string            // nội dung chuyển khoản gợi ý, vd "CB 7K2Q Dat"
```

Ba quyết định đáng giải trình:

1. **`Share` là dữ liệu dẫn xuất nhưng vẫn lưu.** Tính lại được từ `Bill` + `BillItem`, nhưng lưu để người nợ thấy đúng con số tại lúc tạo, kể cả khi thuật toán làm tròn đổi ở bản sau. Sửa hoá đơn thì tính lại toàn bộ `Share` và đẩy `Debt` về `PENDING` nếu số tiền đổi (khoản đã `CONFIRMED` thì báo chênh lệch, không tự huỷ).
2. **Khoá `Debt` tất định (`billId:debtorId`), `Bill.id` là UUID sinh ở client.** Outbox gửi lại bao nhiêu lần cũng không sinh bản trùng. Đây là cách đạt chỉ tiêu "0 bản trùng" ở mục 6.
3. **Lưu chuỗi payload QR, không lưu ảnh QR.** Ảnh sinh lại cục bộ từ payload. Nhẹ hơn, không cần storage, và kiểm được CRC. Ảnh gốc chỉ lưu khi QR không theo chuẩn VietQR (fallback).

---

## 9. Màn hình & điều hướng

Bottom Tab 3 mục + Stack.

**Tab 1 — Nhóm**
- `GroupListScreen` — danh sách nhóm, mỗi dòng hiện số dư của mình (đang nợ / được nợ).
- `GroupDetailScreen` — danh sách hoá đơn theo ngày, thanh số dư trên đầu, nút "Thêm hoá đơn", nút "Tất toán" (P1).
- `BillEditScreen` — **màn phức tạp nhất**: tổng tiền hoặc danh sách món, chọn người (chip avatar), VAT/phí/giảm giá, làm tròn, và **bảng xem trước phần từng người cập nhật theo từng thao tác**. Không cho lưu khi còn món chưa gán.
- `BillDetailScreen` — ai trả, ai nợ bao nhiêu, trạng thái từng khoản, cách tính của từng người.
- `InviteScreen` — link/mã mời, QR mời vào nhóm, thêm thành viên khách.

**Tab 2 — Khoản nợ**
- `DebtListScreen` — hai nhóm: *mình nợ* và *nợ mình*, sắp theo ngày.
- `DebtDetailScreen` — **màn quan trọng nhất cho người nợ**: số tiền lớn, QR động của người trả, ngân hàng + STK (che giữa, bấm để hiện đủ), nội dung chuyển khoản, nút lưu ảnh QR, nút sao chép, nút "Tôi đã chuyển". Với người trả: nút "Đã nhận", "Chưa nhận", "Nhắc", "Gửi qua…".

**Tab 3 — Tôi**
- `ProfileScreen` — tên, QR nhận tiền hiện tại, đăng xuất.
- `QrSetupScreen` — quét camera / chọn ảnh → kết quả parse → xác nhận; nhánh lỗi cho nhập tay.
- `NotificationSettingsScreen` — trạng thái quyền, bật/tắt từng loại thông báo.

Ngoài tab: `AuthStack` (đăng nhập, đăng ký, quên mật khẩu), `JoinGroupScreen` (mở từ link mời).

**Wireframe 6 khung:** Danh sách nhóm · Chi tiết nhóm · **Tạo hoá đơn theo món** · **Khoản nợ có QR** · Khai QR nhận tiền · Tất toán nhóm. Lưu ở `docs/wireframes/`.

**4 trạng thái mỗi màn.** Các empty state người mới gặp ngay: chưa có nhóm, chưa khai QR (chặn nhẹ: vẫn dùng được, nhưng nhắc rằng không ai trả lại mình bằng QR được), nhóm chưa có hoá đơn, không nợ ai.

---

## 10. Thiết kế lõi

### 10.1 Bộ chia tiền (`SplitEngine`)

**Đầu vào:** `Bill` (+ `BillItem[]` nếu theo món), danh sách thành viên tham gia, người trả.
**Đầu ra:** `Share[]` với bất biến `Σ share.amount = bill.total` và `share.amount ≥ 0`.

**Thuật toán:**

1. **Phần gốc** mỗi người:
   - `EQUAL`: `subtotal` chia đều.
   - `ITEMIZED`: mỗi món `price × quantity` chia đều cho `consumerMemberIds` của món; cộng dồn theo người.
   - `CUSTOM`: người dùng nhập; bắt buộc tổng = `subtotal`.
2. **Phụ phí và giảm giá** phân bổ theo tỉ lệ phần gốc: `vat_i = VAT × base_i / subtotal` (tương tự cho phí phục vụ và giảm giá).
3. **Phân bổ bằng phương pháp phần dư lớn nhất (largest remainder)** ở mọi bước có chia: lấy phần nguyên của từng người, rồi phát từng đồng dư cho người có phần thập phân lớn nhất (hoà thì theo thứ tự `memberId` để kết quả tất định). Tính trên số nguyên, không dùng dấu phẩy động.
4. **Làm tròn theo mức nhóm chọn** (1đ hoặc 1.000đ) cho **mọi người trừ người trả**; phần chênh `total − Σ phần đã làm tròn` dồn vào phần người trả, ghi vào `breakdown.roundingAdj`. Người nợ luôn thấy số tròn; người trả gánh hoặc được lời tối đa vài nghìn — đúng thói quen thực tế và minh bạch vì hiện rõ trong chi tiết.
5. **Đối chiếu `total`**: nếu người dùng nhập tổng trên hoá đơn thật khác tổng tính được (lệch do nhà hàng làm tròn), chênh lệch nhỏ dồn vào người trả; lệch lớn hơn ngưỡng (vd 1 %) thì cảnh báo, không tự nuốt.

Ví dụ: 100.000đ chia đều 3 người, làm tròn 1đ → 33.334 / 33.333 / 33.333. Làm tròn 1.000đ, A trả → B và C mỗi người 33.000, A chịu 34.000.

**Ca biên phải có test:** 1 người (chỉ người trả, không sinh nợ); người trả không ăn gì (phần của người trả = 0 + roundingAdj, có thể âm vài trăm đồng — cho phép riêng trường hợp này và ghi rõ trong ADR, hoặc chặn bằng cách dồn dư cho người nợ đầu tiên theo thứ tự; nhóm chốt một cách ở G1); giảm giá lớn hơn tổng; món có `quantity = 0`; thành viên đã rời nhóm vẫn nằm trong hoá đơn cũ.

### 10.2 Codec VietQR (`VietQrCodec`)

VietQR là chuẩn QR thanh toán của NAPAS, xây trên đặc tả EMVCo QR Code (Merchant-Presented Mode). Chuỗi trong QR là dãy trường **TLV**: 2 ký tự ID, 2 ký tự độ dài, rồi giá trị; một số trường lồng TLV bên trong.

Các trường app dùng:

| ID | Ý nghĩa | Ghi chú |
|---|---|---|
| `00` | Payload format indicator | luôn `01` |
| `01` | Point of initiation | `11` = QR tĩnh, `12` = QR động (có số tiền) |
| `38` | Thông tin người nhận (lồng TLV) | `00` = GUID `A000000727`; `01` = lồng (`00` BIN ngân hàng, `01` số tài khoản/thẻ); `02` = mã dịch vụ `QRIBFTTA` (chuyển tới tài khoản) hoặc `QRIBFTTC` (tới thẻ) |
| `53` | Mã tiền tệ | `704` = VND |
| `54` | Số tiền | chỉ có ở QR động |
| `58` | Quốc gia | `VN` |
| `59` | Tên người nhận | tuỳ ngân hàng, có thể thiếu |
| `62` | Thông tin bổ sung (lồng TLV) | `08` = nội dung chuyển khoản |
| `63` | CRC | CRC-16/CCITT-FALSE trên toàn chuỗi tính cả `6304` |

**`parse(payload)`**: tách TLV, kiểm CRC, kiểm GUID NAPAS và mã dịch vụ, trả `{bankBin, accountNo, accountName?, isDynamic}` hoặc lỗi có mã (`NOT_EMV`, `BAD_CRC`, `NOT_VIETQR`, `UNSUPPORTED_SERVICE`, `TRUNCATED`).
**`encode({bankBin, accountNo, amount, content})`**: dựng lại chuỗi với `01=12`, thêm `54` và `62.08`, tính CRC mới. Nội dung chuyển khoản chỉ dùng chữ không dấu, số và khoảng trắng, giới hạn độ dài (nhiều ngân hàng cắt hoặc từ chối ký tự đặc biệt).
**`BankDirectory`**: bảng tĩnh BIN → tên ngân hàng, đóng gói trong app, cập nhật theo bản phát hành.

**Vì sao sinh QR cục bộ thay vì gọi dịch vụ sinh ảnh VietQR trên mạng:** chạy offline, không gửi số tài khoản của thành viên ra dịch vụ thứ ba, và codec là logic thuần test được. Là một ADR.

**Rủi ro phải spike ở G1:** thu ảnh QR thật từ app ngân hàng của 3 thành viên (và vài người quen) để kiểm định dạng thực tế; ví điện tử có QR riêng không theo VietQR thì rơi về `RAW_IMAGE`. Kiểm luôn QR sinh ra có được app ngân hàng của cả nhóm chấp nhận không, đặc biệt trường nội dung.

### 10.3 Tối giản nợ (`DebtSimplifier`, P1)

Khi tất toán một nhóm có nhiều hoá đơn: tính số dư ròng mỗi người `net_i = Σ đã trả − Σ phần phải chịu` (chỉ tính các khoản chưa `CONFIRMED`), rồi lặp: ghép người nợ nhiều nhất với người được nợ nhiều nhất, chuyển `min(|nợ|, được nợ)`, loại người đã về 0.

Thuật toán tham lam này cho tối đa `n − 1` giao dịch với `n` người có số dư khác 0. Tìm số giao dịch **ít nhất tuyệt đối** là bài toán NP-khó; với nhóm ≤10 người có thể thêm bước tìm các tập con có tổng bằng 0 để giảm thêm, nhưng không bắt buộc. Nói được một câu này khi bảo vệ là đủ.

Mặc định của app vẫn là **trả theo từng hoá đơn** (đúng ý tưởng gốc: trả cho người đã trả). Tất toán là thao tác chủ động của cả nhóm, sinh ra các `Debt` mới kiểu `SETTLEMENT` và đánh `VOID` các khoản cũ bị gộp, để QR luôn trỏ đúng người nhận thật.

### 10.4 Máy trạng thái thanh toán

```
PENDING ──(người nợ: "đã chuyển")──► MARKED_PAID ──(người trả: "đã nhận")──► CONFIRMED
   │                                     │
   │                                     └──(người trả: "chưa nhận")──► DISPUTED ──► PENDING
   └──(người trả: "đã nhận" trực tiếp, vd trả tiền mặt)──────────────────────────► CONFIRMED
Hoá đơn bị xoá / tất toán gộp ──► VOID
```

Mỗi chuyển trạng thái chỉ một bên được làm, và chỉ đi theo mũi tên. Luật này vừa nằm ở domain (`PaymentStateMachine`, unit test) vừa nằm ở security rules phía server (mục 13) — không tin client.

---

## 11. Dữ liệu, đồng bộ, xác thực (Chương 3)

### 11.1 Chọn backend

Không tự dựng backend. Hai lựa chọn BaaS:

| | Firebase (Auth + Firestore) | Supabase (Auth + Postgres) |
|---|---|---|
| Offline | Firestore có sẵn cache offline + hàng đợi ghi | Không có sẵn; tự làm SQLite + outbox |
| Realtime | Listener có sẵn | Realtime channel có sẵn |
| Phân quyền | Security Rules | Row Level Security bằng SQL |
| Push | FCM cùng hệ sinh thái; gửi push từ server cần Cloud Functions, **kiểm lại điều kiện gói trả phí/thẻ thanh toán trước khi chọn** | Edge Functions + FCM |
| Câu chuyện Ch3 để bảo vệ | Offline "có sẵn" → ít thứ tự làm để trình bày | Tự làm outbox + đồng bộ → nhiều thứ để trình bày hơn, tốn công hơn |

**Khuyến nghị: Firebase**, vì nhóm 3 người nên dồn sức vào lõi chia tiền và VietQR. Để phần Ch3 không bị "SDK làm hộ", nhóm vẫn tự thiết kế và trình bày: ID sinh ở client + khoá tất định (ghi lặp an toàn), quy tắc một người ghi cho mỗi hoá đơn, máy trạng thái có kiểm ở server, và test security rules bằng emulator.

### 11.2 Tránh xung đột bằng thiết kế, không bằng thuật toán merge

- **Hoá đơn chỉ người tạo (hoặc người trả) sửa được.** Một người ghi → không có xung đột ghi đồng thời giữa hai người. Sửa trên hai máy của cùng người thì last-write-wins theo `version`.
- **Khoản nợ có hai người ghi nhưng ghi khác trường và theo máy trạng thái đơn điệu**, nên không có trạng thái nào bị ghi đè lùi.
- Thành viên khác muốn sửa hoá đơn thì dùng "đề xuất sửa" (P2) hoặc nhắn người tạo.

Viết thành ADR: xung đột được loại bỏ ở mô hình quyền, không cần CRDT.

### 11.3 Xác thực và lời mời

- Email/mật khẩu + Google. Không dùng OTP SMS ở P0 (chi phí và cấu hình).
- Link mời chứa `inviteToken` ngẫu nhiên, có hạn, OWNER thu hồi được. Người mở link phải đăng nhập rồi mới vào nhóm.
- **Thành viên khách** có thể được "nhận" về một tài khoản thật sau này: khách bấm link riêng của suất khách đó, đăng nhập, và `Member.userId` được gắn vào; lịch sử nợ giữ nguyên.

### 11.4 Kịch bản suy giảm (cổng G3)

| Tình huống | Hành vi |
|---|---|
| Mất mạng | Xem mọi dữ liệu đã tải; tạo/sửa hoá đơn, đánh dấu "đã chuyển" vẫn được, hiện nhãn "chờ đồng bộ"; QR vẫn sinh được vì sinh cục bộ |
| Mạng chập chờn khi đang lưu | Ghi lặp an toàn nhờ ID tất định, không trùng |
| Người trả chưa khai QR | Khoản nợ hiện số tiền + "chưa có QR", nút nhắc người trả khai |
| QR người trả không phải VietQR | Hiện ảnh gốc + số tiền cần chuyển để người nợ tự nhập |
| Bị từ chối quyền camera | Vẫn chọn ảnh từ thư viện hoặc nhập tay được |

---

## 12. Tích hợp thiết bị (Chương 4)

**Bốn tích hợp:** camera (quét QR), thư viện ảnh (đọc QR từ ảnh chụp màn hình, lưu QR ra), share sheet (gửi cho thành viên khách qua Zalo/Messenger), notification.

**Ma trận quyền:**

| Quyền | Dùng cho | Xin khi nào | Nếu từ chối |
|---|---|---|---|
| `CAMERA` | Quét QR trực tiếp | Khi bấm "Quét bằng camera" | Dùng "Chọn ảnh" hoặc nhập tay |
| Đọc ảnh (Photo Picker, Android 13+; `READ_MEDIA_IMAGES` chỉ khi thật cần) | Chọn ảnh QR | Photo Picker không cần quyền runtime | — |
| Ghi ảnh (MediaStore; `WRITE_EXTERNAL_STORAGE` chỉ cho API ≤ 28) | Lưu QR vào thư viện | Khi bấm "Lưu QR" lần đầu | Vẫn sao chép thông tin hoặc chia sẻ ảnh qua share sheet |
| `POST_NOTIFICATIONS` (API 33+) | Hoá đơn mới, đổi trạng thái, nhắc nợ | Sau khi vào nhóm đầu tiên | App chạy bình thường; banner "bạn sẽ không nhận được nhắc" + lối vào cài đặt |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Đồng bộ | Manifest | Dùng để hiện đúng trạng thái "đang offline" |

**Test trên máy thật (bắt buộc):** quét QR thật từ app ngân hàng; lưu QR ra thư viện rồi mở app ngân hàng thật, chọn "quét từ ảnh", kiểm số tài khoản + số tiền + nội dung; share sang Zalo; notification khi app nền. Quay video làm minh chứng ở `tests/evidence/`.

**Lưu ý khi test chuyển khoản thật:** chỉ cần tới màn xác nhận của app ngân hàng là đã kiểm được QR, không cần bấm chuyển. Nếu muốn chuyển thật để quay demo thì dùng số tiền nhỏ giữa các thành viên nhóm.

---

## 13. Kiểm thử, bảo mật, hiệu năng (Chương 5)

### 13.1 Tháp test

**Unit (Jest, không cần thiết bị) — khoảng 60–80 test.**
- `SplitEngine`: từng chế độ chia; VAT/phí/giảm giá; làm tròn 1đ và 1.000đ; ví dụ 100.000/3; các ca biên ở mục 10.1.
- `Allocator`: largest remainder đúng và tất định khi hoà.
- `VietQrCodec`: parse QR mẫu của từng ngân hàng (fixture lấy từ spike); CRC sai; thiếu trường `38`; độ dài TLV sai; payload bị cắt; encode rồi parse lại ra đúng dữ liệu; CRC tính ra khớp vector test đã biết.
- `DebtSimplifier`: tổng số dư về 0; số giao dịch ≤ n − 1; ví dụ vòng tròn A→B→C→A triệt tiêu.
- `PaymentStateMachine`: mọi chuyển hợp lệ và bị chặn.

**Property test (fast-check).**

```
∀ bill ngẫu nhiên (mọi splitMode, 2–15 người, VAT/phí/giảm giá, rounding):
   Σ shares = bill.total
∧ ∀ người nợ: share ≥ 0 ∧ (rounding = 1000 ⇒ share mod 1000 = 0)
∧ kết quả tất định (chạy 2 lần ra cùng kết quả)

∀ (bin, stk, amount, content) hợp lệ:
   parse(encode(x)) = x                         // round-trip
∧ CRC(encode(x)) hợp lệ

∀ tập số dư có tổng 0:
   áp các giao dịch simplify ⇒ mọi số dư = 0
∧ số giao dịch ≤ số người có số dư ≠ 0 − 1
```

Ba bất biến này là "bằng chứng đúng" của app. Câu bảo vệ: *10.000 hoá đơn ngẫu nhiên, không hoá đơn nào lệch một đồng.*

**Test security rules** (Firebase Emulator): người ngoài nhóm không đọc được nhóm; thành viên không sửa được hoá đơn người khác tạo; người nợ không tự đặt `CONFIRMED`; không ai sửa `paymentProfile` của người khác.

**Component/UI test (React Native Testing Library, 2–3 luồng):** tạo hoá đơn theo món → bảng xem trước khớp; khoản nợ hiện QR; từ chối quyền camera → không crash.

### 13.2 Threat model

| Tài sản | Mối đe doạ | Biện pháp | Rủi ro còn lại |
|---|---|---|---|
| **Số tiền chuyển đến đúng người** | **Tráo QR**: một thành viên sửa QR của người khác để tiền chảy về mình | Rule: chỉ chủ tài khoản ghi `paymentProfile`; QR sinh ra từ payload đã kiểm CRC; màn khoản nợ hiện rõ **tên ngân hàng + STK**, kèm cảnh báo nếu người nhận vừa đổi QR trong 7 ngày; nhắc người dùng đối chiếu tên người nhận mà app ngân hàng hiện trước khi bấm chuyển | Chủ tài khoản bị chiếm tài khoản app thì vẫn đổi được QR — nêu trong giới hạn đã biết |
| Số tài khoản ngân hàng | Lộ cho người ngoài nhóm | Chỉ thành viên cùng nhóm đọc được; STK che giữa khi hiển thị | Thành viên trong nhóm thấy STK — đó là mục đích của app, nói rõ khi khai QR |
| Trạng thái thanh toán | Người nợ tự đánh "đã xác nhận" | Chuyển `CONFIRMED` chỉ người trả được làm, kiểm ở server rule | — |
| Nhóm | Đoán/lộ link mời | Token ngẫu nhiên ≥128 bit, có hạn, thu hồi được | Link bị chuyển tiếp trước khi hết hạn — OWNER xoá thành viên được |
| Ảnh hoá đơn (P1) | Chứa thông tin cá nhân | Chỉ thành viên nhóm đọc | — |
| Cấu hình backend | Lộ key | Key Firebase phía client không phải secret, **bảo vệ bằng security rules**; mọi secret phía server (nếu có Functions) không lên Git; gitleaks trong CI | — |

Hai mục bắt buộc theo syllabus: **khai báo dùng AI** (phạm vi AI hỗ trợ phát triển, ghi trong báo cáo) và **đạo đức dữ liệu**: chỉ dùng STK và QR của chính thành viên nhóm để test; bộ fixture QR trong repo phải là dữ liệu giả hoặc đã được chủ tài khoản đồng ý, không commit STK người ngoài.

### 13.3 Hiệu năng

- Đo `SplitEngine` với hoá đơn 50 món × 15 người (mục tiêu < 5 ms, để bảng xem trước cập nhật tức thì khi gõ).
- Đo thời gian đồng bộ giữa 2 máy (chỉ tiêu mục 6).
- Đo cold/warm start.
- Một tối ưu có số trước/sau để đưa vào báo cáo: ví dụ memo hoá bảng xem trước ở `BillEditScreen` (đo số lần render khi nhập 20 món).

---

## 14. Phát hành & hồ sơ (Chương 6)

- **EAS Build** → APK/AAB đã ký; credential không lên Git.
- **CI**: GitHub Action chạy `tsc --noEmit`, ESLint, Jest (gồm property test) + gitleaks khi mở PR.
- `README.md` theo mẫu 10 mục, có `.env.example` cho cấu hình Firebase.
- **Release note + giới hạn đã biết**: app không xác minh giao dịch ngân hàng; ví không theo chuẩn VietQR chỉ hiện ảnh; tối giản nợ không đảm bảo số giao dịch ít nhất tuyệt đối; bảng BIN ngân hàng cập nhật theo bản phát hành.
- Hồ sơ bàn giao theo Section 7.2.

**Sáu ADR nên viết:**

1. Chọn React Native + TypeScript (và vì sao đề tài này không cần Java native).
2. Chọn Firebase thay vì Supabase / tự dựng backend.
3. Tiền là số nguyên VND; phân bổ largest remainder; làm tròn dồn cho người trả.
4. Sinh VietQR cục bộ thay vì gọi dịch vụ sinh ảnh; lưu payload thay vì ảnh.
5. Loại bỏ xung đột bằng mô hình quyền (một người ghi mỗi hoá đơn, máy trạng thái đơn điệu) thay vì thuật toán merge.
6. Mặc định trả theo từng hoá đơn; tối giản nợ là thao tác tất toán chủ động.

---

## 15. Phân công 3 người + timeline theo cổng

| Người | Mảng chính | Phụ trách | Minh chứng cá nhân |
|---|---|---|---|
| **A** | Presentation + UX | 3 tab, ~13 màn, `BillEditScreen` với bảng xem trước, 4 trạng thái mỗi màn, wireframe, design system, component test | Issue/PR UI, video demo luồng, bộ wireframe |
| **B** | Data + backend + thiết bị | Mô hình Firestore, security rules + test emulator, auth, lời mời, đồng bộ offline, quét QR camera/ảnh, lưu ảnh, share, notification, ma trận quyền, test máy thật | Issue/PR data & thiết bị, rules test report, video test máy thật, ADR #2 #5 |
| **C** | Domain | `SplitEngine`, `Allocator`, `VietQrCodec`, `BankDirectory`, `DebtSimplifier`, `PaymentStateMachine`, unit + property test, benchmark, threat model, spike QR thật | Issue/PR domain, test report, ADR #3 #4 #6 |

Việc chéo: README, báo cáo, slide; ai cũng review PR của người khác. ADR #1 viết chung.

**Timeline:**

- **G0 — Khởi tạo.** Chốt đổi đề tài trong nhóm, **xin giảng viên**, tạo repo `ET4710-Nhom03-ChiaBill`, README, `.gitignore`, `.env.example`, bật gitleaks.
- **G1 — Kiến trúc & UX.** 6 wireframe, sơ đồ kiến trúc, data model, 6 ADR, test plan. **Spike VietQR**: thu ≥15 QR thật, viết parser đầu tiên, sinh một QR động và quét bằng app ngân hàng thật. Nếu spike này hỏng thì đề tài mất điểm khác biệt, nên làm đầu tiên.
- **G2 — Vertical slice.** Trên máy thật: *tạo nhóm 3 người → thêm hoá đơn chia đều → máy thứ hai thấy khoản nợ với QR đúng số tiền → quét bằng app ngân hàng thật ra đúng STK và số tiền.*
- **G3 — Tích hợp & tin cậy.** Chia theo món + VAT/phí; offline + ghi lặp; máy trạng thái thanh toán; notification; security rules test; property test 0 vi phạm.
- **G4 — Quality gate.** Hết lỗi blocker/critical; test/security/perf report; release candidate.
- **G5 — Phát hành & bảo vệ.** APK ký, hồ sơ, demo có quét QR bằng app ngân hàng thật, portfolio cá nhân.

---

## 16. Rủi ro & giảm thiểu

| Rủi ro | Loại | Hành động sớm |
|---|---|---|
| Không được duyệt đổi đề tài lần hai | Hành chính | Hỏi giảng viên ngay, mang tài liệu này + mục 17; chuẩn bị sẵn phương án giữ TimeFit |
| QR ngân hàng thực tế khác đặc tả, hoặc app ngân hàng từ chối QR động sinh ra | Kỹ thuật | Spike ở đầu G1 với QR thật của nhiều ngân hàng; fallback `RAW_IMAGE` và nhập tay |
| Sai tiền do dấu phẩy động hoặc làm tròn | Đúng đắn | Tiền số nguyên, ADR #3, property test từ G1 |
| Tráo QR để chiếm tiền | Bảo mật | Rule chỉ chủ ghi, cảnh báo đổi QR, hiện tên ngân hàng + STK |
| Đồng bộ/offline tốn thời gian hơn dự kiến | Kỹ thuật | Dùng offline có sẵn của Firestore; khoá tất định ngay từ data model |
| Push khi app đóng cần gói trả phí | Chi phí | Để P1; P0 dùng listener realtime + local notification |
| Người dùng hiểu nhầm app "trả tiền hộ" | Sản phẩm/pháp lý | Nói rõ trong onboarding và README: app không giữ, không chuyển tiền |
| Đóng góp lệch giữa 3 người | Nhóm | 3 mảng rõ, issue/PR/review minh bạch |
| Dùng STK thật trong fixture test | Đạo đức dữ liệu | Fixture giả hoặc có đồng ý; gitleaks + review |

---

## 17. So sánh với hai đề tài trước

| Trục | MeetBrief | TimeFit | ChiaBill |
|---|---|---|---|
| Lõi kỹ thuật | Gọi LLM, parse JSON | CSP xếp lịch + heuristic | Chia tiền + làm tròn, codec VietQR (TLV/CRC), tối giản nợ |
| Test không cần thiết bị | ~15 | ~80 + property test | ~70 + 3 bộ property test |
| Tích hợp thiết bị | 1 | 2 | 4 (camera, thư viện ảnh, share, notification) |
| Ch3 (auth, sync, nhiều người dùng) | Cần thêm BaaS | **Yếu** (một người dùng) | **Mạnh, có sẵn trong bài toán** |
| Lý do dùng Java native | Không có | Có (`CalendarContract`, `AlarmManager`) | Không có → theo RN/TS như syllabus |
| Demo | Dán biên bản → tóm tắt | Nhập việc → 3 phương án giờ | Quét QR bằng app ngân hàng thật ra đúng số tiền |
| Người dùng sau môn | Ít | Chính bạn | Bất kỳ nhóm bạn nào |
| Rủi ro lớn nhất | LLM trả JSON không ổn định | Solver phình scope | QR ngân hàng thực tế lệch đặc tả; đổi đề tài lần hai |
| Tiến độ hiện tại | Tài liệu | **Tài liệu + 6.712 dòng code, solver đã verify** | Tài liệu này |

**Giữ được gì từ TimeFit nếu đổi:** cách tổ chức domain thuần + property test, phân công 3 mảng, khung README/threat model/ADR/timeline, cách viết ma trận quyền và kịch bản test máy thật. **Mất:** gần toàn bộ code Java (solver, Room, `CalendarContract`, alarm, 12 màn), vì cả nền tảng lẫn bài toán đều đổi.

**Cân nhắc sòng phẳng.**

- Nếu G0 **chưa** nộp, cả nhóm thấy ChiaBill hấp dẫn hơn, và giảng viên không chấp nhận Java: ChiaBill là lựa chọn tốt hơn TimeFit. Nó bám syllabus hơn (RN/TS), phủ Ch3 tự nhiên, và dễ tìm người thử.
- Nếu giảng viên **đã chấp nhận** Java hoặc TimeFit đã được duyệt: giữ TimeFit. Code đã chạy, solver đã verify, và đổi đề tài lần hai tốn uy tín với giảng viên nhiều hơn phần điểm Ch3 lấy lại được. Điểm yếu Ch3 của TimeFit đã có phương án bù (chia sẻ khung giờ rảnh).
- Không nên chọn theo "ý tưởng nào mới hơn". Chọn theo câu trả lời của giảng viên về hai câu hỏi: đổi đề tài được không, và Java có được không. Hỏi cả hai trong cùng một tin nhắn.

---

## Phụ lục A — Checklist cổng G0

- [ ] Cả nhóm đồng thuận đổi sang ChiaBill
- [ ] **Đã hỏi giảng viên về việc đổi đề tài** và được đồng ý
- [ ] Chốt nền tảng React Native + TypeScript (hoặc có phê duyệt nếu vẫn Java)
- [ ] Repo `ET4710-Nhom03-ChiaBill`
- [ ] README: thành viên, problem statement, vision, giá trị, luồng chính, MVP (P0/P1/P2/Later)
- [ ] `docs/project-brief.md` (tài liệu này) + vị trí lưu wireframe
- [ ] `.gitignore` loại `node_modules/`, build, `.env`; commit `.env.example`; bật gitleaks
- [ ] Giảng viên/trợ giảng truy cập được repo
- [ ] ≥1 issue cho việc trước buổi sau, có người phụ trách + tiêu chí hoàn thành — đề xuất: *spike VietQR, thu 15 QR thật*
- [ ] Mỗi thành viên ghi: phần việc nhận, 1 rủi ro thấy, việc làm trước buổi sau

## Phụ lục B — Tên sản phẩm

Gợi ý theo quy ước PascalCase Latin không dấu: **`ChiaBill`** (khuyến nghị), `SplitQR`, `ChiaDeu`, `TraLai`.
Repo tương ứng: `ET4710-Nhom03-ChiaBill`.

## Phụ lục C — Bản đồ tính năng ↔ yêu cầu môn

| Nhóm tính năng | Giai đoạn | Trục môn học được phủ |
|---|---|---|
| Đăng nhập, nhóm, lời mời, thành viên khách | P0 | **Ch3 (auth, dữ liệu dùng chung)** |
| Khai QR nhận tiền (camera/ảnh, parse VietQR) | P0 | **Ch4 (camera, quyền)**, Ch5 (unit test codec) |
| Hoá đơn + SplitEngine + làm tròn | P0 | **Ch2 (domain), Ch5 (property test)** |
| VietQR động cho từng khoản nợ | P0 | Ch5 (round-trip test), giá trị sản phẩm |
| Lưu QR, share sheet | P0 | Ch4 |
| Offline + đồng bộ + ghi lặp an toàn | P0 | **Ch3 (offline-first, đồng bộ)** |
| Máy trạng thái thanh toán + security rules | P0 | Ch3, **Ch5 (bảo mật)** |
| Notification khi app nền | P0 | Ch4 |
| Tất toán + tối giản nợ | P1 | Ch2, Ch5 |
| Push khi app đóng + nhắc nợ | P1 | Ch4 |
| Lịch sử sửa hoá đơn, ảnh hoá đơn | P1 | Ch3 |
| CI lint + test | P1 | Ch6 |
| OCR hoá đơn, đa tiền tệ, thống kê | P2 | Ch4, Ch2 |
