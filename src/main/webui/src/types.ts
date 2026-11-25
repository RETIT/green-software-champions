/** CO₂e of one period; values are missing when there is no data for it. */
export interface HistoryPoint {
  /** YYYY-MM */
  period: string;
  totalCo2Kg?: number;
  awsCo2Kg?: number;
  azureCo2Kg?: number;
  gcpCo2Kg?: number;
}

/** A month of a sparkline, with its texts preformatted. */
export interface SparklinePoint {
  /** Position of the dot in percent, like `y`, which is missing without data. */
  x: number;
  y?: number;
  label: string;
  value: string;
  /** The value for a month of average length, if that is another one. */
  normalized?: string;
  /** The change from the previous month. */
  change?: string;
  trend?: "positive" | "negative" | "neutral";
}
