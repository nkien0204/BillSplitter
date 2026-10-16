// Test Firestore/Storage Security Rules của ChiaBill trên Firebase Emulator.
// Chạy: cd rules-test && npm install && npm test   (cần Java 11+ và firebase-tools)
// Ba nhân vật của poster: An trả bữa lẩu, Bình và Chi nợ An. Dũng là người ngoài.
const { test, before, beforeEach, after } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const {
  initializeTestEnvironment, assertSucceeds, assertFails,
} = require('@firebase/rules-unit-testing');
const {
  doc, getDoc, setDoc, updateDoc, deleteDoc, addDoc, collection, getDocs, query, where,
  arrayUnion, writeBatch,
} = require('firebase/firestore');
const { ref, uploadBytes } = require('firebase/storage');

const PHONE = { an: '0901111111', binh: '0902222222', chi: '0903333333', dung: '0904444444' };
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-chiabill',
    firestore: { rules: fs.readFileSync(path.join(__dirname, '..', 'firestore.rules'), 'utf8') },
    storage: { rules: fs.readFileSync(path.join(__dirname, '..', 'storage.rules'), 'utf8') },
  });
});
after(() => env.cleanup());

function as(uid) {
  return env.authenticatedContext(uid, { email: `${PHONE[uid]}@chiabill.app` });
}
const db = (uid) => as(uid).firestore();

const group = {
  name: 'Lẩu thứ Sáu', inviteCode: 'DALAT7', createdBy: 'an', createdAt: 1,
  memberIds: ['an', 'binh', 'chi'], activeMemberIds: ['an', 'binh', 'chi'], guests: {},
};
const bill = {
  title: 'Lẩu', payerId: 'an', createdBy: 'an', mode: 'EQUAL', subtotal: 620000,
  participantsCsv: 'an,binh,chi', items: [], createdAt: 2, updatedAt: 2,
};
const step = (debtorId, status, by) => ({
  billId: 'b1', debtorId, status, proofImageUrl: null, updatedBy: by, updatedAt: 3,
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const d = ctx.firestore();
    await setDoc(doc(d, 'groups/g1'), group);
    await setDoc(doc(d, 'groups/g1/bills/b1'), bill);
    await setDoc(doc(d, 'inviteCodes/DALAT7'), { groupId: 'g1' });
    for (const uid of Object.keys(PHONE)) {
      await setDoc(doc(d, `users/${uid}`), { name: uid, phone: PHONE[uid] });
      await setDoc(doc(d, `phones/${PHONE[uid]}`), { uid });
    }
  });
});

// ---------------------------------------------------------------- tài khoản

test('đăng ký: tạo hồ sơ đúng số điện thoại của mình', async () => {
  const d = env.authenticatedContext('moi', { email: '0905555555@chiabill.app' }).firestore();
  const batch = writeBatch(d);
  batch.set(doc(d, 'users/moi'), { name: 'Mới', phone: '0905555555' });
  batch.set(doc(d, 'phones/0905555555'), { uid: 'moi' });
  await assertSucceeds(batch.commit());
});

test('đăng ký: không chiếm được số điện thoại của người khác', async () => {
  const d = env.authenticatedContext('moi', { email: '0905555555@chiabill.app' }).firestore();
  await assertFails(setDoc(doc(d, 'phones/0906666666'), { uid: 'moi' }));
  await assertFails(setDoc(doc(d, 'users/moi'), { name: 'Mới', phone: '0906666666' }));
});

test('không ghi đè được số điện thoại đã có của ai', async () => {
  await assertFails(setDoc(doc(db('dung'), `phones/${PHONE.an}`), { uid: 'dung' }));
});

test('tìm theo đúng một số thì được, liệt kê danh bạ thì không', async () => {
  await assertSucceeds(getDoc(doc(db('dung'), `phones/${PHONE.an}`)));
  await assertFails(getDocs(collection(db('dung'), 'phones')));
  await assertFails(getDocs(collection(db('dung'), 'users')));
});

test('chỉ sửa được hồ sơ của mình, và không đổi được số điện thoại', async () => {
  await assertSucceeds(updateDoc(doc(db('an'), 'users/an'), { bankBin: '970436', accountNo: '0123' }));
  await assertFails(updateDoc(doc(db('binh'), 'users/an'), { accountNo: '9999' }));
  await assertFails(updateDoc(doc(db('an'), 'users/an'), { phone: '0907777777' }));
});

// ---------------------------------------------------------------- nhóm

test('người ngoài không đọc được nhóm, hoá đơn, khoản nợ', async () => {
  await assertFails(getDoc(doc(db('dung'), 'groups/g1')));
  await assertFails(getDoc(doc(db('dung'), 'groups/g1/bills/b1')));
  await assertFails(getDocs(collection(db('dung'), 'groups/g1/statuses')));
});

