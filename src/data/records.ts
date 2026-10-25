/**
 * Hình dạng tài liệu Firestore. Tiền là số nguyên VND; thời gian là epoch ms (giống bản Room cũ).
 * Firestore không nhận `undefined` nên trường vắng mặt luôn là `null`.
 *
 *   users/{uid}                 tài khoản hoặc khách (id "guest-…")
 *   phones/{phone}              { uid }: tra cứu đúng số điện thoại, không liệt kê
 *   groups/{groupId}            memberOrder = mọi người từng ở nhóm (thứ tự chia), activeMemberIds = người hiện tại
 *   inviteCodes/{code}          { groupId }
 *   joinProofs/{uid}            { code }: bằng chứng biết mã mời, chỉ để security rules đối chiếu
 *   bills/{billId}              hoá đơn: tổng tiền + người tham gia (chia đều)
 *   debtStatuses/{billId:uid}   trạng thái khoản nợ; thiếu = PENDING
 *   inbox/{id}                  thông báo gửi tới một tài khoản
 */

export interface UserRecord {
  id: string;
  name: string;
  colorIndex: number;
  /** Khách: có tên, chưa có tài khoản, không đăng nhập được. */
  guest: boolean;
  /** Số điện thoại đã chuẩn hoá (0912345678). Khách: null. */
  phone: string | null;
  bankBin: string | null;
  accountNo: string | null;
  accountName: string | null;
  /** Lúc đổi QR gần nhất, để cảnh báo "vừa đổi QR" (chống tráo QR). 0 = chưa khai. */
  qrUpdatedAt: number;
  createdAt: number;
  /** Chỉ có ở khách: tài khoản đã tạo khách này. */
  createdBy?: string | null;
}

export function hasPaymentProfile(u: UserRecord | null | undefined): boolean {
  return !!u && u.bankBin != null && u.accountNo != null && u.accountNo !== '';
}

export interface GroupRecord {
  id: string;
  name: string;
  /** Mã mời 6 ký tự, không trùng. Người có mã tự vào nhóm được (FR3). */
  inviteCode: string | null;
  createdBy: string;
  createdAt: number;
  /** Mọi người từng ở nhóm, theo thứ tự: dùng để chia hoá đơn. Chỉ thêm vào cuối, không bao giờ xoá. */
  memberOrder: string[];
  /** Thành viên hiện tại: dùng cho giao diện, quyền và hoá đơn mới. */
  activeMemberIds: string[];
}

export interface BillRecord {
  id: string;
  groupId: string;
  title: string;
  payerId: string;
  /** Tổng hoá đơn (VND). */
  subtotal: number;
  /** Những người cùng chia đều. */
  participants: string[];
  /** Tên hằng của Category. */
  category: string;
  createdBy: string | null;
  createdAt: number;
  updatedAt: number;
}

export interface DebtStatusRecord {
  /** billId:debtorId */
  key: string;
  billId: string;
  groupId: string;
  debtorId: string;
  status: string;
  updatedAt: number;
}

export interface InboxRecord {
  id: string;
  recipientId: string;
  fromId: string;
  message: string;
  billId: string | null;
  debtKey: string | null;
  createdAt: number;
}
