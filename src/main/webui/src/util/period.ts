/** Shifts a YYYY-MM period by the given number of months. */
export function shiftPeriod(period: string, months: number): string {
  const [year, month] = period.split("-").map(Number);
  const d = new Date(year, month - 1 + months);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
}

/** The `months` consecutive periods ending at `endPeriod`, oldest first. */
export function periodWindow(endPeriod: string, months: number): string[] {
  return Array.from({ length: months }, (_, i) =>
    shiftPeriod(endPeriod, i - (months - 1)),
  );
}

/**
 * Month length that emissions are scaled to, so months of different length are
 * comparable. Cancels out in comparisons; only sets the magnitude of shown values.
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