test('thành viên tìm được nhóm của mình bằng truy vấn activeMemberIds', async () => {
  const q = query(collection(db('binh'), 'groups'), where('activeMemberIds', 'array-contains', 'binh'));
  await assertSucceeds(getDocs(q));
});

test('tạo nhóm cùng mã mời trong một lần ghi', async () => {
  const d = db('dung');
  const batch = writeBatch(d);
  batch.set(doc(d, 'groups/g2'), { ...group, inviteCode: 'HOIAN2', createdBy: 'dung',
    memberIds: ['dung'], activeMemberIds: ['dung'] });
  batch.set(doc(d, 'inviteCodes/HOIAN2'), { groupId: 'g2' });
  await assertSucceeds(batch.commit());
});

test('không gắn được mã mời vào nhóm của người khác', async () => {
  await assertFails(setDoc(doc(db('dung'), 'inviteCodes/ABCDEF'), { groupId: 'g1' }));
});

test('có mã mời: tự thêm mình vào nhóm', async () => {
  await assertSucceeds(updateDoc(doc(db('dung'), 'groups/g1'), {
    memberIds: arrayUnion('dung'), activeMemberIds: arrayUnion('dung'),
  }));
});

test('xin vào nhóm thì không được đổi gì khác, cũng không thêm người khác', async () => {
  await assertFails(updateDoc(doc(db('dung'), 'groups/g1'), {
    memberIds: arrayUnion('dung'), activeMemberIds: arrayUnion('dung'), name: 'Của Dũng',
  }));
  await assertFails(updateDoc(doc(db('dung'), 'groups/g1'), {
    memberIds: arrayUnion('dung', 'x'), activeMemberIds: arrayUnion('dung', 'x'),
  }));
});

test('không ai đảo được thứ tự thành viên (thứ tự quyết định phần dư)', async () => {
  await assertFails(updateDoc(doc(db('an'), 'groups/g1'), { memberIds: ['binh', 'an', 'chi'] }));
});

test('tự rời nhóm được; xoá người khác thì chỉ người tạo nhóm', async () => {
  await assertFails(updateDoc(doc(db('binh'), 'groups/g1'), { activeMemberIds: ['an', 'binh'] }));
  await assertSucceeds(updateDoc(doc(db('chi'), 'groups/g1'), { activeMemberIds: ['an', 'binh'] }));
  await assertSucceeds(updateDoc(doc(db('an'), 'groups/g1'), { activeMemberIds: ['an'] }));
});

test('chỉ người tạo nhóm xoá được nhóm', async () => {
  await assertFails(deleteDoc(doc(db('binh'), 'groups/g1')));
  await assertSucceeds(deleteDoc(doc(db('an'), 'groups/g1')));
});

// ---------------------------------------------------------------- hoá đơn

test('thành viên thêm hoá đơn được, người ngoài thì không', async () => {
  await assertSucceeds(setDoc(doc(db('binh'), 'groups/g1/bills/b2'), { ...bill, payerId: 'binh', createdBy: 'binh' }));
  await assertFails(setDoc(doc(db('dung'), 'groups/g1/bills/b3'), { ...bill, createdBy: 'dung' }));
});

test('người trả phải là thành viên nhóm', async () => {
  await assertFails(setDoc(doc(db('binh'), 'groups/g1/bills/b2'), { ...bill, payerId: 'dung', createdBy: 'binh' }));
});

test('chỉ người tạo hoặc người trả sửa được hoá đơn', async () => {
  await assertFails(updateDoc(doc(db('chi'), 'groups/g1/bills/b1'), { subtotal: 1 }));
  await assertSucceeds(updateDoc(doc(db('an'), 'groups/g1/bills/b1'), { subtotal: 600000 }));
});

// ---------------------------------------------------------------- máy trạng thái thanh toán

test('Bình báo đã chuyển phần của mình', async () => {
  await assertSucceeds(setDoc(doc(db('binh'), 'groups/g1/statuses/b1_binh'), step('binh', 'MARKED_PAID', 'binh')));
});

test('Chi không báo hộ Bình được', async () => {
  await assertFails(setDoc(doc(db('chi'), 'groups/g1/statuses/b1_binh'), step('binh', 'MARKED_PAID', 'chi')));
});

test('Bình không tự xác nhận khoản của mình', async () => {
  await assertFails(setDoc(doc(db('binh'), 'groups/g1/statuses/b1_binh'), step('binh', 'CONFIRMED', 'binh')));
});

test('An (người nhận) xác nhận được', async () => {
  await assertSucceeds(setDoc(doc(db('an'), 'groups/g1/statuses/b1_binh'), step('binh', 'CONFIRMED', 'an')));
});

test('An báo chưa nhận chỉ sau khi Bình báo đã chuyển', async () => {
  await assertFails(setDoc(doc(db('an'), 'groups/g1/statuses/b1_binh'), step('binh', 'DISPUTED', 'an')));
  await setDoc(doc(db('binh'), 'groups/g1/statuses/b1_binh'), step('binh', 'MARKED_PAID', 'binh'));
  await assertSucceeds(setDoc(doc(db('an'), 'groups/g1/statuses/b1_binh'), step('binh', 'DISPUTED', 'an')));
});

