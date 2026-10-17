import { BillSpec } from './BillSpec';
import type { Category } from './types';

/** Hoá đơn đã lưu: định danh + nội dung chia. createdAt là ngày chi (người dùng chọn được). */
export class Bill {
  readonly id: string;
  readonly groupId: string;
  readonly title: string;
  readonly createdAt: number;
  readonly createdBy: string | null;
  readonly spec: BillSpec;
  readonly category: Category;

  constructor(
    id: string,
    groupId: string,
    title: string | null | undefined,
    createdAt: number,
    createdBy: string | null | undefined,
    spec: BillSpec,
    category: Category | null | undefined = 'KHAC',
  ) {
    if (id == null) throw new Error('id is required');
    if (groupId == null) throw new Error('groupId is required');
    this.id = id;
    this.groupId = groupId;
    this.title = title ?? '';
    this.createdAt = createdAt;
    this.createdBy = createdBy ?? null;
    this.spec = spec;
    this.category = category ?? 'KHAC';
  }

  get payerId(): string {
    return this.spec.payerId;
  }
}
