import {
  type Firestore,
  type Unsubscribe,
  collection,
  doc,
  limit,
  onSnapshot,
  orderBy,
  query,
  where,
} from 'firebase/firestore';
import { AppSnapshot } from './AppSnapshot';
import { billFrom, groupFrom, inboxFrom, statusFrom, userFrom } from './docs';
import type { BillRecord, DebtStatusRecord, GroupRecord, InboxRecord, UserRecord } from './records';

const INBOX_LIMIT = 200;

interface GroupWatch {
  bills?: BillRecord[];
  statuses?: DebtStatusRecord[];
  unsubs: Unsubscribe[];
}

/**
 * Nghe Firestore và gộp mọi nguồn thành một AppSnapshot:
 *   - tài khoản của mình và của mọi người từng ở các nhóm mình đang tham gia (từng tài liệu một: users không cho liệt kê)
 *   - nhóm mình đang là thành viên
 *   - hoá đơn và trạng thái khoản nợ của từng nhóm đó
 *   - hộp thư của mình
 * Chỉ phát snapshot khi mọi nguồn đã trả kết quả đầu tiên, để sổ nợ không bao giờ tính trên dữ liệu thiếu.
 */
export function watchSnapshot(
  db: Firestore,
  uid: string,
  emit: (s: AppSnapshot) => void,
  onError?: (e: Error) => void,
): Unsubscribe {
  let closed = false;
  let scheduled = false;

  const users = new Map<string, UserRecord | null>(); // null = đã hỏi nhưng không có / không đọc được
  const userUnsubs = new Map<string, Unsubscribe>();
  let groups: GroupRecord[] | undefined;
  const groupWatches = new Map<string, GroupWatch>();
  let inbox: InboxRecord[] | undefined;

  const report = (e: unknown) => {
    if (!closed && onError) onError(e instanceof Error ? e : new Error(String(e)));
  };
  const isDenied = (e: unknown) => (e as { code?: string })?.code === 'permission-denied';

  const neededUserIds = (): Set<string> => {
    const ids = new Set<string>([uid]);
    for (const g of groups ?? []) for (const id of g.memberOrder) ids.add(id);
    return ids;
  };

  const ready = (): boolean => {
    if (!groups || !inbox) return false;
    for (const g of groups) {
      const w = groupWatches.get(g.id);
      if (!w || !w.bills || !w.statuses) return false;
    }
    for (const id of neededUserIds()) if (!users.has(id)) return false;
    return true;
  };

  const flush = () => {
    scheduled = false;
    if (closed || !ready()) return;
    const bills: BillRecord[] = [];
    const statuses: DebtStatusRecord[] = [];
    for (const g of groups as GroupRecord[]) {
      const w = groupWatches.get(g.id) as GroupWatch;
      bills.push(...(w.bills as BillRecord[]));
      statuses.push(...(w.statuses as DebtStatusRecord[]));
    }
    const known = [...users.values()].filter((u): u is UserRecord => u != null);
    try {
      emit(new AppSnapshot(known, groups as GroupRecord[], bills, statuses, inbox as InboxRecord[]));
    } catch (e) {
      report(e);
    }
  };

  // Gom nhiều cập nhật liên tiếp thành một lần phát.
  const schedule = () => {
    if (scheduled || closed) return;
    scheduled = true;
    Promise.resolve().then(flush);
  };

  const syncUsers = () => {
    const need = neededUserIds();
    for (const id of need) {
      if (userUnsubs.has(id)) continue;
      userUnsubs.set(
        id,
        onSnapshot(
          doc(db, 'users', id),
          (s) => {
            users.set(id, s.exists() ? userFrom(s) : null);
            schedule();
          },
          (e) => {
            users.set(id, null);
            if (!isDenied(e)) report(e);
            schedule();
          },
        ),
      );
    }
    for (const [id, unsub] of userUnsubs) {
      if (need.has(id)) continue;
      unsub();
      userUnsubs.delete(id);
      users.delete(id);
    }
  };

  /**
   * Bills và debtStatuses của một nhóm được rules cho đọc khi mình là thành viên theo server.
   * Nên chỉ gắn khi tài liệu nhóm đã được server xác nhận (không còn ghi chờ), và thử lại nếu bị từ chối
   * trong lúc nhóm vẫn còn trong danh sách (trễ nhịp giữa hai truy vấn).
   */
  const MAX_RETRY = 5;
  const attachGroup = (groupId: string, attempt: number) => {
    const w: GroupWatch = { unsubs: [] };
    groupWatches.set(groupId, w);
    const onErr = (e: unknown) => {
      if (!isDenied(e)) return report(e);
      // Bị từ chối: hoặc mình vừa rời nhóm (danh sách nhóm sắp bỏ nhóm này), hoặc server chưa kịp thấy nhóm.
      w.unsubs.forEach((u) => u());
      if (groupWatches.get(groupId) === w) groupWatches.delete(groupId);
      if (!closed && attempt < MAX_RETRY) {
        setTimeout(() => {
          if (!closed && !groupWatches.has(groupId) && (groups ?? []).some((g) => g.id === groupId)) {
            attachGroup(groupId, attempt + 1);
          }
        }, 300 * (attempt + 1));
      }
    };
    w.unsubs.push(
      onSnapshot(
        query(collection(db, 'bills'), where('groupId', '==', groupId)),
        (s) => {
          w.bills = s.docs.map(billFrom);
          schedule();
        },
        onErr,
      ),
      onSnapshot(
        query(collection(db, 'debtStatuses'), where('groupId', '==', groupId)),
        (s) => {
          w.statuses = s.docs.map(statusFrom);
          schedule();
        },
        onErr,
      ),
    );
  };

  const syncGroups = (list: GroupRecord[], confirmed: ReadonlySet<string>) => {
    const ids = new Set(list.map((g) => g.id));
    for (const g of list) {
      if (!groupWatches.has(g.id) && confirmed.has(g.id)) attachGroup(g.id, 0);
    }
    for (const [id, w] of groupWatches) {
      if (ids.has(id)) continue;
      w.unsubs.forEach((u) => u());
      groupWatches.delete(id);
    }
  };

  const unsubGroups = onSnapshot(
    query(collection(db, 'groups'), where('activeMemberIds', 'array-contains', uid)),
    // includeMetadataChanges: cần biết lúc ghi chờ (hasPendingWrites) chuyển thành đã được server xác nhận.
    { includeMetadataChanges: true },
    (s) => {
      groups = s.docs.map(groupFrom);
      syncGroups(groups, new Set(s.docs.filter((d) => !d.metadata.hasPendingWrites).map((d) => d.id)));
      syncUsers();
      schedule();
    },
    report,
  );

  const unsubInbox = onSnapshot(
    query(
      collection(db, 'inbox'),
      where('recipientId', '==', uid),
      orderBy('createdAt', 'desc'),
      limit(INBOX_LIMIT),
    ),
    (s) => {
      inbox = s.docs.map(inboxFrom);
      schedule();
    },
    report,
  );

  syncUsers(); // tài khoản của chính mình, trước cả khi biết nhóm

  return () => {
    closed = true;
    unsubGroups();
    unsubInbox();
    userUnsubs.forEach((u) => u());
    groupWatches.forEach((w) => w.unsubs.forEach((u) => u()));
    userUnsubs.clear();
    groupWatches.clear();
  };
}