test('khoản đã xác nhận thì không quay lại được', async () => {
  await setDoc(doc(db('an'), 'groups/g1/statuses/b1_binh'), step('binh', 'CONFIRMED', 'an'));
  await assertFails(setDoc(doc(db('an'), 'groups/g1/statuses/b1_binh'), step('binh', 'PENDING', 'an')));
  await assertFails(setDoc(doc(db('binh'), 'groups/g1/statuses/b1_binh'), step('binh', 'MARKED_PAID', 'binh')));
});

test('id khoản nợ phải khớp hoá đơn và người nợ', async () => {
  await assertFails(setDoc(doc(db('binh'), 'groups/g1/statuses/b1_chi'), step('binh', 'MARKED_PAID', 'binh')));
});

// ---------------------------------------------------------------- hộp thư

test('gửi tin phải ký đúng tên mình; chỉ chủ hộp thư đọc được', async () => {
  const msg = { fromId: 'binh', message: 'Bình báo đã chuyển 206.667đ', createdAt: 4 };
  await assertSucceeds(addDoc(collection(db('binh'), 'users/an/inbox'), msg));
  await assertFails(addDoc(collection(db('binh'), 'users/an/inbox'), { ...msg, fromId: 'chi' }));
  await assertSucceeds(getDocs(collection(db('an'), 'users/an/inbox')));
  await assertFails(getDocs(collection(db('binh'), 'users/an/inbox')));
});

// ---------------------------------------------------------------- ảnh (Storage)

const jpeg = new Uint8Array([0xff, 0xd8, 0xff, 0xd9]);

test('tải ảnh chuyển khoản vào thư mục của mình', async () => {
  const s = as('binh').storage();
  await assertSucceeds(uploadBytes(ref(s, 'users/binh/proof/a.jpg'), jpeg, { contentType: 'image/jpeg' }));
});

test('không tải ảnh vào thư mục người khác, không tải tệp không phải ảnh', async () => {
  const s = as('binh').storage();
  await assertFails(uploadBytes(ref(s, 'users/an/proof/a.jpg'), jpeg, { contentType: 'image/jpeg' }));
  await assertFails(uploadBytes(ref(s, 'users/binh/proof/a.txt'), jpeg, { contentType: 'text/plain' }));
});

// ---------------------------------------------------------------- đúng các lần ghi app thực hiện

test('app sửa hoá đơn: ghi hoá đơn và đưa khoản đổi số tiền về PENDING trong một lô', async () => {
  await setDoc(doc(db('binh'), 'groups/g1/statuses/b1_binh'), step('binh', 'MARKED_PAID', 'binh'));
  const d = db('an');
  const batch = writeBatch(d);
  batch.set(doc(d, 'groups/g1/bills/b1'), { ...bill, subtotal: 700000, updatedAt: 5 });
  batch.set(doc(d, 'groups/g1/statuses/b1_binh'), step('binh', 'PENDING', 'an'));
  await assertSucceeds(batch.commit());
});

test('app thêm thành viên: ghi lại cả tài liệu nhóm từ máy', async () => {
  await assertSucceeds(setDoc(doc(db('binh'), 'groups/g1'), {
    ...group, memberIds: [...group.memberIds, 'dung'], activeMemberIds: [...group.activeMemberIds, 'dung'],
  }));
});

test('người tạo rời nhóm thì trao quyền cho người khác được; người khác không tự nhận quyền', async () => {
  await assertFails(setDoc(doc(db('binh'), 'groups/g1'), { ...group, createdBy: 'binh' }));
  await assertSucceeds(setDoc(doc(db('an'), 'groups/g1'), {
    ...group, createdBy: 'binh', activeMemberIds: ['binh', 'chi'],
  }));
});

test('app xoá nhóm: khoản nợ, hoá đơn, mã mời rồi nhóm, trong một lô', async () => {
  await setDoc(doc(db('an'), 'groups/g1/statuses/b1_binh'), step('binh', 'CONFIRMED', 'an'));
  const d = db('an');
  const batch = writeBatch(d);
  batch.delete(doc(d, 'groups/g1/statuses/b1_binh'));
  batch.delete(doc(d, 'groups/g1/bills/b1'));
  batch.delete(doc(d, 'inviteCodes/DALAT7'));
  batch.delete(doc(d, 'groups/g1'));
  await assertSucceeds(batch.commit());
});

test('người đã rời nhóm vào lại bằng mã mời, giữ vị trí cũ', async () => {
  await updateDoc(doc(db('chi'), 'groups/g1'), { activeMemberIds: ['an', 'binh'] });
  await assertSucceeds(updateDoc(doc(db('chi'), 'groups/g1'), {
    memberIds: arrayUnion('chi'), activeMemberIds: arrayUnion('chi'),
  }));
});
