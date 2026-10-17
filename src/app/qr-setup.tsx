import { Camera, CameraView, useCameraPermissions } from 'expo-camera';
import * as Clipboard from 'expo-clipboard';
import * as ImagePicker from 'expo-image-picker';
import { router } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { Modal, View } from 'react-native';
import {
  BankDirectory,
  Money,
  PaymentTarget,
  TransferContent,
  VietQrCodec,
} from '@/domain';
import { useAction, useLedger } from '@/state/AppContext';
import { Button, Card, Chip, ChipRow, Field, Label, Loading, Muted, Screen, toast } from '@/ui/components';
import { colors } from '@/ui/theme';
import { Text } from 'react-native';

/**
 * Khai QR nhận tiền: chọn ảnh chụp màn hình QR, quét camera, dán chuỗi, hoặc nhập tay.
 * Mọi đường đều đi qua VietQrCodec.parse (kiểm CRC) trước khi lưu.
 */
export default function QrSetup() {
  const app = useLedger();
  const run = useAction();
  const [permission, requestPermission] = useCameraPermissions();
  const [scanning, setScanning] = useState(false);
  const [raw, setRaw] = useState('');
  const [pending, setPending] = useState<{ target: PaymentTarget; note: string } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [bin, setBin] = useState('970436');
  const [accountNo, setAccountNo] = useState('');
  const [accountNoError, setAccountNoError] = useState<string | null>(null);
  const [accountName, setAccountName] = useState('');
  const prefilled = useRef(false);

  const me = app?.userId;
  const u = app && me ? app.snapshot.user(me) : undefined;
  useEffect(() => {
    if (prefilled.current || !u) return;
    prefilled.current = true;
    if (u.bankBin && u.accountNo) {
      setBin(u.bankBin);
      setAccountNo(u.accountNo);
      setAccountName(u.accountName ?? '');
    }
  }, [u]);

  if (!app || !me) return <Loading />;
  const { repo } = app;
  const hasProfile = !!u?.bankBin && !!u?.accountNo;

  function handleRaw(text: string) {
    const r = VietQrCodec.parse(text);
    if (!r.ok) {
      setPending(null);
      setError(`${r.error} · ${r.message}`);
      return;
    }
    setError(null);
    let note = `Ngân hàng: ${BankDirectory.nameOf(r.target.bankBin)}\nSố tài khoản: ${r.target.accountNo}`;
    if (r.target.accountName) note += `\nChủ tài khoản: ${r.target.accountName}`;
    note += r.dynamic
      ? `\nQR này có sẵn số tiền ${Money.format(r.amount)}; ChiaBill chỉ lấy số tài khoản.`
      : '\nQR tĩnh (không kèm số tiền)';
    setPending({ target: r.target, note });
  }

  async function save(t: PaymentTarget) {
    const ok = await run(() => repo.setPaymentTarget(me as string, t));
    if (ok) {
      toast(`Đã lưu QR nhận tiền: ${BankDirectory.nameOf(t.bankBin)} ${t.maskedAccount()}`);
      router.back();
    }
  }

  async function pickImage() {
    const res = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 1 });
    if (res.canceled || res.assets.length === 0) return;
    try {
      const found = await Camera.scanFromURLAsync(res.assets[0].uri, ['qr']);
      if (found.length === 0) throw new Error('none');
      setRaw(found[0].data);
      handleRaw(found[0].data);
    } catch {
      setPending(null);
      setError('Không tìm thấy mã QR trong ảnh. Thử ảnh chụp màn hình rõ nét, chỉ có mã QR.');
    }
  }

  async function startScan() {
    if (!permission?.granted && !(await requestPermission()).granted) {
      toast('Cần quyền camera để quét QR.');
      return;
    }
    setScanning(true);
  }

  function saveManual() {
    if (!PaymentTarget.isValidAccount(accountNo.trim())) return setAccountNoError('Số tài khoản 6–19 chữ số');
    setAccountNoError(null);
    const name = TransferContent.toAscii(accountName).toUpperCase().trim();
    void save(new PaymentTarget(bin, accountNo.trim(), name));
  }

  return (
    <Screen>
      <Muted>Quét hoặc chọn ảnh QR nhận tiền từ app ngân hàng của bạn. App chỉ lấy số tài khoản, không lấy số tiền.</Muted>
      <Button title="Chọn ảnh QR (ảnh chụp màn hình)" onPress={pickImage} />
      <Button kind="outline" title="Quét bằng camera" onPress={startScan} />

      <Label>Hoặc dán chuỗi QR</Label>
      <Field value={raw} onChangeText={setRaw} multiline placeholder="000201…" autoCapitalize="none" autoCorrect={false} />
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <Button style={{ flex: 1 }} kind="outline" title="Đọc chuỗi" onPress={() => handleRaw(raw)} />
        <Button
          style={{ flex: 1 }}
          kind="outline"
          title="Dán"
          onPress={async () => {
            const t = await Clipboard.getStringAsync();
            setRaw(t);
            handleRaw(t);
          }}
        />
      </View>
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <Button
          style={{ flex: 1 }}
          kind="text"
          title="Mẫu hợp lệ"
          onPress={() => {
            const p = VietQrCodec.encode(new PaymentTarget('970422', '0987654321', 'NGUYEN VAN A'), 0, '');
            setRaw(p);
            handleRaw(p);
          }}
        />
        <Button
          style={{ flex: 1 }}
          kind="text"
          title="Mẫu sai CRC"
          onPress={() => {
            let p = VietQrCodec.encode(new PaymentTarget('970422', '0987654321', ''), 0, '');
            p = p.slice(0, -1) + (p.endsWith('A') ? 'B' : 'A');
            setRaw(p);
            handleRaw(p);
          }}
        />
      </View>

      {error && (
        <Card style={{ backgroundColor: colors.errBg }}>
          <Text style={{ color: colors.errFg }}>{error}</Text>
        </Card>
      )}
      {pending && (
        <Card style={{ backgroundColor: colors.okBg, gap: 8 }}>
          <Text style={{ color: colors.okFg }}>{pending.note}</Text>
          <Button title="Dùng QR này" onPress={() => save(pending.target)} />
        </Card>
      )}

      <Label>Hoặc nhập tay</Label>
      <ChipRow>
        {[...BankDirectory.all()].map(([b, n]) => (
          <Chip key={b} text={n} selected={bin === b} onPress={() => setBin(b)} />
        ))}
      </ChipRow>
      <Field label="Số tài khoản" keyboardType="number-pad" value={accountNo} onChangeText={setAccountNo} error={accountNoError} />
      <Field label="Tên chủ tài khoản (không bắt buộc)" autoCapitalize="characters" value={accountName} onChangeText={setAccountName} />
      <Button title="Lưu" onPress={saveManual} />
      {hasProfile && (
        <Button
          kind="danger"
          title="Xoá QR nhận tiền"
          onPress={async () => {
            const ok = await run(() => repo.clearPaymentTarget(me));
            if (ok) {
              toast('Đã xoá QR nhận tiền.');
              router.back();
            }
          }}
        />
      )}

      <Modal visible={scanning} animationType="slide" onRequestClose={() => setScanning(false)}>
        <View style={{ flex: 1, backgroundColor: '#000' }}>
          <CameraView
            style={{ flex: 1 }}
            barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
            onBarcodeScanned={({ data }) => {
              setScanning(false);
              setRaw(data);
              handleRaw(data);
            }}
          />
          <View style={{ position: 'absolute', bottom: 48, left: 24, right: 24 }}>
            <Button title="Đóng" onPress={() => setScanning(false)} />
          </View>
        </View>
      </Modal>
    </Screen>
  );
}
