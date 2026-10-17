import * as Clipboard from 'expo-clipboard';
import { Stack, router, useLocalSearchParams } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { Share, Text, View } from 'react-native';
import QRCode from 'react-native-qrcode-svg';
import {
  type Action,
  BankDirectory,
  Money,
  PaymentStateMachine,
  TransferContent,
  VietQrCodec,
  isOpen,
} from '@/domain';
import { useAction, useLedger } from '@/state/AppContext';
import { Button, Card, Loading, Muted, Pill, Screen, toast } from '@/ui/components';
import { PILL_ERR, PILL_NEUTRAL, PILL_OK, PILL_WARN, colors, statusColors, statusLabel } from '@/ui/theme';
import { ago, day } from '@/util/format';
import { type QrHandle, saveToGallery, shareQr } from '@/util/qrExport';

const RECENT_QR_CHANGE = 7 * 24 * 3600 * 1000;

/**
 * Màn quan trọng nhất cho người nợ: số tiền, VietQR của người trả có sẵn số tiền + nội dung,
 * và các nút theo máy trạng thái thanh toán. Người trả thấy nút xác nhận / nhắc / gửi QR.
 */
export default function DebtDetail() {
  const { key: debtKey } = useLocalSearchParams<{ key: string }>();
  const app = useLedger();
  const run = useAction();
  const qr = useRef<QrHandle | null>(null);
  const [reveal, setReveal] = useState(false);
  const [showPayload, setShowPayload] = useState(false);

  const debt = app?.snapshot.ledger().debt(debtKey);
  useEffect(() => {
    if (app && !debt) {
      toast('Khoản nợ không còn (hoá đơn đã bị sửa hoặc xoá).');
      if (router.canGoBack()) router.back();
    }
  }, [app, debt]);
  if (!app || !debt) return <Loading />;
  const { snapshot: s, userId: me, repo } = app;
  const d = debt;
  const bill = s.ledger().bill(d.billId)!;
  const g = s.group(d.groupId);
  const debtor = s.name(d.debtorId);
  const creditor = s.name(d.creditorId);
  const debtorUser = s.user(d.debtorId);
  const creditorUser = s.user(d.creditorId);
  const role = PaymentStateMachine.roleOf(me, d.debtorId, d.creditorId);
  const st = d.status;

  const target = s.paymentTarget(d.creditorId);
  const content = TransferContent.forDebt(d.billId, debtor);
  const payload = target ? VietQrCodec.encode(target, d.amount, content) : null;
  const showQr = !!target && (role === 'DEBTOR' || (role === 'CREDITOR' && isOpen(st)));
  const qrAgo = creditorUser ? Date.now() - creditorUser.qrUpdatedAt : Infinity;
  const recentChange = role === 'DEBTOR' && !!creditorUser && creditorUser.qrUpdatedAt > 0 && qrAgo < RECENT_QR_CHANGE;

  const act = (action: Action, ok: string) => run(async () => { await repo.applyDebtAction(debtKey, action, me); toast(ok); });
  const canPay = role === 'DEBTOR' && PaymentStateMachine.canApply(st, 'MARK_PAID', role);
  const pendingCreditor = role === 'CREDITOR' && (st === 'PENDING' || st === 'DISPUTED');
  const guest = !!debtorUser?.guest;
  const qrName = 'chiabill-' + debtKey.replace(':', '-');

  async function copy() {
    const text =
      (target ? `${BankDirectory.nameOf(target.bankBin)} · STK ${target.accountNo}\n` : '') +
      `Số tiền: ${d.amount}\nNội dung: ${content}`;
    await Clipboard.setStringAsync(text);
    toast('Đã sao chép thông tin chuyển khoản.');
  }

  async function share() {
    let msg = `${debtor} ơi, phần của bạn cho “${bill.title}” là ${Money.format(d.amount)}.`;
    if (target) {
      msg += `\nQuét QR đính kèm, hoặc chuyển ${BankDirectory.nameOf(target.bankBin)} STK ${target.accountNo}, nội dung: ${content}`;
    }
    try {
      if (qr.current && target) {
        await Clipboard.setStringAsync(msg); // share sheet chỉ gửi được ảnh: lời nhắn nằm sẵn trong bộ nhớ tạm để dán
        await shareQr(qr.current, qrName);
      } else {
        await Share.share({ message: msg });
      }
    } catch (e) {
      toast('Không mở được chia sẻ: ' + (e instanceof Error ? e.message : String(e)));
    }
  }

  let note: { text: string; c: { bg: string; fg: string } } | null = null;
  if (st === 'CONFIRMED') note = { text: `${creditor} đã xác nhận nhận tiền. Khoản này đã xong.`, c: PILL_OK };
  else if (st === 'MARKED_PAID' && role === 'DEBTOR') note = { text: `Đang chờ ${creditor} xác nhận đã nhận tiền.`, c: PILL_WARN };
  else if (st === 'DISPUTED' && role === 'DEBTOR') {
    note = { text: `${creditor} báo chưa nhận được tiền. Kiểm tra lại giao dịch rồi bấm “Tôi đã chuyển”.`, c: PILL_ERR };
  } else if (st === 'DISPUTED' && role === 'CREDITOR') note = { text: `Bạn đã báo chưa nhận được. ${debtor} sẽ kiểm tra lại.`, c: PILL_ERR };
  else if (role === 'OTHER') note = { text: 'Bạn chỉ xem được khoản này. Người nợ và người được trả mới thao tác được.', c: PILL_NEUTRAL };

  return (
    <Screen>
      <Stack.Screen options={{ title: 'Khoản nợ', headerBackTitle: g?.name }} />
      <Card style={{ alignItems: 'center', gap: 6 }}>
        <Muted>{`${role === 'DEBTOR' ? 'Bạn' : debtor}  →  ${role === 'CREDITOR' ? 'Bạn' : creditor}`}</Muted>
        <Text style={{ fontSize: 30, fontWeight: '800' }}>{Money.format(d.amount)}</Text>
        <Muted>{`${bill.title} · ${day(bill.createdAt)}`}</Muted>
        <Pill text={statusLabel(st)} colors={statusColors(st)} />
      </Card>

      {note && (
        <Card style={{ backgroundColor: note.c.bg }}>
          <Text style={{ color: note.c.fg }}>{note.text}</Text>
        </Card>
      )}

      {showQr && target && payload && (
        <Card style={{ alignItems: 'center', gap: 8 }}>
          <QRCode value={payload} size={240} getRef={(c: unknown) => (qr.current = c as QrHandle)} />
          <Muted>{role === 'DEBTOR' ? 'Quét bằng app ngân hàng, số tiền đã điền sẵn' : `QR mà ${debtor} sẽ nhận`}</Muted>
          <Text style={{ fontWeight: '700' }}>{BankDirectory.nameOf(target.bankBin)}</Text>
          <Text>{reveal ? target.accountNo : target.maskedAccount()}</Text>
          <Muted>{target.accountName === '' ? '—' : target.accountName}</Muted>
          <Muted>{`Nội dung: ${content}`}</Muted>
          <Button kind="text" title={reveal ? 'Ẩn số tài khoản' : 'Hiện đủ số tài khoản'} onPress={() => setReveal(!reveal)} />
          <Text style={{ color: recentChange ? colors.owe : colors.muted, fontSize: 12, textAlign: 'center' }}>
            {recentChange
              ? `${creditor} vừa đổi QR nhận tiền ${ago(creditorUser!.qrUpdatedAt)}. Đối chiếu kỹ tên người nhận mà app ngân hàng hiện ra trước khi chuyển.`
              : 'Trước khi bấm chuyển, đối chiếu tên người nhận mà app ngân hàng hiện ra.'}
          </Text>
        </Card>
      )}

      {!target && isOpen(st) && role !== 'OTHER' && (
        <Card style={{ backgroundColor: colors.warnBg, gap: 8 }}>
          <Text style={{ fontWeight: '700', color: colors.warnFg }}>
            {role === 'CREDITOR' ? 'Bạn chưa khai QR nhận tiền' : `${creditor} chưa khai QR nhận tiền`}
          </Text>
          {role === 'CREDITOR' ? (
            <Button title="Khai QR ngay" onPress={() => router.push('/qr-setup')} />
          ) : (
            <Button
              title={`Nhắc ${creditor} khai QR`}
              onPress={async () => {
                await run(() =>
                  repo.sendReminder(
                    d.creditorId, me,
                    `${debtor} muốn trả bạn ${Money.format(d.amount)} nhưng bạn chưa khai QR nhận tiền. Vào tab Tôi để khai.`,
                    d.billId, d.key,
                  ),
                );
                toast(`Đã nhắc ${creditor}.`);
              }}
            />
          )}
        </Card>
      )}

      {canPay && (
        <View style={{ gap: 8 }}>
          <Button kind="outline" title="Lưu QR vào ảnh" disabled={!target} onPress={async () => {
            try {
              if (!qr.current) return;
              await saveToGallery(qr.current, qrName);
              toast('Đã lưu vào thư viện ảnh. Mở app ngân hàng › Quét QR › chọn ảnh này.');
            } catch (e) {
              toast(e instanceof Error ? e.message : String(e));
            }
          }} />
          <Button kind="outline" title="Sao chép thông tin chuyển khoản" onPress={copy} />
          <Button title="Tôi đã chuyển" onPress={() => act('MARK_PAID', `Đã báo ${creditor}. Chờ xác nhận.`)} />
        </View>
      )}

      {role === 'CREDITOR' && st === 'MARKED_PAID' && (
        <Card style={{ gap: 8 }}>
          <Text style={{ fontWeight: '700' }}>{`${debtor} báo đã chuyển. Bạn đã nhận được tiền chưa?`}</Text>
          <Button title="Đã nhận tiền" onPress={() => act('CONFIRM', `Đã xác nhận nhận ${Money.format(d.amount)} từ ${debtor}.`)} />
          <Button kind="outline" title="Chưa nhận được" onPress={() => act('DISPUTE', `Đã báo ${debtor} là bạn chưa nhận được.`)} />
        </Card>
      )}

      {pendingCreditor && (
        <View style={{ gap: 8 }}>
          <Button
            kind="outline"
            title={guest ? `${debtor} là khách` : `Nhắc ${debtor}`}
            disabled={guest}
            onPress={async () => {
              await run(() =>
                repo.sendReminder(d.debtorId, me, `${creditor} nhắc bạn trả ${Money.format(d.amount)} cho “${bill.title}”.`, d.billId, d.key),
              );
              toast(`Đã gửi nhắc tới ${debtor}.`);
            }}
          />
          <Button kind="outline" title="Gửi QR cho người nợ" onPress={share} />
          <Button title={`${debtor} đã trả tiền mặt`} onPress={() => act('CONFIRM', `Đã ghi nhận ${debtor} trả tiền mặt.`)} />
        </View>
      )}

      <Button kind="text" title={showPayload ? 'Ẩn chuỗi VietQR' : 'Xem chuỗi VietQR (chi tiết kỹ thuật)'} onPress={() => setShowPayload(!showPayload)} />
      {showPayload && (
        <Card>
          <Text selectable style={{ fontFamily: 'Courier', fontSize: 12 }}>
            {payload == null
              ? 'Người nhận chưa khai QR nên chưa sinh được chuỗi.'
              : `${payload}\n\nCRC-16/CCITT: ${payload.slice(-4)}\n54 (số tiền): ${d.amount}\n62.08 (nội dung): ${content}`}
          </Text>
        </Card>
      )}
    </Screen>
  );
}
