import { router } from 'expo-router';
import { useState } from 'react';
import { Modal, Text, View } from 'react-native';
import { Money } from '@/domain';
import { useAction, useLedger } from '@/state/AppContext';
import { Button, Card, Field, Loading, Muted, type RowData, Rows, Screen } from '@/ui/components';
import { PILL_NEUTRAL, PILL_OK, PILL_OWE, colors } from '@/ui/theme';

export default function Groups() {
  const app = useLedger();
  const run = useAction();
  const [joining, setJoining] = useState(false);
  const [code, setCode] = useState('');
  if (!app) return <Loading />;
  const { snapshot: s, userId: me, repo } = app;
  const l = s.ledger();

  const rows: RowData[] = s.groupsOf(me).map((g) => {
    const net = l.owedTo(me, g.id) - l.owedBy(me, g.id);
    const base: RowData = {
      key: g.id,
      avatarName: g.name,
      colorIndex: Math.abs(hash(g.id)),
      title: g.name,
      subtitle: `${s.membersOf(g.id).length} thành viên · ${l.billsOfGroup(g.id).length} hoá đơn`,
      onPress: () => router.push({ pathname: '/group/[id]', params: { id: g.id } }),
    };
    if (net > 0) return { ...base, amount: Money.format(net), amountColor: colors.brand, pill: 'Bạn được nợ', pillColors: PILL_OK };
    if (net < 0) return { ...base, amount: Money.format(-net), amountColor: colors.owe, pill: 'Bạn đang nợ', pillColors: PILL_OWE };
    return { ...base, pill: 'Sòng phẳng', pillColors: PILL_NEUTRAL };
  });

  async function join() {
    const ok = await run(async () => {
      const groupId = await repo.joinByInviteCode(code, me);
      setJoining(false);
      setCode('');
      router.push({ pathname: '/group/[id]', params: { id: groupId } });
    });
    void ok;
  }

  return (
    <Screen>
      <Muted>Chào {s.name(me)}, số dư của bạn ở mọi nhóm</Muted>
      <View style={{ flexDirection: 'row', gap: 12 }}>
        <Card style={{ flex: 1 }}>
          <Muted>Bạn được nợ</Muted>
          <Text style={{ fontSize: 20, fontWeight: '800', color: colors.brand }}>{Money.format(l.owedTo(me, null))}</Text>
        </Card>
        <Card style={{ flex: 1 }}>
          <Muted>Bạn đang nợ</Muted>
          <Text style={{ fontSize: 20, fontWeight: '800', color: colors.owe }}>{Money.format(l.owedBy(me, null))}</Text>
        </Card>
      </View>
      <View style={{ flexDirection: 'row', gap: 12 }}>
        <Button style={{ flex: 1 }} title="Tạo nhóm" onPress={() => router.push('/create-group')} />
        <Button style={{ flex: 1 }} kind="outline" title="Vào nhóm bằng mã" onPress={() => setJoining(true)} />
      </View>
      <Rows rows={rows} empty="Bạn chưa ở nhóm nào. Tạo nhóm mới hoặc nhập mã mời." />

      <Modal transparent animationType="fade" visible={joining} onRequestClose={() => setJoining(false)}>
        <View style={{ flex: 1, backgroundColor: '#0008', justifyContent: 'center', padding: 24 }}>
          <Card style={{ gap: 12 }}>
            <Text style={{ fontSize: 17, fontWeight: '700' }}>Vào nhóm bằng mã mời</Text>
            <Muted>Người trong nhóm gửi mã cho bạn.</Muted>
            <Field placeholder="vd DAL-AT7" value={code} onChangeText={setCode} autoCapitalize="characters" autoCorrect={false} />
            <View style={{ flexDirection: 'row', gap: 12, justifyContent: 'flex-end' }}>
              <Button kind="text" title="Huỷ" onPress={() => setJoining(false)} />
              <Button title="Vào nhóm" onPress={join} />
            </View>
          </Card>
        </View>
      </Modal>
    </Screen>
  );
}

function hash(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0;
  return h;
}

