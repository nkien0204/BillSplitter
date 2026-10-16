# Luồng hoạt động của ChiaBill

Mỗi bước của mỗi luồng có một chú thích ngay trong code, dạng:

```java
// LUỒNG 4 · Thêm hoá đơn · Bước 3/7: Bấm "Lưu và báo cho nhóm": gửi BillInput xuống repository.
//   → FirestoreLedgerRepository.saveBill
```

Dòng `→` chỉ bước tiếp theo nằm ở đâu. Muốn đi hết một luồng thì tìm chữ `LUỒNG 4` trong Android Studio (Ctrl+Shift+F) và đọc theo số bước.

Bảng dưới đây liệt kê từng bước kèm tệp và số dòng của chú thích, trên nhánh `feature/firebase`.

## Luồng 1. Đăng nhập

Mở app, đăng ký hoặc đăng nhập bằng số điện thoại và mật khẩu.

| Bước | Ở đâu | Việc xảy ra |
|---|---|---|
| 1 | [`app/…/ui/LoginActivity.java:81`](../app/src/main/java/vn/nhom03/chiabill/ui/LoginActivity.java#L81) | Bấm "Đăng nhập": gửi số điện thoại + mật khẩu xuống AuthRepository. (Đăng ký: askRegister, cùng đường.) |
| 2 | [`app/…/data/repo/FirebaseAuthRepository.java:86`](../app/src/main/java/vn/nhom03/chiabill/data/repo/FirebaseAuthRepository.java#L86) | Số điện thoại → email giả 09xx@chiabill.app → Firebase Auth kiểm mật khẩu, rồi đọc tên ở users/{uid}. |
| 3 | [`app/…/ui/LoginActivity.java:106`](../app/src/main/java/vn/nhom03/chiabill/ui/LoginActivity.java#L106) | Đúng mật khẩu: lưu uid vào SessionManager và mở màn chính. |
| 4 | [`app/…/data/repo/FirestoreLedgerRepository.java:101`](../app/src/main/java/vn/nhom03/chiabill/data/repo/FirestoreLedgerRepository.java#L101) | Firebase báo đăng nhập/đăng xuất. Có tài khoản → bắt đầu nghe dữ liệu của tài khoản đó (LUỒNG 2). |
| 5 | [`app/…/ui/LoginActivity.java:42`](../app/src/main/java/vn/nhom03/chiabill/ui/LoginActivity.java#L42) | Mở app lần sau: còn phiên Firebase thì vào thẳng màn chính, không hỏi mật khẩu. |

## Luồng 2. Đồng bộ Firestore → màn hình

Luồng nền mọi luồng khác dựa vào: thay đổi trên Firestore về tới màn hình của mọi máy.

| Bước | Ở đâu | Việc xảy ra |
|---|---|---|
| 1 | [`app/…/data/repo/FirestoreLedgerRepository.java:120`](../app/src/main/java/vn/nhom03/chiabill/data/repo/FirestoreLedgerRepository.java#L120) | Mở 3 luồng nghe: các nhóm có mình (activeMemberIds), hộp thư của mình, hồ sơ của mình. |
| 2 | [`app/…/data/repo/FirestoreLedgerRepository.java:163`](../app/src/main/java/vn/nhom03/chiabill/data/repo/FirestoreLedgerRepository.java#L163) | Danh sách nhóm đổi: với mỗi nhóm mới thì nghe thêm hoá đơn + trạng thái nợ (watchGroup), |
| 3 | [`app/…/data/repo/FirestoreLedgerRepository.java:294`](../app/src/main/java/vn/nhom03/chiabill/data/repo/FirestoreLedgerRepository.java#L294) | Gom mọi tài liệu đang nghe thành dòng Room (toUser, toBill, toStatus...) và thay toàn bộ Room |
| 4 | [`app/…/data/repo/LocalLedgerRepository.java:67`](../app/src/main/java/vn/nhom03/chiabill/data/repo/LocalLedgerRepository.java#L67) | Mỗi bảng Room là một LiveData; bảng nào đổi thì dựng lại AppSnapshot (rebuild bên dưới). |
| 5 | [`app/…/data/repo/AppSnapshot.java:70`](../app/src/main/java/vn/nhom03/chiabill/data/repo/AppSnapshot.java#L70) | Chuyển dòng Room thành đối tượng domain và tính sổ nợ một lần cho mọi màn hình (LUỒNG 8). |
| 6 | [`app/…/ui/MainActivity.java:58`](../app/src/main/java/vn/nhom03/chiabill/ui/MainActivity.java#L58) | Màn hình chỉ "quan sát" AppSnapshot: dữ liệu đổi ở bất kỳ máy nào thì render chạy lại. |

## Luồng 3. Tạo nhóm và mời

Tạo nhóm, thêm bạn bằng số điện thoại, hoặc tự vào nhóm bằng mã mời.

| Bước | Ở đâu | Việc xảy ra |
|---|---|---|
| 1 | [`app/…/ui/CreateGroupActivity.java:70`](../app/src/main/java/vn/nhom03/chiabill/ui/CreateGroupActivity.java#L70) | Nhập số điện thoại của bạn để thêm vào nhóm. |
| 2 | [`app/…/data/repo/FirestoreLedgerRepository.java:654`](../app/src/main/java/vn/nhom03/chiabill/data/repo/FirestoreLedgerRepository.java#L654) | Tra đúng một số: phones/{số} → uid → users/{uid}. Rules cấm liệt kê nên không dò được danh bạ. |
| 3 | [`app/…/ui/CreateGroupActivity.java:136`](../app/src/main/java/vn/nhom03/chiabill/ui/CreateGroupActivity.java#L136) | Bấm "Tạo nhóm": tên nhóm, thành viên có tài khoản, khách (chỉ có tên). |
| 4 | [`app/…/data/repo/LocalLedgerRepository.java:575`](../app/src/main/java/vn/nhom03/chiabill/data/repo/LocalLedgerRepository.java#L575) | Tạo id nhóm, mã mời 6 ký tự, tài khoản khách; ghi vào Room; soạn tin "đã thêm bạn vào nhóm". |
| 5 | [`app/…/data/repo/FirestoreLedgerRepository.java:869`](../app/src/main/java/vn/nhom03/chiabill/data/repo/FirestoreLedgerRepository.java#L869) | Chép nhóm lên Firestore: groups/{id} và inviteCodes/{mã} trong một lô (cùng thành công hoặc cùng hỏng). |
| 6 | [`app/…/ui/tabs/GroupsFragment.java:115`](../app/src/main/java/vn/nhom03/chiabill/ui/tabs/GroupsFragment.java#L115) | Người khác nhập mã mời để tự vào nhóm. |
| 7 | [`app/…/data/repo/FirestoreLedgerRepository.java:706`](../app/src/main/java/vn/nhom03/chiabill/data/repo/FirestoreLedgerRepository.java#L706) | Mã → id nhóm, rồi chỉ thêm đúng mình vào memberIds/activeMemberIds (arrayUnion). |

## Luồng 4. Thêm hoá đơn

Nhập hoá đơn, xem trước phần từng người, lưu và báo cho nhóm.

| Bước | Ở đâu | Việc xảy ra |
|---|---|---|
| 1 | [`app/…/ui/BillEditActivity.java:100`](../app/src/main/java/vn/nhom03/chiabill/ui/BillEditActivity.java#L100) | Mở form hoá đơn: tạo BillDraft (bản nháp) từ nhóm hoặc từ hoá đơn cũ khi sửa. |
| 2 | [`app/…/ui/BillEditActivity.java:350`](../app/src/main/java/vn/nhom03/chiabill/ui/BillEditActivity.java#L350) | Mỗi lần gõ: SplitEngine (domain) tính lại phần từng người ngay trên máy, không cần mạng. |
| 3 | [`app/…/ui/BillEditActivity.java:409`](../app/src/main/java/vn/nhom03/chiabill/ui/BillEditActivity.java#L409) | Bấm "Lưu và báo cho nhóm": gửi BillInput xuống repository. |
| 4 | [`app/…/data/repo/LocalLedgerRepository.java:622`](../app/src/main/java/vn/nhom03/chiabill/data/repo/LocalLedgerRepository.java#L622) | Kiểm luật: còn ở nhóm, chia hợp lệ, sửa thì phải là người tạo hoặc người trả. |
| 5 | [`app/…/data/repo/FirestoreLedgerRepository.java:900`](../app/src/main/java/vn/nhom03/chiabill/data/repo/FirestoreLedgerRepository.java#L900) | Chép hoá đơn (kèm món trong mảng items) lên groups/{g}/bills/{id}; khoản bị đổi số tiền ghi PENDING. |
| 6 | [`firestore.rules:97`](../firestore.rules#L97) | Firestore kiểm lại phía máy chủ: là thành viên, người trả thuộc nhóm, |
| 7 | [`app/…/ui/GroupDetailActivity.java:74`](../app/src/main/java/vn/nhom03/chiabill/ui/GroupDetailActivity.java#L74) | Máy khác: hoá đơn mới hiện trong nhóm trong khoảng một giây, kèm thông báo (LUỒNG 6). |

## Luồng 5. Trả nợ bằng VietQR

Người nợ quét QR có sẵn số tiền, gửi ảnh chuyển khoản; người nhận xác nhận.

| Bước | Ở đâu | Việc xảy ra |
|---|---|---|
| 1 | [`app/…/ui/QrSetupActivity.java:140`](../app/src/main/java/vn/nhom03/chiabill/ui/QrSetupActivity.java#L140) | Người nhận tiền khai QR một lần: quét QR ngân hàng của mình, VietQrCodec đọc ra ngân hàng + số tài khoản. |
| 2 | [`app/…/ui/DebtDetailActivity.java:109`](../app/src/main/java/vn/nhom03/chiabill/ui/DebtDetailActivity.java#L109) | Người nợ mở khoản nợ: VietQrCodec tạo mã VietQR có sẵn số tiền và nội dung. |
| 3 | [`app/…/ui/DebtDetailActivity.java:247`](../app/src/main/java/vn/nhom03/chiabill/ui/DebtDetailActivity.java#L247) | Nén ảnh chuyển khoản rồi gửi lên Firebase Storage (users/{uid}/proof/...), nhận lại URL. |
| 4 | [`app/…/ui/DebtDetailActivity.java:258`](../app/src/main/java/vn/nhom03/chiabill/ui/DebtDetailActivity.java#L258) | "Tôi đã chuyển" kèm URL ảnh. |
| 5 | [`app/…/data/repo/LocalLedgerRepository.java:871`](../app/src/main/java/vn/nhom03/chiabill/data/repo/LocalLedgerRepository.java#L871) | Máy trạng thái thanh toán quyết định bước này có hợp lệ không, theo vai trò người bấm |
| 6 | [`domain/…/ledger/PaymentStateMachine.java:31`](../domain/src/main/java/vn/nhom03/chiabill/domain/ledger/PaymentStateMachine.java#L31) | Luật duy nhất của tiền: người nợ báo đã chuyển; chỉ người nhận xác nhận hoặc báo chưa nhận. |
| 7 | [`app/…/data/repo/FirestoreLedgerRepository.java:962`](../app/src/main/java/vn/nhom03/chiabill/data/repo/FirestoreLedgerRepository.java#L962) | Chép trạng thái mới lên groups/{g}/statuses/{billId}_{debtorId}, kèm ảnh chuyển khoản. |
| 8 | [`app/…/ui/DebtDetailActivity.java:275`](../app/src/main/java/vn/nhom03/chiabill/ui/DebtDetailActivity.java#L275) | Người nhận xem ảnh rồi bấm "Đã nhận tiền" (CONFIRM) hoặc "Chưa nhận được" (DISPUTE): cùng đường bước 5 → 7. |

## Luồng 6. Thông báo

Tin "thêm hoá đơn", "đã chuyển", "đã nhận" đi từ máy này sang máy kia.

| Bước | Ở đâu | Việc xảy ra |
|---|---|---|
| 1 | [`app/…/data/repo/LocalLedgerRepository.java:952`](../app/src/main/java/vn/nhom03/chiabill/data/repo/LocalLedgerRepository.java#L952) | Mọi tin ("thêm hoá đơn", "đã chuyển", "đã nhận"...) được soạn ở đây rồi giao cho EventSink. |
| 2 | [`app/…/data/repo/FirestoreLedgerRepository.java:600`](../app/src/main/java/vn/nhom03/chiabill/data/repo/FirestoreLedgerRepository.java#L600) | Ghi tin vào hộp thư người nhận: users/{người nhận}/inbox. Rules bắt fromId đúng là mình. |
| 3 | [`app/…/data/repo/FirestoreLedgerRepository.java:254`](../app/src/main/java/vn/nhom03/chiabill/data/repo/FirestoreLedgerRepository.java#L254) | App đang mở: luồng nghe hộp thư thấy tin mới → bật thông báo trên máy. |
| 4 | [`functions/index.js:17`](../functions/index.js#L17) | App đang tắt: Cloud Function chạy khi có tin mới, gửi thông báo đẩy (FCM) tới mọi máy của người nhận. |
| 5 | [`app/…/util/NotificationHelper.java:51`](../app/src/main/java/vn/nhom03/chiabill/util/NotificationHelper.java#L51) | Hiện thông báo; bấm vào thì mở thẳng khoản nợ hoặc hoá đơn (MainActivity đọc DEBT_KEY/BILL_ID). |

## Luồng 7. Chụp hoá đơn

Đọc chữ trên ảnh hoá đơn ngay trên máy để điền sẵn form.

| Bước | Ở đâu | Việc xảy ra |
|---|---|---|
| 1 | [`app/…/ui/BillEditActivity.java:453`](../app/src/main/java/vn/nhom03/chiabill/ui/BillEditActivity.java#L453) | Chụp hoặc chọn ảnh hoá đơn. |
| 2 | [`app/…/util/ReceiptScanner.java:39`](../app/src/main/java/vn/nhom03/chiabill/util/ReceiptScanner.java#L39) | ML Kit đọc chữ ngay trên máy (không gửi ảnh đi đâu), trả về từng dòng kèm vị trí. |
| 3 | [`domain/…/receipt/ReceiptParser.java:176`](../domain/src/main/java/vn/nhom03/chiabill/domain/receipt/ReceiptParser.java#L176) | Ghép các mảnh thành hàng, tìm tên quán, tổng tiền và các món (tên + giá). Java thuần, có test. |
| 4 | [`app/…/ui/BillEditActivity.java:475`](../app/src/main/java/vn/nhom03/chiabill/ui/BillEditActivity.java#L475) | Điền tên quán, tổng tiền, hoặc danh sách món vào bản nháp; người dùng vẫn sửa được. |

## Luồng 8. Số dư và tất toán

Ai nợ ai bao nhiêu, và cách chuyển ít lần nhất để xong nợ.

| Bước | Ở đâu | Việc xảy ra |
|---|---|---|
| 1 | [`domain/…/ledger/Ledger.java:34`](../domain/src/main/java/vn/nhom03/chiabill/domain/ledger/Ledger.java#L34) | Từ hoá đơn + trạng thái: chia từng hoá đơn bằng SplitEngine, mỗi người nợ một khoản (Debt) cho người trả. |
| 2 | [`domain/…/ledger/Ledger.java:110`](../domain/src/main/java/vn/nhom03/chiabill/domain/ledger/Ledger.java#L110) | Số dư ròng: được nợ trừ đang nợ, chỉ tính khoản chưa CONFIRMED. Tổng cả nhóm luôn bằng 0. |
| 3 | [`domain/…/ledger/DebtSimplifier.java:26`](../domain/src/main/java/vn/nhom03/chiabill/domain/ledger/DebtSimplifier.java#L26) | Rút gọn: ghép người âm nhiều nhất với người dương nhiều nhất, lặp lại. |
| 4 | [`app/…/ui/SettleActivity.java:44`](../app/src/main/java/vn/nhom03/chiabill/ui/SettleActivity.java#L44) | Màn "Tất toán nhóm" hiện danh sách ai chuyển cho ai bao nhiêu; tab Khoản nợ dùng owedTo/owedBy. |

## Ba lớp bảo vệ của một thao tác

Ví dụ Bình bấm "Tôi đã chuyển" (Luồng 5):

1. Màn hình chỉ hiện nút cho đúng người nợ (`DebtDetailActivity`).
2. `PaymentStateMachine` trong lõi Java kiểm lại vai trò và trạng thái trước khi ghi (`LocalLedgerRepository.applyDebtAction`).
3. `firestore.rules` kiểm lần cuối trên máy chủ Firebase, nên app bị sửa cũng không ghi bậy được. 32 test trong `rules-test/` chứng minh điều này.
