const EMPTY = "—";

export type Trend = "positive" | "negative" | "neutral";

type MaybeNumber = number | null | undefined;

function isFiniteNumber(value: MaybeNumber): value is number {
  return Number.isFinite(value);
}

function formatNumber(value: number, digits: number): string {
  return value.toLocaleString(undefined, {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
    signDisplay: "negative",
  });
}

export function formatCo2(kg: MaybeNumber): string {
  if (!isFiniteNumber(kg)) return EMPTY;
  if (Math.abs(kg) >= 999.5) return `${formatNumber(kg / 1_000, 2)} t`;
  if (Math.abs(kg) < 9.95) return `${formatNumber(kg, 1)} kg`;
  return `${formatNumber(kg, 0)} kg`;
}

export function reductionTrend(percent: MaybeNumber): Trend {
  if (!isFiniteNumber(percent)) return "neutral";
  const rounded = Number(percent.toFixed(1));
  if (rounded === 0) return "neutral";
  return rounded > 0 ? "positive" : "negative";
}

const TREND_ARROW: Record<Trend, string> = {
  positive: "↓ ",
  negative: "↑ ",
  neutral: "",
};

export function formatReductionPercent(percent: MaybeNumber): string {
  if (!isFiniteNumber(percent)) return EMPTY;
  return `${TREND_ARROW[reductionTrend(percent)]}${formatPercent(Math.abs(percent))}`;
}

/** Formats a YYYY-MM period, e.g. "Mar 26" (short) or "March 2026" (long). */
export function formatPeriod(
  period: string,
  style: "short" | "long" = "short",
): string {
  if (!/^\d{4}-\d{2}$/.test(period)) return EMPTY;
  const [year, month] = period.split("-").map(Number);
  return new Date(year, month - 1).toLocaleString(undefined, {
    month: style,
    year: style === "short" ? "2-digit" : "numeric",
  });
}

export function formatPercent(percent: MaybeNumber, digits = 1): string {
  if (!isFiniteNumber(percent)) return EMPTY;
  return `${formatNumber(percent, digits)}%`;
}
