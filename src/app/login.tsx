import { useState } from "react";
import {
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  Text,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { messageForAuthError } from "@/data/AuthRepository";
import { useApp } from "@/state/AppContext";
import { Button, Field, toast } from "@/ui/components";
import { colors } from "@/ui/theme";

export default function Login() {
  const { auth } = useApp();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [errors, setErrors] = useState<{
    phone?: string;
    password?: string;
    name?: string;
  }>({});

  async function submit() {
    const e: typeof errors = {};
    if (mode === "register" && name.trim() === "")
      e.name = "Vui lòng nhập tên hiển thị";
    if (phone.trim() === "") e.phone = "Vui lòng nhập số điện thoại";
    if (password.trim() === "") e.password = "Vui lòng nhập mật khẩu";
    setErrors(e);
    if (Object.keys(e).length > 0) return;
    setBusy(true);
    try {
      if (mode === "login") await auth.login(phone.trim(), password);
      else await auth.register(name.trim(), phone.trim(), password);
      // AppProvider thấy đổi trạng thái đăng nhập và tự chuyển sang màn chính.
    } catch (err) {
      toast(messageForAuthError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.brand }}>
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
        <ScrollView
          contentContainerStyle={{
            flexGrow: 1,
            justifyContent: "center",
            padding: 24,
            gap: 14,
          }}
          keyboardShouldPersistTaps="handled"
        >
          <View style={{ alignItems: "center", marginBottom: 12 }}>
            <Text style={{ color: "#fff", fontSize: 34, fontWeight: "800" }}>
              ChiaBill
            </Text>
            <Text style={{ color: colors.brandOnDark, marginTop: 4 }}>
              Chia sẻ chi phí - Kết nối mọi người
            </Text>
          </View>
          <View
            style={{
              backgroundColor: colors.surface,
              borderRadius: 16,
              padding: 16,
              gap: 12,
            }}
          >
            <Text
              style={{ fontSize: 18, fontWeight: "700", color: colors.ink }}
            >
              {mode === "login" ? "Đăng nhập" : "Tạo tài khoản"}
            </Text>
            {mode === "register" && (
              <Field
                label="Tên hiển thị"
                value={name}
                onChangeText={setName}
                autoCapitalize="words"
                error={errors.name}
              />
            )}
            <Field
              label="Số điện thoại"
              placeholder="vd 0905 555 555"
              value={phone}
              onChangeText={setPhone}
              keyboardType="phone-pad"
              error={errors.phone}
            />
            <Field
              label="Mật khẩu"
              value={password}
              onChangeText={setPassword}
              secureTextEntry
              error={errors.password}
            />
            {mode === "register" && (
              <Text style={{ color: colors.muted, fontSize: 12 }}>
                Người khác sẽ tìm thấy bạn bằng số điện thoại này để thêm vào
                nhóm. Mật khẩu tối thiểu 6 ký tự.
              </Text>
            )}
            <Button
              title={mode === "login" ? "Đăng nhập" : "Tạo tài khoản"}
              onPress={submit}
              disabled={busy}
            />
            <Button
              kind="text"
              title={
                mode === "login"
                  ? "+ Tạo tài khoản mới"
                  : "Đã có tài khoản? Đăng nhập"
              }
              onPress={() => {
                setMode(mode === "login" ? "register" : "login");
                setErrors({});
              }}
            />
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}
