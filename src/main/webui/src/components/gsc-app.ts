import { Task } from "@lit/task";
import { LitElement, css, html } from "lit";
import { customElement, property } from "lit/decorators.js";
import type { LeaderboardEntry, LeaderboardSummary } from "../types.ts";
import {
  formatCo2,
  formatReductionPercent,
  reductionTrend,
  type Trend,
} from "../util/format.ts";
import "./gsc-leaderboard.ts";
import "./gsc-world-map.ts";

type View = "leaderboard" | "map";

interface LeaderboardData {
  leaderboard: LeaderboardEntry[];
  summary: LeaderboardSummary;
  /** Set when the API failed and demo data is shown instead. */
  error: string | null;
}

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
  @property({ type: String }) period = this._currentPeriod();
  @property({ type: String }) activeView: View = "leaderboard";

  /**
   * Reruns whenever `period` changes. A new run aborts the in-flight fetches
   * of the previous one, and results of superseded runs are discarded.
   */
  private _dataTask = new Task(this, {
    task: async ([period], { signal }): Promise<LeaderboardData> => {
      const query = `period=${encodeURIComponent(period)}`;
      try {
        const [lbRes, sumRes] = await Promise.all([
          fetch(`/api/leaderboard?${query}`, { signal }),
          fetch(`/api/leaderboard/summary?${query}`, { signal }),
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
          ...this._demoData(period),
          error:
            (err instanceof Error && err.message) ||
            "Failed to load leaderboard data.",
        };
      }
    },
    args: () => [this.period] as const,
  });

  private _currentPeriod(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
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
    const earnedAt = new Date().toISOString();
    const ranked = teams
      .map((t, i): LeaderboardEntry => ({
        teamId: i + 1,
        teamName: t.name,
        teamColor: t.color,
        teamIcon: t.icon,
        rank: 0,
        period,
        totalCo2Kg: baseCo2[i],
        previousCo2Kg: baseCo2[i] * (1 + (Math.random() * 0.3 - 0.1)),
        reductionPercent: Math.random() * 30 - 5,
        awsCo2Kg: baseCo2[i] * 0.4,
        azureCo2Kg: baseCo2[i] * 0.35,
        gcpCo2Kg: baseCo2[i] * 0.25,
        companySharePercent: 0,
        achievements:
          i === 0
            ? [
                {
                  id: 1,
                  title: "Carbon Neutral Pioneer",
                  description: null,
                  badgeIcon: "🌍",
                  earnedAt,
                  type: "FIRST_NET_ZERO",
                },
                {
                  id: 2,
                  title: "Reduction Champion",
                  description: null,
                  badgeIcon: "🏆",
                  earnedAt,
                  type: "HIGHEST_REDUCTION",
                },
              ]
            : i === 3
              ? [
                  {
                    id: 3,
                    title: "Leaderboard Leader",
                    description: null,
                    badgeIcon: "🥇",
                    earnedAt,
                    type: "LEADERBOARD_LEADER",
                  },
                ]
              : [],
      }))
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
        reductionPercent:
          prevTotal > 0 ? ((prevTotal - total) / prevTotal) * 100 : null,
        teamCount: teams.length,
      },
    };
  }

  private _onPeriodChange(e: Event) {
    const input = e.target as HTMLInputElement;
    // A cleared month input yields "", which is not a valid period.
    if (!input.value) {
      input.value = this.period;
      return;
    }
    // Setting the period reruns the data task.
    this.period = input.value;
  }

  private _renderContent({ leaderboard, summary: s, error }: LeaderboardData) {
    const trend = reductionTrend(s.reductionPercent);

    return html`
      ${
        error
          ? html`
              <div class="error-msg">
                <span>⚠️</span>
                <span>${error} — Showing demo data.</span>
              </div>
            `
          : ""
      }
      <div class="summary-bar">
        <div class="summary-card">
          <div class="summary-card-icon">🌫️</div>
          <div class="summary-card-body">
            <div class="value">${formatCo2(s.totalCo2Kg)}</div>
            <div class="label">Total Company CO₂e</div>
          </div>
        </div>
        <div class="summary-card">
          <div class="summary-card-icon">${TREND_ICON[trend]}</div>
          <div class="summary-card-body">
            <div class="value ${trend}">
              ${formatReductionPercent(s.reductionPercent)}
            </div>
            <div class="label">vs. Previous Period</div>
          </div>
        </div>
        <div class="summary-card">
          <div class="summary-card-icon">👥</div>
          <div class="summary-card-body">
            <div class="value">${s.teamCount}</div>
            <div class="label">Teams Competing</div>
          </div>
        </div>
        <div class="summary-card">
          <div class="summary-card-icon">📅</div>
          <div class="summary-card-body">
            <div class="value" style="font-size:1.1em">${this.period}</div>
            <div class="label">Current Period</div>
          </div>
        </div>
      </div>
      ${
        this.activeView === "leaderboard"
          ? html`
              <gsc-leaderboard
                .entries="${leaderboard}"
                .period="${this.period}"
              >
              </gsc-leaderboard>
            `
          : html`
              <gsc-world-map .entries="${leaderboard}" .period="${this.period}">
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
            class="nav-btn ${this.activeView === "leaderboard" ? "active" : ""}"
            @click="${() => {
              this.activeView = "leaderboard";
            }}"
          >
            🏆 Leaderboard
          </button>
          <button
            class="nav-btn ${this.activeView === "map" ? "active" : ""}"
            @click="${() => {
              this.activeView = "map";
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
            .value="${this.period}"
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
      font-family: "Source Sans 3", "Source Sans Pro", Helvetica, sans-serif;
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
