import { Allocator } from '../split/Allocator';
import { JavaRandom } from './javaRandom';

describe('Allocator', () => {
  it('chia đều, phần dư về người đầu', () => {
    expect(Allocator.equal(100000, 3)).toEqual([33334, 33333, 33333]);
  });

  it('theo trọng số lấy phần dư lớn nhất', () => {
    expect(Allocator.allocate(100, [3, 1, 2])).toEqual([50, 17, 33]);
  });

  it('trọng số bằng không không nhận tiền', () => {
    expect(Allocator.allocate(10, [0, 5, 0])).toEqual([0, 10, 0]);
  });

  it('tổng bằng không hoặc trọng số rỗng', () => {
    expect(Allocator.allocate(0, [1, 1])).toEqual([0, 0]);
    expect(Allocator.allocate(99, [0, 0])).toEqual([0, 0]);
  });

  it('từ chối số âm', () => {
    expect(() => Allocator.allocate(-1, [1])).toThrow();
    expect(() => Allocator.allocate(1, [-1])).toThrow();
  });

  it('chính xác cả khi tích total × weight vượt 2^53', () => {
    const total = 900_000_000_000;
    const out = Allocator.allocate(total, [3_000_000_000, 3_000_000_000, 3_000_000_000]);
    expect(out.reduce((a, b) => a + b, 0)).toBe(total);
    expect(out).toEqual([300_000_000_000, 300_000_000_000, 300_000_000_000]);
  });

  it('property: tổng luôn khớp và tất định', () => {
    const r = new JavaRandom(42);
    for (let k = 0; k < 20000; k++) {
      const n = 1 + r.nextInt(15);
      const w: number[] = [];
      for (let i = 0; i < n; i++) w.push(r.nextInt(4) === 0 ? 0 : r.nextInt(2_000_000));
      const total = r.nextInt(50_000_000);
      const a = Allocator.allocate(total, w);
      const sumW = w.reduce((x, y) => x + y, 0);
      const sum = a.reduce((x, y) => x + y, 0);
      expect(sum).toBe(sumW === 0 ? 0 : total);
      expect(Allocator.allocate(total, w)).toEqual(a);
      for (let i = 0; i < n; i++) {
        if (w[i] === 0) expect(a[i]).toBe(0);
        // mỗi phần lệch khỏi tỉ lệ đúng không quá 1 đồng
        if (sumW > 0) expect(Math.abs(a[i] - (total * w[i]) / sumW)).toBeLessThan(1 + 1e-6);
      }
    }
  });
});
