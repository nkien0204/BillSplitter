import type { DebtStatus } from '@/domain';

/** Bảng màu giữ nguyên từ colors.xml của bản Android. */
export const colors = {
  ink: '#1C1B19',
  muted: '#5E5A52',
  paper: '#F4F1EA',
  surface: '#FFFFFF',
  line: '#E3DDD0',
  lineStrong: '#D6CFBF',
  sand: '#EFEADF',
  brand: '#0B6B4A',
  brandDark: '#08553A',
  brandOnDark: '#8FD9B5',
  owe: '#B4461A',
  oweOnDark: '#F5B08E',
  onDarkMuted: '#CFC8B8',
  okBg: '#E3F0E8',
  okFg: '#0B5A3F',
  warnBg: '#FBEFD2',
  warnFg: '#6E4700',
  errBg: '#FBE3E3',
  errFg: '#7A1616',
  oweBg: '#F8E6DC',
  oweFg: '#8E3510',
};

export const AVATAR = ['#2F5D8A', '#9A4E1D', '#6B4C9A', '#5E6B2F', '#8A2E4F', '#2E6F6A'];

export function avatarColor(index: number): string {
  return AVATAR[((index % AVATAR.length) + AVATAR.length) % AVATAR.length];
}

export interface PillColors {
  bg: string;
  fg: string;
}

export const PILL_OK: PillColors = { bg: colors.okBg, fg: colors.okFg };
export const PILL_OWE: PillColors = { bg: colors.oweBg, fg: colors.oweFg };
export const PILL_WARN: PillColors = { bg: colors.warnBg, fg: colors.warnFg };
export const PILL_ERR: PillColors = { bg: colors.errBg, fg: colors.errFg };
export const PILL_NEUTRAL: PillColors = { bg: colors.sand, fg: colors.muted };

export function statusLabel(s: DebtStatus): string {
  switch (s) {
    case 'MARKED_PAID':
      return 'Chờ xác nhận';
    case 'CONFIRMED':
      return 'Đã thanh toán';
    case 'DISPUTED':
      return 'Chưa nhận được';
    default:
      return 'Chưa trả';
  }
}

/** {nền, chữ} của nhãn trạng thái. */
export function statusColors(s: DebtStatus): PillColors {
  switch (s) {
    case 'MARKED_PAID':
      return PILL_WARN;
    case 'CONFIRMED':
      return PILL_OK;
    case 'DISPUTED':
      return PILL_ERR;
    default:
      return PILL_OWE;
  }
}
