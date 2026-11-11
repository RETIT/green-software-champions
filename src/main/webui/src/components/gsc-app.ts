import { Task } from "@lit/task";
import { LitElement, css, html, nothing } from "lit";
import { customElement, property, state } from "lit/decorators.js";
import { classMap } from "lit/directives/class-map.js";
import { live } from "lit/directives/live.js";
import type {
  Achievement,
  HistoryPoint,
  LeaderboardEntry,
  LeaderboardSummary,
} from "../types.ts";
import {
  formatCo2,
  formatPeriod,
  formatReductionPercent,
  reductionTrend,
  type Trend,
} from "../util/format.ts";
import { daysInPeriod, periodWindow, shiftPeriod } from "../util/period.ts";
import "./gsc-leaderboard.ts";
import "./gsc-sparkline.ts";
import "./gsc-world-map.ts";

type View = "leaderboard" | "map";

interface LeaderboardData {
  leaderboard: LeaderboardEntry[];
  summary: LeaderboardSummary;
  /** Set when the API failed and demo data is shown instead. */
  error: string | null;
}

/** Length of the demo data history, matching the backend default. */
const HISTORY_MONTHS = 13;

const TREND_ICON: Record<Trend, string> = {
  positive: "📉",
  negative: "📈",
  neutral: "➖",
};

/**
 * Root application shell — styled to RETIT CI.
 */
@customElement("gsc-app")
export class GscApp extends LitElement {
  /**
   * The selected period. Empty until the user picks one, which lets the backend
   * choose the latest period every team has data for.
   */
  @property({ type: String }) period = "";
  @state() private _activeView: View = "leaderboard";

  /**
   * Reruns whenever `period` changes. A new run aborts the in-flight fetches
   * of the previous one, and results of superseded runs are discarded.
   */
  private _dataTask = new Task(this, {
    task: async ([period], { signal }): Promise<LeaderboardData> => {
      const query = period ? `?period=${encodeURIComponent(period)}` : "";
      try {
        const [lbRes, sumRes] = await Promise.all([
          fetch(`/api/leaderboard${query}`, { signal }),
          fetch(`/api/leaderboard/summary${query}`, { signal }),
        ]);
        if (!lbRes.ok)
          throw new Error(`Leaderboard fetch failed: ${lbRes.status}`);
        if (!sumRes.ok)
          throw new Error(`Summary fetch failed: ${sumRes.status}`);
        return {
          leaderboard: await lbRes.json(),
          summary: await sumRes.json(),
          error: null,
        };
      } catch (err) {
        signal.throwIfAborted();
        return {
          ...this._demoData(period || this._fallbackPeriod()),
          error:
            (err instanceof Error && err.message) ||
            "Failed to load leaderboard data.",
        };
      }
    },
    args: () => [this.period] as const,
  });

  /** The period shown: the selected one, or else the one the backend chose. */
  private get _shownPeriod(): string {
    return this.period || this._dataTask.value?.summary.period || "";
  }

  /**
   * The previous month, the latest one the scheduled sync can fetch. Used for
   * the demo data when the backend could not choose a period.
   */
  private _fallbackPeriod(): string {
    const d = new Date();
    const current = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
    return shiftPeriod(current, -1);
  }

