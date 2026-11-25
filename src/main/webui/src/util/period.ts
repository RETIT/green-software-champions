/**
 * Month length that emissions are scaled to, so months of different length are
 * comparable, as in Sparkline.java.
 */
export const NORMALIZED_DAYS = 30;

/** `kg` emitted over `days`, scaled to NORMALIZED_DAYS. */
export function normalizeToMonth(kg: number, days: number): number {
  return (kg / days) * NORMALIZED_DAYS;
}

/** The number of days in a YYYY-MM period. */
export function daysInPeriod(period: string): number {
  const [year, month] = period.split("-").map(Number);
  // Date months are 0-based: day 0 of the following month is the last of `period`
  return new Date(year, month, 0).getDate();
}
