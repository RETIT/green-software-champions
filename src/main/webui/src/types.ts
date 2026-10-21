export type AchievementType =
  | "FIRST_NET_ZERO"
  | "HIGHEST_REDUCTION"
  | "CONSECUTIVE_CHAMPION"
  | "GREEN_STREAK"
  | "LEADERBOARD_LEADER"
  | "BIG_SAVER"
  | "EFFICIENCY_EXPERT"
  | "CUSTOM";

export interface Achievement {
  id: number;
  title: string;
  description: string | null;
  badgeIcon: string | null;
  /** ISO-8601 timestamp */
  earnedAt: string;
  type: AchievementType | null;
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
}

export interface LeaderboardSummary {
  period: string;
  totalCo2Kg: number;
  previousTotalCo2Kg: number | null;
  reductionPercent: number | null;
  teamCount: number;
}
