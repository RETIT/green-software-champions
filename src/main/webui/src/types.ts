export interface Achievement {
  id: number;
  title: string;
  /** What it was awarded for, quoting the value, e.g. the reduction in percent. */
  description: string | null;
  /**
   * What achievements of its type are awarded for, the same for all of them; null
   * for custom achievements.
   */
  criteria: string | null;
  badgeIcon: string | null;
  /** YYYY-MM period the achievement was awarded for. */
  period: string;
  type: string | null;
  /** The threshold reached, for the types with thresholds, e.g. a streak's length. */
  threshold: number | null;
  /** The tier the threshold stands for, counting from 1, e.g. 3 for a 12-month streak. */
  tier: number | null;
  /** The value it was awarded for, e.g. the reduction in percent. */
  measuredValue: number | null;
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

/** The logged-in user. */
export interface User {
  /** Null if nobody is logged in, which only happens in dev mode. */
  name: string | null;
  /** The roles of this application: "user", "admin". */
  roles: string[];
}
