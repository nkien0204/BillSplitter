import * as FileSystem from 'expo-file-system/legacy';
import * as MediaLibrary from 'expo-media-library/legacy';
import * as Sharing from 'expo-sharing';

/** Phần mô tả tối thiểu của đối tượng react-native-qrcode-svg ta cần (getRef). */
export interface QrHandle {
  toDataURL(cb: (base64: string) => void): void;
}

function toPng(handle: QrHandle, name: string): Promise<string> {
  return new Promise((resolve, reject) => {
    try {
      handle.toDataURL(async (base64) => {
        try {
          if (!FileSystem.cacheDirectory) throw new Error('Không có thư mục tạm');
          const uri = `${FileSystem.cacheDirectory}${name}.png`;
          await FileSystem.writeAsStringAsync(uri, base64, { encoding: FileSystem.EncodingType.Base64 });
          resolve(uri);
        } catch (e) {
          reject(e);
        }
      });
    } catch (e) {
      reject(e);
    }
  });
}

/** Lưu ảnh QR vào thư viện ảnh. Ném lỗi nếu người dùng từ chối quyền. */
export async function saveToGallery(handle: QrHandle, name: string): Promise<void> {
  const perm = await MediaLibrary.requestPermissionsAsync(true);
  if (!perm.granted) throw new Error('Không có quyền lưu ảnh. Dùng “Sao chép” hoặc chia sẻ thay thế.');
  await MediaLibrary.saveToLibraryAsync(await toPng(handle, name));
}

/** Chia sẻ ảnh QR qua share sheet (Zalo, Messenger…). */
export async function shareQr(handle: QrHandle, name: string): Promise<void> {
  if (!(await Sharing.isAvailableAsync())) throw new Error('Thiết bị không hỗ trợ chia sẻ ảnh.');
  await Sharing.shareAsync(await toPng(handle, name), { mimeType: 'image/png', dialogTitle: 'Gửi QR thanh toán' });
}
