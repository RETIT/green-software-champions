import { LitElement, html, css } from "lit";
import {
  formatCo2,
  formatReductionPercent,
  reductionTrend,
} from "../util/format";

const TREND_ICON = { positive: "📉", negative: "📈", neutral: "➖" };

/**
 * Root application shell — styled to RETIT CI.
 */
class GscApp extends LitElement {
  static properties = {
    leaderboard: { type: Array },
    summary: { type: Object },
    period: { type: String },
    loading: { type: Boolean },
    error: { type: String },
    activeView: { type: String },
  };

  static styles = css`
    :host {
      display: block;
      min-height: 100vh;
      background: #f4fbf6;
      font-family: "Source Sans 3", "Source Sans Pro", Helvetica, sans-serif;
      font-weight: 300;
      color: #666;
    }

    /* ── Navigation bar (RETIT green) ──────────────────── */
    header {
      background: var(--retit-footer);
      color: #f7f7f7;
      height: 3.25em;
      line-height: 3.25em;
      position: sticky;
      top: 0;
      z-index: 1000;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 1.5em;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
    }

    /* Logo */
    .logo {
      display: flex;
      align-items: center;
      gap: 0.6em;
      text-decoration: none;
      color: #fff;
    }

    .logo-icon {
      font-size: 1.4em;
      line-height: 1;
    }

    .logo-text {
      font-size: 1em;
      font-weight: 600;
      color: #fff;
      letter-spacing: 0.01em;
      line-height: 1;
    }

    .logo-text .retit-highlight {
      color: #fff;
      opacity: 0.9;
    }

    .logo-sep {
      color: rgba(255, 255, 255, 0.5);
      font-weight: 300;
      margin: 0 0.3em;
    }

    .logo-sub {
      color: rgba(255, 255, 255, 0.85);
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
      color: #f7f7f7;
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
      background-color: rgba(0, 0, 0, 0.15);
    }

    .nav-btn.active {
      background-color: rgba(0, 0, 0, 0.2);
      box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.3);
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
      color: rgba(255, 255, 255, 0.8);
      text-transform: uppercase;
      letter-spacing: 0.06em;
    }

    .period-selector input[type="month"] {
      background: rgba(255, 255, 255, 0.15);
      border: 1px solid rgba(255, 255, 255, 0.35);
      color: #fff;
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
      background: rgba(255, 255, 255, 0.25);
    }

    /* ── Hero section ───────────────────────────────────── */
    .hero {
      background: #f4fbf6;
      padding: 3em 2em 2em;
      text-align: left;
      border-bottom: 1px solid rgba(144, 144, 144, 0.25);
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
      color: #568139;
      line-height: 1.25em;
      margin: 0 0 0.4em;
    }

    .hero-text h1 span {
      color: #75b729;
    }

    .hero-text p {
      font-size: 0.95em;
      color: #999;
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
      background: #fff;
      border-radius: 3px;
      box-shadow: 0px 3px 10px 3px rgba(0, 0, 0, 0.08);
      padding: 1.25em 1.5em;
      display: flex;
      align-items: flex-start;
      gap: 1em;
      transition:
        box-shadow 0.2s,
        transform 0.2s;
      position: relative;
    }

    .summary-card::before {
      content: "";
      position: absolute;
      top: 0;
      left: 0;
      width: 0;
      height: 3px;
      background: #568139;
      border-radius: 3px 3px 0 0;
      transition: width 0.4s ease;
    }

    .summary-card:hover {
      box-shadow: 0px 3px 7px 2px rgba(0, 0, 0, 0.18);
      transform: translateY(-1px);
    }

    .summary-card:hover::before {
      width: 100%;
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
      color: #555;
      line-height: 1;
      letter-spacing: -0.01em;
    }

    .summary-card .value.positive {
      color: #568139;
    }
    .summary-card .value.negative {
      color: #c0392b;
    }

    .summary-card .label {
      font-size: 0.72em;
      font-weight: 400;
      color: #999;
      margin-top: 0.35em;
      text-transform: uppercase;
      letter-spacing: 0.07em;
    }

    /* ── Loading ────────────────────────────────────────── */
    .loading {
      text-align: center;
      padding: 5em 2em;
      color: #999;
    }

    .spinner {
      width: 40px;
      height: 40px;
      border: 3px solid rgba(117, 183, 41, 0.2);
      border-top-color: #75b729;
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
      background: rgba(192, 57, 43, 0.06);
      border: 1px solid rgba(192, 57, 43, 0.25);
      border-radius: 3px;
      padding: 0.75em 1.25em;
      color: #c0392b;
      font-size: 0.875em;
      font-weight: 400;
      display: flex;
      align-items: center;
      gap: 0.5em;
      margin-bottom: 1.5em;
    }

    /* ── Footer ─────────────────────────────────────────── */
    footer {
      background: var(--retit-footer);
      color: rgba(255, 255, 255, 0.7);
      text-align: center;
      padding: 2.5em 2em;
      font-size: 0.85em;
      font-weight: 300;
      margin-top: auto;
    }

    footer a {
      color: #c3e895;
      text-decoration: none;
      border-bottom: 1px dotted rgba(195, 232, 149, 0.4);
      transition: color 0.2s;
    }

    footer a:hover {
      color: #75b729;
      border-bottom-color: transparent;
    }
  `;