  private _demoData(
    period: string,
  ): Pick<LeaderboardData, "leaderboard" | "summary"> {
    const teams = [
      { name: "Team Alpha", color: "#75b729", icon: "🌱" },
      { name: "Team Beta", color: "#3b8ecf", icon: "💧" },
      { name: "Team Gamma", color: "#e09c1a", icon: "⚡" },
      { name: "Team Delta", color: "#8b5cf6", icon: "🚀" },
    ];
    const baseCo2 = [1240, 2890, 3450, 980];
    // One period more than charted, so the oldest charted one has a predecessor
    const periods = periodWindow(period, HISTORY_MONTHS + 1);
    const previousPeriod = periods[periods.length - 2];
    // Title and badge icon of each achievement type, as the backend grants them
    const achievementKinds: Record<string, [string, string]> = {
      HIGHEST_REDUCTION: ["Reduction Champion", "🏆"],
      MOST_CO2_SAVED: ["Most CO₂e Saved", "💪"],
      GREEN_STREAK: ["Green Streak", "🌱"],
    };
    const achievement = (
      id: number,
      type: string,
      awardPeriod: string,
      threshold: number | null = null,
      tier: number | null = null,
    ): Achievement => ({
      id,
      title: achievementKinds[type][0],
      description: null,
      criteria: null,
      badgeIcon: achievementKinds[type][1],
      period: awardPeriod,
      type,
      threshold,
      tier,
      measuredValue: null,
    });
    // Like the backend, compare daily averages so months of different length
    // are comparable
    const reduction = (previous: number, current: number) => {
      const previousPerDay = previous / daysInPeriod(previousPeriod);
      const currentPerDay = current / daysInPeriod(period);
      return ((previousPerDay - currentPerDay) / previousPerDay) * 100;
    };
    const ranked = teams
      .map((t, i): LeaderboardEntry => {
        // Random walk back from the current period: a random average monthly
        // reduction (negative = growing) plus random month-to-month noise
        const monthlyReduction = Math.random() * 0.06 - 0.02;
        const totals = [baseCo2[i] * (0.9 + Math.random() * 0.2)];
        for (let p = 1; p < periods.length; p++) {
          const noise = Math.random() * 0.16 - 0.08;
          totals.unshift(totals[0] * (1 + monthlyReduction + noise));
        }
        const history = periods.slice(1).map((p, j): HistoryPoint => ({
          period: p,
          totalCo2Kg: totals[j + 1],
          awsCo2Kg: totals[j + 1] * 0.4,
          azureCo2Kg: totals[j + 1] * 0.35,
          gcpCo2Kg: totals[j + 1] * 0.25,
        }));
        const current = totals[totals.length - 1];
        const previous = totals[totals.length - 2];
        return {
          teamId: i + 1,
          teamName: t.name,
          teamColor: t.color,
          teamIcon: t.icon,
          rank: 0,
          period,
          totalCo2Kg: current,
          previousCo2Kg: previous,
          reductionPercent: reduction(previous, current),
          awsCo2Kg: current * 0.4,
          azureCo2Kg: current * 0.35,
          gcpCo2Kg: current * 0.25,
          companySharePercent: 0,
          history,
          // Latest first, like the API
          achievements:
            i === 0
              ? [
                  achievement(1, "HIGHEST_REDUCTION", period),
                  achievement(2, "GREEN_STREAK", period, 6, 2),
                  achievement(3, "HIGHEST_REDUCTION", previousPeriod),
                  achievement(5, "GREEN_STREAK", periods[periods.length - 4], 3, 1),
                ]
              : i === 3
                ? [achievement(4, "MOST_CO2_SAVED", period)]
                : [],
        };
      })
      .sort(
        (a, b) =>
          (a.totalCo2Kg ?? Number.MAX_VALUE) -
          (b.totalCo2Kg ?? Number.MAX_VALUE),
      )
      .map((t, i) => ({ ...t, rank: i + 1 }));

    const total = ranked.reduce((s, t) => s + (t.totalCo2Kg || 0), 0);
    const prevTotal = ranked.reduce((s, t) => s + (t.previousCo2Kg || 0), 0);
    return {
      leaderboard: ranked.map((t) => ({
        ...t,
        companySharePercent:
          total > 0 && t.totalCo2Kg !== null
            ? (t.totalCo2Kg / total) * 100
            : null,
      })),
      summary: {
        period,
        totalCo2Kg: total,
        previousTotalCo2Kg: prevTotal,
        reductionPercent: prevTotal > 0 ? reduction(prevTotal, total) : null,
        teamCount: teams.length,
        history: ranked[0].history.map((point, j) => {
          const sum = (key: keyof Omit<HistoryPoint, "period">) =>
            ranked.reduce((acc, t) => acc + (t.history[j][key] ?? 0), 0);
          return {
            period: point.period,
            totalCo2Kg: sum("totalCo2Kg"),
            awsCo2Kg: sum("awsCo2Kg"),
            azureCo2Kg: sum("azureCo2Kg"),
            gcpCo2Kg: sum("gcpCo2Kg"),
          };
        }),
      },
    };
  }

  private _onPeriodChange(e: Event) {
    const input = e.target as HTMLInputElement;
    // A cleared month input yields "", which is not a valid period. The
    // period itself is unchanged, so re-render to let live() restore it.
    if (!input.value) {
      this.requestUpdate();
      return;
    }
    // Setting the period reruns the data task.
    this.period = input.value;
  }

