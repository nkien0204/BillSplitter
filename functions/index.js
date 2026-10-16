// Cloud Function duy nhất của ChiaBill: tin mới vào users/{uid}/inbox → thông báo đẩy tới mọi máy
// của người đó (users/{uid}.fcmTokens). Nhờ vậy app đang tắt vẫn nhận "Bình báo đã chuyển 200.000đ".
// App đang mở thì tự nghe hộp thư, không cần hàm này. Triển khai: firebase deploy --only functions
// (cần gói Blaze; vùng phải trùng vùng của Firestore).
const { onDocumentCreated } = require('firebase-functions/v2/firestore');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore, FieldValue } = require('firebase-admin/firestore');
const { getMessaging } = require('firebase-admin/messaging');

initializeApp();

const DEAD_TOKEN = new Set([
  'messaging/registration-token-not-registered',
  'messaging/invalid-registration-token',
]);

// LUỒNG 6 · Thông báo · Bước 4/5: App đang tắt: Cloud Function chạy khi có tin mới, gửi thông báo đẩy (FCM) tới mọi máy của người nhận.
//   → Android tự hiện thông báo; ChiaBillMessagingService giữ mã FCM luôn mới
exports.pushInbox = onDocumentCreated(
  { document: 'users/{uid}/inbox/{id}', region: 'asia-southeast1' },
  async (event) => {
    const msg = event.data && event.data.data();
    if (!msg || !msg.message) return;
    const userRef = getFirestore().doc(`users/${event.params.uid}`);
    const user = await userRef.get();
    const tokens = (user.exists && user.get('fcmTokens')) || [];
    if (tokens.length === 0) return;

    const res = await getMessaging().sendEachForMulticast({
      tokens,
      notification: { title: 'ChiaBill', body: msg.message },
      data: { billId: msg.billId || '', debtKey: msg.debtKey || '' },
      android: { notification: { channelId: 'debts' } },
    });

    // Máy đã gỡ app hoặc đăng xuất: bỏ mã đó để lần sau không gửi nữa.
    const dead = tokens.filter((_, i) => !res.responses[i].success
      && DEAD_TOKEN.has(res.responses[i].error && res.responses[i].error.code));
    if (dead.length > 0) await userRef.update({ fcmTokens: FieldValue.arrayRemove(...dead) });
  },
);
