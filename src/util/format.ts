export function day(millis: number): string {
  const d = new Date(millis);
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}`;
}

export function fullDate(millis: number): string {
  const d = new Date(millis);
  return `${day(millis)}/${d.getFullYear()}`;
}

export function ago(millis: number, now = Date.now()): string {
  const d = Math.max(0, now - millis) / 1000;
  if (d < 60) return 'vừa xong';
  if (d < 3600) return `${Math.floor(d / 60)} phút trước`;
  if (d < 86400) return `${Math.floor(d / 3600)} giờ trước`;
  if (d < 7 * 86400) return `${Math.floor(d / 86400)} ngày trước`;
  return day(millis);
}
