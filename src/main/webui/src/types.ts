export interface Achievement {
  id: number;
  title: string;
  description: string | null;
  badgeIcon: string | null;
  /** ISO-8601 timestamp */
  earnedAt: string;
  type: string | null;
}

/** CO₂e of one period; all values are null when there is no data for it. */
export interface HistoryPoint {
  /** YYYY-MM */
  period: string;
  totalCo2Kg: number | null;
  awsCo2Kg: number | null;
  azureCo2Kg: number | null;
  gcpCo2Kg: number | null;
}

export interface LeaderboardEntry {
  teamId: number;
  teamName: string;
  teamColor: string;
  teamIcon: string | null;
  rank: number;
  period: string;
  totalCo2Kg: number | null;
  previousCo2Kg: number | null;
  reductionPercent: number | null;
  awsCo2Kg: number | null;
  azureCo2Kg: number | null;
  gcpCo2Kg: number | null;
  companySharePercent: number | null;
  achievements: Achievement[];
  /** Periods up to and including `period`, oldest first. */
  history: HistoryPoint[];
}

export interface LeaderboardSummary {
  period: string;
  totalCo2Kg: number;
  previousTotalCo2Kg: number | null;
  reductionPercent: number | null;
  teamCount: number;
  /** Periods up to and including `period`, oldest first. */
  history: HistoryPoint[];
}
