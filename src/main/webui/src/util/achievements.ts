import type { Achievement } from "../types.ts";
import { formatCo2, formatPercent, formatPeriod } from "./format.ts";

/** The most achievements a tooltip lists, so it stays readable. */
const MAX_TOOLTIP_ACHIEVEMENTS = 12;

/**
 * Groups achievements of the same kind, so that one awarded several times can be
 * shown once with a count. Custom achievements are grouped by title and icon, as
 * they share a type. With achievements given latest first, as the API returns
 * them, each group is latest first and the most recently earned group comes first.
 */
export function groupAchievements(
  achievements: Achievement[],
): Achievement[][] {
  const groups = new Map<string, Achievement[]>();
  for (const a of achievements) {
    const key =
      a.type && a.type !== "CUSTOM"
        ? a.type
        : `CUSTOM|${a.badgeIcon}|${a.title}`;
    const group = groups.get(key);
    if (group) {
      group.push(a);
    } else {
      groups.set(key, [a]);
    }
  }
  return [...groups.values()];
}

const NUMERALS = ["I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"];

/** A tier as a Roman numeral, e.g. "III", or as a number beyond ten. */
function tierNumeral(tier: number): string {
  return NUMERALS[tier - 1] ?? String(tier);
}

/**
 * What a group's badge shows besides its title. For achievements with tiers, the
 * highest tier reached as a Roman numeral and how often it was reached, as the lower
 * tiers are earned on the way to it, e.g. a 12-month streak also earns tiers I and
 * II. Otherwise no tier and how often the achievement was awarded.
 */
export function groupBadge(group: Achievement[]): {
  tier: string | null;
  count: number;
} {
  const tiers = group.map((a) => a.tier ?? 0);
  const highest = Math.max(...tiers);
  return highest > 0
    ? {
        tier: tierNumeral(highest),
        count: tiers.filter((t) => t === highest).length,
      }
    : { tier: null, count: group.length };
}

/**
 * The title of a group, with its highest tier if it has tiers, and how often that
 * was reached or the achievement was awarded if more than once.
 */
export function groupLabel(group: Achievement[]): string {
  const { tier, count } = groupBadge(group);
  return [group[0].title, tier, count > 1 ? `×${count}` : null]
    .filter((part) => part !== null)
    .join(" ");
}

/**
 * The value an achievement was awarded for, in short, as its criteria already say
 * what it means, or null if it has none, e.g. one granted before values were stored.
 */
function awardValue(a: Achievement): string | null {
  const value = a.measuredValue;
  const hasValue = Number.isFinite(value);
  switch (a.type) {
    case "HIGHEST_REDUCTION":
    case "BIG_SAVER":
      return hasValue ? `${formatPercent(value)} less` : null;
    case "MOST_CO2_SAVED":
      return hasValue ? `${formatCo2(value)} saved` : null;
    case "PERSONAL_BEST":
      return hasValue ? `${formatPercent(value)} below the previous best` : null;
    case "ANNUAL_IMPROVER":
      return hasValue
        ? `${formatPercent(value)} less than ${Number(a.period.slice(0, 4)) - 1}`
        : null;
    case "GREEN_STREAK":
      return a.threshold !== null ? `${a.threshold} months` : null;
    default:
      return null;
  }
}

/**
 * What an achievement was awarded for: its tier, if it has one, and its value, or
 * its description if it has no value.
 */
function awardDetail(a: Achievement): string | null {
  const value = awardValue(a) ?? a.description;
  if (!a.tier) return value;
  const tier = tierNumeral(a.tier);
  return value ? `${tier} (${value})` : tier;
}

/** When an achievement was awarded: the year for yearly ones, otherwise the month. */
function awardPeriod(a: Achievement): string {
  return a.type === "ANNUAL_IMPROVER"
    ? a.period.slice(0, 4)
    : formatPeriod(a.period, "long");
}

/**
 * A tooltip for a group: its label and what its achievements are awarded for, then
 * when each one was awarded and its value. Custom achievements have no criteria, so
 * their description is shown once if they all share it, otherwise per achievement.
 */
export function groupTooltip(group: Achievement[]): string {
  const { criteria, description } = group[0];
  const sharesDescription = group.every((a) => a.description === description);
  const summary = criteria ?? (sharesDescription ? description : null);
  const lines = group.slice(0, MAX_TOOLTIP_ACHIEVEMENTS).map((a) => {
    const detail = criteria
      ? awardDetail(a)
      : sharesDescription
        ? null
        : a.description;
    return detail ? `${awardPeriod(a)}: ${detail}` : awardPeriod(a);
  });
  if (group.length > MAX_TOOLTIP_ACHIEVEMENTS) {
    lines.push(`… and ${group.length - MAX_TOOLTIP_ACHIEVEMENTS} earlier`);
  }
  const header = summary ? [groupLabel(group), summary, ""] : [groupLabel(group)];
  return [...header, ...lines].join("\n");
}
