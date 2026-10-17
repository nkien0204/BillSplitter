import type { Persistence } from 'firebase/auth';

/**
 * Firebase xuất getReactNativePersistence từ bản build "react-native" của firebase/auth,
 * nhưng file kiểu mặc định lại là bản web nên TypeScript không thấy. Khai báo bổ sung ở đây.
 * (Không dùng tsconfig paths: Metro cũng đọc paths và sẽ nạp nhầm file .d.ts.)
 */
declare module 'firebase/auth' {
  interface ReactNativeAsyncStorage {
    setItem(key: string, value: string): Promise<void>;
    getItem(key: string): Promise<string | null>;
    removeItem(key: string): Promise<void>;
  }
  export function getReactNativePersistence(storage: ReactNativeAsyncStorage): Persistence;
}