  constructor() {
    super();
    this.leaderboard = [];
    this.summary = null;
    this.period = this._currentPeriod();
    this.loading = false;
    this.error = null;
    this.activeView = "leaderboard";
  }

  _currentPeriod() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
  }

  connectedCallback() {
    super.connectedCallback();
    this._loadData();
  }

  async _loadData() {
    this.loading = true;
    this.error = null;
    try {
      const [lbRes, sumRes] = await Promise.all([
        fetch(`/api/leaderboard?period=${this.period}`),
        fetch(`/api/leaderboard/summary?period=${this.period}`),
      ]);
      if (!lbRes.ok)
        throw new Error(`Leaderboard fetch failed: ${lbRes.status}`);
      if (!sumRes.ok) throw new Error(`Summary fetch failed: ${sumRes.status}`);
      this.leaderboard = await lbRes.json();
      this.summary = await sumRes.json();
    } catch (err) {
      this.error = err.message || "Failed to load leaderboard data.";
      this._loadDemoData();
    } finally {
      this.loading = false;
    }
  }

  _loadDemoData() {
    const teams = [
      { name: "Team Alpha", color: "#75b729", icon: "🌱" },
      { name: "Team Beta", color: "#3b8ecf", icon: "💧" },
      { name: "Team Gamma", color: "#e09c1a", icon: "⚡" },
      { name: "Team Delta", color: "#8b5cf6", icon: "🚀" },
    ];
    const baseCo2 = [1240, 2890, 3450, 980];
    this.leaderboard = teams
      .map((t, i) => ({
        teamId: i + 1,
        teamName: t.name,
        teamColor: t.color,
        teamIcon: t.icon,
        rank: 0,
        period: this.period,
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
                  title: "🌍 Carbon Neutral Pioneer",
                  badgeIcon: "🌍",
                  type: "FIRST_NET_ZERO",
                },
                {
                  id: 2,
                  title: "🏆 Reduction Champion",
                  badgeIcon: "🏆",
                  type: "HIGHEST_REDUCTION",
                },
              ]
            : i === 3
              ? [
                  {
                    id: 3,
                    title: "🥇 Leaderboard Leader",
                    badgeIcon: "🥇",
                    type: "LEADERBOARD_LEADER",
                  },
                ]
              : [],
      }))
      .sort((a, b) => a.totalCo2Kg - b.totalCo2Kg)
      .map((t, i) => ({ ...t, rank: i + 1 }));

    const total = this.leaderboard.reduce((s, t) => s + (t.totalCo2Kg || 0), 0);
    const prevTotal = this.leaderboard.reduce(
      (s, t) => s + (t.previousCo2Kg || 0),
      0,
    );
    this.leaderboard = this.leaderboard.map((t) => ({
      ...t,
      companySharePercent: total > 0 ? (t.totalCo2Kg / total) * 100 : 0,
    }));
    this.summary = {
      period: this.period,
      totalCo2Kg: total,
      previousTotalCo2Kg: prevTotal,
      reductionPercent:
        prevTotal > 0 ? ((prevTotal - total) / prevTotal) * 100 : null,
      teamCount: teams.length,
    };
  }

  _onPeriodChange(e) {
    this.period = e.target.value;
    this._loadData();
  }

  render() {
    const s = this.summary;
    const trend = reductionTrend(s?.reductionPercent);

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
        ${
          this.loading
            ? html`
                <div class="loading">
                  <div class="spinner"></div>
                  <p>Loading carbon data…</p>
                </div>
              `
            : ""
        }
        ${
          this.error
            ? html`
                <div class="error-msg">
                  <span>⚠️</span>
                  <span>${this.error} — Showing demo data.</span>
                </div>
              `
            : ""
        }
        ${
          s
            ? html`
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
                      <div class="value" style="font-size:1.1em">
                        ${this.period}
                      </div>
                      <div class="label">Current Period</div>
                    </div>
                  </div>
                </div>
              `
            : ""
        }
        ${
          this.activeView === "leaderboard"
            ? html`
                <gsc-leaderboard
                  .entries="${this.leaderboard}"
                  .period="${this.period}"
                >
                </gsc-leaderboard>
              `
            : html`
                <gsc-world-map
                  .entries="${this.leaderboard}"
                  .period="${this.period}"
                >
                </gsc-world-map>
              `
        }
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
}

customElements.define("gsc-app", GscApp);
