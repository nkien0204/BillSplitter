import { router } from 'expo-router';
import { useState } from 'react';
import { Text, View } from 'react-native';
import { PhoneNumber } from '@/domain';
import type { UserRecord } from '@/data/records';
import { useAction, useLedger } from '@/state/AppContext';
import { Avatar, Button, Card, Chip, ChipRow, Field, Label, Loading, Muted, Screen, toast } from '@/ui/components';

/**
 * Tạo nhóm. Thành viên có tài khoản được thêm bằng cách tìm đúng số điện thoại (không liệt kê mọi người dùng),
 * thành viên khách chỉ cần tên.
 */
export default function CreateGroup() {
  const app = useLedger();
  const run = useAction();
  const [name, setName] = useState('');
  const [nameError, setNameError] = useState<string | null>(null);
  const [phone, setPhone] = useState('');
  const [phoneError, setPhoneError] = useState<string | null>(null);
  const [found, setFound] = useState<UserRecord | null>(null);
  const [members, setMembers] = useState<{ id: string; name: string }[]>([]);
  const [guestName, setGuestName] = useState('');
  const [guests, setGuests] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  if (!app) return <Loading />;
  const { snapshot: s, userId: me, repo } = app;

  async function find() {
    setPhoneError(null);
    setFound(null);
    try {
      const u = await repo.findUserByPhone(phone);
      if (!u) return setPhoneError('Chưa có tài khoản nào dùng số này. Có thể thêm người đó làm khách ở dưới.');
      if (u.id === me) return setPhoneError('Đây là số của bạn.');
      setFound(u);
    } catch (e) {
      setPhoneError(e instanceof Error ? e.message : String(e));
    }
  }

  function addFound() {
    if (!found || members.some((m) => m.id === found.id)) return;
    setMembers([...members, { id: found.id, name: found.name }]);
    setFound(null);
    setPhone('');
  }

  function addGuest() {
    const n = guestName.trim();
    if (!n) return;
    setGuests([...guests, n]);
    setGuestName('');
  }

  async function save() {
    const n = name.trim();
    if (!n) return setNameError('Cần đặt tên nhóm');
    setNameError(null);
    if (members.length === 0 && guests.length === 0) return toast('Nhóm cần ít nhất một người nữa ngoài bạn.');
    setBusy(true);
    let id: string | null = null;
    const ok = await run(async () => {
      id = await repo.createGroup(n, me, members.map((m) => m.id), guests);
    });
    setBusy(false);
    if (ok && id) router.replace({ pathname: '/group/[id]', params: { id } });
  }

  return (
    <Screen>
      <Field label="Tên nhóm" placeholder="vd Đà Lạt cuối tuần" value={name} onChangeText={setName} error={nameError} />

      <Label>Thành viên có tài khoản</Label>
      <ChipRow>
        <Chip text={`${s.name(me)} (bạn)`} />
        {members.map((m) => (
          <Chip key={m.id} text={m.name} onClose={() => setMembers(members.filter((x) => x.id !== m.id))} />
        ))}
      </ChipRow>
      <Field
        label="Tìm bằng số điện thoại"
        placeholder="vd 0904 444 444"
        value={phone}
        onChangeText={setPhone}
        keyboardType="phone-pad"
        error={phoneError}
        onSubmitEditing={find}
      />
      <Button kind="outline" title="Tìm" onPress={find} />
      {found && (
        <Card style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
          <Avatar name={found.name} colorIndex={found.colorIndex} />
          <View style={{ flex: 1 }}>
            <Text style={{ fontWeight: '600' }}>{found.name}</Text>
            <Muted>{PhoneNumber.masked(found.phone)}</Muted>
          </View>
          <Button
            title={members.some((m) => m.id === found.id) ? 'Đã thêm' : 'Thêm'}
            disabled={members.some((m) => m.id === found.id)}
            onPress={addFound}
          />
        </Card>
      )}

      <Label>Thành viên khách (chưa có tài khoản)</Label>
      <ChipRow>
        {guests.map((g, i) => (
          <Chip key={g + i} text={`${g} (khách)`} onClose={() => setGuests(guests.filter((_, j) => j !== i))} />
        ))}
      </ChipRow>
      <Field placeholder="Tên khách" value={guestName} onChangeText={setGuestName} onSubmitEditing={addGuest} />
      <Button kind="outline" title="Thêm khách" onPress={addGuest} />

      <Button title="Tạo nhóm" onPress={save} disabled={busy} style={{ marginTop: 12 }} />
    </Screen>
  );
}
