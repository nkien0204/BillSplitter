import { router } from "expo-router";
import { useEffect, useState } from "react";
import { Linking, Modal, Text, View } from "react-native";
import QRCode from "react-native-qrcode-svg";
import { BankDirectory, PhoneNumber, VietQrCodec } from "@/domain";
import { useAction, useApp, useLedger } from "@/state/AppContext";
import { canPost, ensurePermission } from "@/state/notifications";
import {
  Avatar,
  Button,
  Card,
  Field,
  Label,
  Loading,
  Muted,
  type RowData,
  Rows,
  Screen,
} from "@/ui/components";
import { ago } from "@/util/format";
import { colors } from "@/ui/theme";

export default function Profile() {
  const app = useLedger();
  const { auth } = useApp();
  const run = useAction();
  const [renaming, setRenaming] = useState(false);
  const [name, setName] = useState("");
  const [notifOk, setNotifOk] = useState(true);

  useEffect(() => {
    ensurePermission()
      .then(setNotifOk)
      .catch(() => setNotifOk(false));
  }, []);

  if (!app) return <Loading />;
  const { snapshot: s, userId: me, repo } = app;
  const u = s.user(me);
  if (!u) return <Loading />;
  const t = s.paymentTarget(me);

  const inbox: RowData[] = s
    .inboxOf(me)
    .slice(0, 20)
    .map((e) => ({
      key: e.id,
      title: ago(e.createdAt),
      subtitle: e.message,
      multiline: true,
      onPress:
        e.debtKey && s.ledger().debt(e.debtKey)
          ? () =>
              router.push({
                pathname: "/debt/[key]",
                params: { key: e.debtKey as string },
              })
          : undefined,
    }));

  return (
    <Screen>
      <Card style={{ flexDirection: "row", alignItems: "center", gap: 12 }}>
        <Avatar name={u.name} colorIndex={u.colorIndex} size={52} />
        <View style={{ flex: 1 }}>
          <Text style={{ fontSize: 18, fontWeight: "700" }}>{u.name}</Text>
          <Muted>
            {u.phone ? PhoneNumber.format(u.phone) : "Chưa có số điện thoại"}
          </Muted>
        </View>
        <Button
          kind="outline"
          title="Sửa tên"
          onPress={() => {
            setName(u.name);
            setRenaming(true);
          }}
        />
      </Card>

      {!notifOk && (
        <Card style={{ backgroundColor: colors.warnBg }}>
          <Text style={{ color: colors.warnFg }}>
            Thông báo đang tắt. Tin vẫn vào hộp thư bên dưới.
          </Text>
          <Button
            kind="text"
            title="Mở cài đặt"
            onPress={() => Linking.openSettings()}
          />
        </Card>
      )}

      <Label>QR nhận tiền</Label>
      {t ? (
        <Card style={{ alignItems: "center", gap: 8 }}>
          <QRCode value={VietQrCodec.encode(t, 0, "")} size={200} />
          <Text style={{ fontWeight: "700" }}>
            {BankDirectory.nameOf(t.bankBin)}
          </Text>
          <Text>{t.maskedAccount()}</Text>
          <Muted>
            {t.accountName === ""
              ? "QR tĩnh, không kèm số tiền"
              : t.accountName}
          </Muted>
        </Card>
      ) : (
        <Card>
          <Muted>
            Bạn chưa khai QR nhận tiền. Người nợ cần QR này để chuyển khoản cho
            bạn.
          </Muted>
        </Card>
      )}
      <Button
        title={t ? "Đổi QR nhận tiền" : "Khai QR nhận tiền"}
        onPress={() => router.push("/qr-setup")}
      />

      <Label>Hộp thư</Label>
      <Rows rows={inbox} empty="Chưa có thông báo." />

      <Button
        kind="outline"
        title="Đăng xuất"
        onPress={() => auth.logout()}
        style={{ marginTop: 16 }}
      />

      <Modal
        transparent
        animationType="fade"
        visible={renaming}
        onRequestClose={() => setRenaming(false)}
      >
        <View
          style={{
            flex: 1,
            backgroundColor: "#0008",
            justifyContent: "center",
            padding: 24,
          }}
        >
          <Card style={{ gap: 12 }}>
            <Text style={{ fontSize: 17, fontWeight: "700" }}>Sửa tên</Text>
            <Field value={name} onChangeText={setName} autoCapitalize="words" />
            <View
              style={{
                flexDirection: "row",
                gap: 12,
                justifyContent: "flex-end",
              }}
            >
              <Button
                kind="text"
                title="Huỷ"
                onPress={() => setRenaming(false)}
              />
              <Button
                title="Lưu"
                onPress={async () => {
                  const ok = await run(() => repo.renameUser(me, name));
                  if (ok) setRenaming(false);
                }}
              />
            </View>
          </Card>
        </View>
      </Modal>
    </Screen>
  );
}
