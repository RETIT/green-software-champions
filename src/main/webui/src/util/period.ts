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
