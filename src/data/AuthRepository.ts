export interface AuthUser {
  id: string;
  name: string;
  phone: string;
}

/** Mã lỗi ổn định cho UI; thông điệp tiếng Việt nằm ở messageForAuthError. */
export type AuthErrorCode =
  | 'USER_ALREADY_EXISTS'
  | 'INVALID_CREDENTIALS'
  | 'INVALID_PHONE'
  | 'WEAK_PASSWORD'
  | 'BAD_REQUEST'
  | 'TOO_MANY_REQUESTS'
  | 'NETWORK_ERROR'
  | 'AUTH_NOT_ENABLED'
  | 'BAD_CONFIG'
  | 'UNKNOWN_ERROR';

export class AuthError extends Error {
  constructor(readonly code: AuthErrorCode) {
    super(code);
    this.name = 'AuthError';
  }
}

export type Unsubscribe = () => void;

export interface AuthRepository {
  /** Phát lại trạng thái đăng nhập, kể cả lần khôi phục phiên khi mở app. userId = null: chưa đăng nhập. */
  onAuthStateChanged(cb: (userId: string | null) => void): Unsubscribe;
  /** Tạo tài khoản và đăng nhập luôn. Số điện thoại là khoá để người khác tìm thấy bạn. */
  register(name: string, phone: string, password: string): Promise<AuthUser>;
  login(phone: string, password: string): Promise<AuthUser>;
  logout(): Promise<void>;
}

const MESSAGES: Record<AuthErrorCode, string> = {
  USER_ALREADY_EXISTS: 'Số điện thoại này đã được đăng ký.',
  INVALID_CREDENTIALS: 'Số điện thoại hoặc mật khẩu không chính xác.',
  INVALID_PHONE: 'Số điện thoại không hợp lệ (10 số, bắt đầu bằng 03, 05, 07, 08 hoặc 09).',
  WEAK_PASSWORD: 'Mật khẩu cần ít nhất 6 ký tự.',
  BAD_REQUEST: 'Yêu cầu không hợp lệ. Vui lòng kiểm tra lại thông tin.',
  TOO_MANY_REQUESTS: 'Thử quá nhiều lần. Vui lòng đợi một lát rồi thử lại.',
  NETWORK_ERROR: 'Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.',
  AUTH_NOT_ENABLED:
    'Đăng nhập bằng Email/Mật khẩu chưa được bật trong Firebase. Vào Authentication › Sign-in method để bật.',
  BAD_CONFIG: 'Cấu hình Firebase sai hoặc thiếu. Kiểm tra file .env rồi chạy lại: npx expo start -c',
  UNKNOWN_ERROR: 'Đã xảy ra lỗi không xác định. Vui lòng thử lại!',
};

export function messageForAuthError(e: unknown): string {
  if (e instanceof AuthError) return MESSAGES[e.code];
  return MESSAGES.UNKNOWN_ERROR;
}
