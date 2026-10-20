const EMPTY = "—";

function formatNumber(value, digits) {
  return value.toLocaleString(undefined, {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
    signDisplay: "negative",
  });
}

export function formatCo2(kg) {
  if (!Number.isFinite(kg)) return EMPTY;
  if (Math.abs(kg) >= 999.5) return `${formatNumber(kg / 1_000, 2)} t`;
  if (Math.abs(kg) < 9.95) return `${formatNumber(kg, 1)} kg`;
  return `${formatNumber(kg, 0)} kg`;
}

export function reductionTrend(percent) {
  if (!Number.isFinite(percent)) return "neutral";
  const rounded = Number(percent.toFixed(1));
  if (rounded === 0) return "neutral";
  return rounded > 0 ? "positive" : "negative";
}

const TREND_ARROW = { positive: "↓ ", negative: "↑ ", neutral: "" };

export function formatReductionPercent(percent) {
  if (!Number.isFinite(percent)) return EMPTY;
  return `${TREND_ARROW[reductionTrend(percent)]}${formatPercent(Math.abs(percent))}`;
}

export function formatPercent(percent, digits = 1) {
  if (!Number.isFinite(percent)) return EMPTY;
  return `${formatNumber(percent, digits)}%`;
}