  private _renderContent({ leaderboard, summary: s, error }: LeaderboardData) {
    const trend = reductionTrend(s.reductionPercent);
    const history = s.history || [];
    // Matches when the sparkline draws a line, see gsc-sparkline
    const hasTrend = history.filter((p) => p.totalCo2Kg !== null).length >= 2;

    return html`
      ${
        error
          ? html`
              <div class="error-msg">
                <span>⚠️</span>
                <span>${error} — Showing demo data.</span>
              </div>
            `
          : nothing
      }
      <div class="summary-bar">
        <div class="summary-card">
          <div class="summary-card-icon">🌫️</div>
          <div class="summary-card-body">
            <div class="value">${formatCo2(s.totalCo2Kg)}</div>
            <div class="label">
              Company CO₂e in ${formatPeriod(s.period, "long")}
            </div>
          </div>
        </div>
        <div class="summary-card">
          <div class="summary-card-icon">📊</div>
          <div class="summary-card-body">
            ${
              hasTrend
                ? html`<gsc-sparkline
                    class="trend"
                    .points="${history.map((p) => ({
                      label: formatPeriod(p.period, "long"),
                      value: p.totalCo2Kg,
                      days: daysInPeriod(p.period),
                    }))}"
                    label="Company CO₂e over the last ${history.length} months"
                  ></gsc-sparkline>`
                : html`<div class="value">—</div>`
            }
            <div class="label">
              ${hasTrend ? `${history.length}-Month Trend` : "Trend"}
            </div>
          </div>
        </div>
        <div class="summary-card">
          <div class="summary-card-icon">${TREND_ICON[trend]}</div>
          <div class="summary-card-body">
            <div class="value ${trend}">
              ${formatReductionPercent(s.reductionPercent)}
            </div>
            <div class="label">
              vs. ${formatPeriod(shiftPeriod(s.period, -1), "long")}
            </div>
          </div>
        </div>
      </div>
      ${
        this._activeView === "leaderboard"
          ? html`
              <gsc-leaderboard .entries="${leaderboard}" .period="${s.period}">
              </gsc-leaderboard>
            `
          : html`
              <gsc-world-map .entries="${leaderboard}" .period="${s.period}">
              </gsc-world-map>
            `
      }
    `;
  }

  override render() {
    return html`
      <!-- Navigation Bar -->
      <header>
        <div class="logo">
          <span class="logo-icon">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              width="105px"
              height="32px"
              class="logo-mark"
              viewBox="0 0 548.46 167.22"
              role="img"
              aria-label="RETIT"
              focusable="false"
            >
              <path
                fill="#a7a9ac"
                stroke="#a7a9ac"
                stroke-width="15"
                stroke-linejoin="miter"
                stroke-miterlimit="10"
                d="M494.23 159.6H468.89V31.19l-90.56-.18V7.54l162.5.17V31.19h-46.6V159.6zM405.93 159.19H380.58V57.51h25.35V73.23v85.96z"
              ></path>
              <path
                fill="#71bf44"
                stroke="#71bf44"
                stroke-width="15"
                stroke-linejoin="miter"
                stroke-miterlimit="10"
                d="M31.12 75.09H60.49C69.01 75.09 75.34 73.01 79.5 68.85 83.66 64.7 85.74 58.99 85.74 51.72c0-6.68-2.05-12.07-6.14-16.19C75.5 31.41 69.77 29.35 62.41 29.35H31.12V75.09zm118.02 84.19H124.18L64.34 97.01H31.12v62.42H7.5V7.54H64.34c15.8.0 27.42 4.14 34.84 12.41C106.61 28.22 110.32 38.92 110.32 52.05 110.32 71.56 102.45 84.99 86.7 92.33l62.44 66.95zM233.15 159.43H148V7.54h84.29V30.13H171.62V71.3h48.76V93.33H171.62v42.51h61.53v23.59zm86.31.0H294.11V31.02H186.63V7.54H366.31V31.02H319.46V159.43z"
              ></path>
            </svg>
          </span>
          <span class="logo-text">
            <span class="logo-sep">|</span>
            <span class="logo-sub">Green Software Champions</span>
          </span>
        </div>

        <nav>
          <button
            class="nav-btn ${classMap({
              active: this._activeView === "leaderboard",
            })}"
            @click="${() => {
              this._activeView = "leaderboard";
            }}"
          >
            🏆 Leaderboard
          </button>
          <button
            class="nav-btn ${classMap({ active: this._activeView === "map" })}"
            @click="${() => {
              this._activeView = "map";
            }}"
          >
            🗺️ World Map
          </button>
        </nav>

        <div class="period-selector">
          <label for="period">Period</label>
          <input
            type="month"
            id="period"
            .value="${live(this._shownPeriod)}"
            @change="${this._onPeriodChange}"
          />
        </div>
      </header>

      <!-- Hero -->
      <div class="hero">
        <div class="hero-inner">
          <div class="hero-text">
            <h1>
              We track cloud carbon footprints,<br />
              <span>across all your teams</span>
            </h1>
            <p>
              Compete, reduce and champion sustainable software engineering with
              RETIT Green Software Champions.
            </p>
          </div>
        </div>
      </div>

      <main>
        ${this._dataTask.render({
          pending: () => html`
            <div class="loading">
              <div class="spinner"></div>
              <p>Loading carbon data…</p>
            </div>
          `,
          complete: (data) => this._renderContent(data),
          error: (err) => html`
            <div class="error-msg">
              <span>⚠️</span>
              <span>
                ${
                  (err instanceof Error && err.message) ||
                  "Failed to load leaderboard data."
                }
              </span>
            </div>
          `,
        })}
      </main>

      <footer>
        Powered by
        <a href="https://www.retit.io" target="_blank" rel="noopener"
          >RETIT GmbH</a
        >
        &nbsp;·&nbsp; Green Software Champions &nbsp;·&nbsp; Cloud Carbon
        Footprint Tracking
      </footer>
    `;
  }

  static override styles = css`
    :host {
      display: block;
      min-height: 100vh;
      background: var(--retit-bg);
      font-family: var(--retit-font);
      font-weight: 300;
      color: var(--retit-text);
    }

    /* ── Navigation bar (RETIT green) ──────────────────── */
    header {
      background: var(--retit-dark);
      color: var(--retit-white);
      height: 3.25em;
      line-height: 3.25em;
      position: sticky;
      top: 0;
      z-index: 1000;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 1.5em;
      box-shadow: var(--retit-shadow-raised);
    }

    /* Logo */
    .logo {
      display: flex;
      align-items: center;
      gap: 0.6em;
      text-decoration: none;
      color: var(--retit-white);
    }

    .logo-icon {
      font-size: 1.4em;
      line-height: 1;
    }

    .logo-text {
      font-size: 1em;
      font-weight: 600;
      color: var(--retit-white);
      letter-spacing: 0.01em;
      line-height: 1;
    }

    .logo-sep {
      color: var(--retit-text-light);
      font-weight: 300;
      margin: 0 0.3em;
    }

    .logo-sub {
      color: var(--retit-on-dark-text-muted);
      font-weight: 300;
    }

    /* Nav buttons */
    nav {
      display: flex;
      gap: 0.25em;
      height: inherit;
      align-items: center;
    }

    .nav-btn {
      background: transparent;
      border: none;
      color: var(--retit-white);
      padding: 0 1em;
      height: 2em;
      line-height: 1.95em;
      border-radius: 4px;
      cursor: pointer;
      font-size: 0.85em;
      font-weight: 400;
      font-family: inherit;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      transition: background-color 0.2s ease;
      white-space: nowrap;
    }

    .nav-btn:hover {
      background-color: var(--retit-on-dark-fill);
    }

    .nav-btn.active {
      background-color: var(--retit-on-dark-fill);
      box-shadow: inset 0 0 0 1px var(--retit-on-dark-border);
    }

    /* Period selector */
    .period-selector {
      display: flex;
      align-items: center;
      gap: 0.5em;
    }

    .period-selector label {
      font-size: 0.75em;
      font-weight: 400;
      color: var(--retit-on-dark-text-muted);
      text-transform: uppercase;
      letter-spacing: 0.06em;
    }

    .period-selector input[type="month"] {
      background: var(--retit-on-dark-fill);
      border: 1px solid var(--retit-on-dark-border);
      color: var(--retit-white);
      padding: 0.2em 0.5em;
      border-radius: 4px;
      font-size: 0.8em;
      font-family: inherit;
      outline: none;
      cursor: pointer;
      transition: background 0.15s;
    }

    .period-selector input[type="month"]:hover,
    .period-selector input[type="month"]:focus {
      background: color-mix(
        in srgb,
        var(--retit-white) 15%,
        var(--retit-on-dark-fill)
      );
    }

    /* ── Hero section ───────────────────────────────────── */
    .hero {
      background: var(--retit-bg);
      padding: 3em 2em 2em;
      text-align: left;
      border-bottom: 1px solid var(--retit-border);
    }

    .hero-inner {
      max-width: 1200px;
      margin: 0 auto;
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 2em;
    }

    .hero-text h1 {
      font-size: 1.8em;
      font-weight: 400;
      color: var(--retit-green-dark);
      line-height: 1.25em;
      margin: 0 0 0.4em;
    }

    .hero-text h1 span {
      color: var(--retit-green);
    }

    .hero-text p {
      font-size: 0.95em;
      color: var(--retit-text-light);
      font-weight: 300;
      margin: 0;
    }

    /* ── Main content ───────────────────────────────────── */
    main {
      max-width: 1200px;
      margin: 0 auto;
      padding: 2em 2em 4em;
    }

    /* ── Summary bar ────────────────────────────────────── */
    .summary-bar {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 1.25em;
      margin-bottom: 2em;
    }

    .summary-card {
      background: var(--retit-surface);
      border-radius: 3px;
      box-shadow: var(--retit-shadow);
      padding: 1.25em 1.5em;
      display: flex;
      align-items: flex-start;
      gap: 1em;
    }

    .summary-card-icon {
      font-size: 1.4em;
      line-height: 1;
      flex-shrink: 0;
      margin-top: 0.15em;
    }

    .summary-card-body {
      flex: 1;
      min-width: 0;
    }

    .summary-card .value {
      font-size: 1.6em;
      font-weight: 700;
      color: var(--retit-text);
      line-height: 1;
      letter-spacing: -0.01em;
    }

    .summary-card .trend {
      width: 100%;
      height: 2.5em;
      --sparkline-color: var(--retit-text-light);
      --sparkline-marker-color: var(--retit-green);
    }

    .summary-card .value.positive {
      color: var(--retit-green-dark);
    }
    .summary-card .value.negative {
      color: var(--retit-red);
    }

    .summary-card .label {
      font-size: 0.72em;
      font-weight: 400;
      color: var(--retit-text-light);
      margin-top: 0.35em;
      text-transform: uppercase;
      letter-spacing: 0.07em;
    }

    /* ── Loading ────────────────────────────────────────── */
    .loading {
      text-align: center;
      padding: 5em 2em;
      color: var(--retit-text-light);
    }

    .spinner {
      width: 40px;
      height: 40px;
      border: 3px solid
        color-mix(in srgb, var(--retit-green) 20%, var(--retit-bg));
      border-top-color: var(--retit-green);
      border-radius: 50%;
      animation: spin 0.75s linear infinite;
      margin: 0 auto 1em;
    }

    .loading p {
      font-size: 0.9em;
      font-weight: 400;
    }

    @keyframes spin {
      to {
        transform: rotate(360deg);
      }
    }

    /* ── Error ──────────────────────────────────────────── */
    .error-msg {
      background: color-mix(in srgb, var(--retit-red) 8%, var(--retit-surface));
      border: 1px solid
        color-mix(in srgb, var(--retit-red) 30%, var(--retit-surface));
      border-radius: 3px;
      padding: 0.75em 1.25em;
      color: var(--retit-red);
      font-size: 0.875em;
      font-weight: 400;
      display: flex;
      align-items: center;
      gap: 0.5em;
      margin-bottom: 1.5em;
    }

    /* ── Footer ─────────────────────────────────────────── */
    footer {
      background: var(--retit-dark);
      color: var(--retit-on-dark-text-muted);
      text-align: center;
      padding: 2.5em 2em;
      font-size: 0.85em;
      font-weight: 300;
      margin-top: auto;
    }

    footer a {
      color: var(--retit-green-light);
      text-decoration: none;
      border-bottom: 1px dotted
        color-mix(in srgb, var(--retit-green-light) 40%, var(--retit-dark));
      transition: color 0.2s;
    }

    footer a:hover {
      color: var(--retit-green);
      border-bottom-color: transparent;
    }
  `;
}

declare global {
  interface HTMLElementTagNameMap {
    "gsc-app": GscApp;
  }
}
