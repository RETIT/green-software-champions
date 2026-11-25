const EMPTY = "—";

/** English like the pages, which Format.java formats the same way. */
const LOCALE = "en-US";

function formatNumber(value: number, digits: number): string {
  return value.toLocaleString(LOCALE, {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
    signDisplay: "negative",
  });
}

export function formatCo2(kg: number | undefined): string {
  if (kg === undefined) return EMPTY;
  if (Math.abs(kg) >= 999.5) return `${formatNumber(kg / 1_000, 2)} t`;
  if (Math.abs(kg) < 9.95) return `${formatNumber(kg, 1)} kg`;
  return `${formatNumber(kg, 0)} kg`;
}

/** Formats a YYYY-MM period, e.g. "Mar 26" (short) or "March 2026" (long). */
export function formatPeriod(
  period: string,
  style: "short" | "long" = "short",
): string {
  const [year, month] = period.split("-").map(Number);
  return new Date(year, month - 1).toLocaleString(LOCALE, {
    month: style,
    year: style === "short" ? "2-digit" : "numeric",
  });
}
