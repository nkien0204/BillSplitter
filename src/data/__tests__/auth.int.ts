import { Device, errorOf, randomPhone } from './harness';

const devices: Device[] = [];
const device = () => {
  const d = new Device();
  devices.push(d);
  return d;
};
afterAll(() => Promise.all(devices.map((d) => d.close())));

describe('FirebaseAuthRepository', () => {
  it('đăng ký, đăng xuất, đăng nhập lại bằng nhiều cách gõ số điện thoại', async () => {
    const d = device();
    const phone = randomPhone();
    const u = await d.auth.register('  Đạt ', phone, 'matkhau123');
    expect(u.name).toBe('Đạt');
    expect(u.phone).toBe(phone);

    await d.auth.logout();
    const spaced = `${phone.slice(0, 4)} ${phone.slice(4, 7)} ${phone.slice(7)}`;
    const again = await d.auth.login(spaced, 'matkhau123');
    expect(again.id).toBe(u.id);
    expect(again.name).toBe('Đạt');
    const intl = '+84' + phone.slice(1);
    await d.auth.logout();
    expect((await d.auth.login(intl, 'matkhau123')).id).toBe(u.id);
  });

  it('từ chối số đã đăng ký, số sai định dạng, mật khẩu yếu, sai mật khẩu', async () => {
    const d1 = device();
    const phone = randomPhone();
    await d1.auth.register('Minh', phone, 'matkhau123');

    const d2 = device();
    expect((await errorOf(d2.auth.register('Trùng', phone, 'matkhau123')))?.message).toBe('USER_ALREADY_EXISTS');
    expect((await errorOf(d2.auth.register('Sai', '12345', 'matkhau123')))?.message).toBe('INVALID_PHONE');
    expect((await errorOf(d2.auth.register('Yếu', randomPhone(), '123')))?.message).toBe('WEAK_PASSWORD');
    expect((await errorOf(d2.auth.register('  ', randomPhone(), 'matkhau123')))?.message).toBe('BAD_REQUEST');
    expect((await errorOf(d2.auth.login(phone, 'saimatkhau')))?.message).toBe('INVALID_CREDENTIALS');
    expect((await errorOf(d2.auth.login(randomPhone(), 'matkhau123')))?.message).toBe('INVALID_CREDENTIALS');
  });

  it('phát lại trạng thái đăng nhập', async () => {
    const d = device();
    const seen: (string | null)[] = [];
    const off = d.auth.onAuthStateChanged((id) => seen.push(id));
    const u = await d.auth.register('Lan', randomPhone(), 'matkhau123');
    await d.auth.logout();
    off();
    expect(seen).toContain(u.id);
    expect(seen[seen.length - 1]).toBeNull();
  });
});
