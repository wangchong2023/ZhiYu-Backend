const pad = (n: number) => String(n).padStart(2, '0');

/** 将 ISO UTC 时间字符串格式化为本地时间 (yyyy-MM-dd HH:mm) */
export function formatBuildTime(isoString: string): string {
  try {
    const d = new Date(isoString);
    if (isNaN(d.getTime())) return isoString;
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  } catch {
    return isoString;
  }
}
