import { LitElement, html, css } from 'lit';

/**
 * Root application shell component.
 * Manages global state: authentication status, selected period,
 * leaderboard data, and company summary.
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
      background: linear-gradient(135deg, #0a0f1e 0%, #0d1b2a 50%, #0a1628 100%);
    }

    header {
      background: rgba(255, 255, 255, 0.05);
      backdrop-filter: blur(10px);
      border-bottom: 1px solid rgba(255, 255, 255, 0.1);
      padding: 1rem 2rem;
      display: flex;
      align-items: center;
      justify-content: space-between;
      position: sticky;
      top: 0;
      z-index: 100;
    }

    .logo {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .logo-emoji { font-size: 2rem; }

    .logo-text {
      font-size: 1.4rem;
      font-weight: 700;
      background: linear-gradient(90deg, #4caf50, #2196f3, #ff9800);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
    }

    .logo-subtitle {
      font-size: 0.75rem;
      color: #7a9bb5;
      margin-top: -4px;
    }

    nav {
      display: flex;
      gap: 0.5rem;
    }

    .nav-btn {
      background: transparent;
      border: 1px solid rgba(255, 255, 255, 0.15);
      color: #c0d0e0;
      padding: 0.4rem 1rem;
      border-radius: 20px;
      cursor: pointer;
      font-size: 0.875rem;
      transition: all 0.2s;
    }

    .nav-btn:hover,
    .nav-btn.active {
      background: rgba(76, 175, 80, 0.2);
      border-color: #4caf50;
      color: #fff;
    }

    .period-selector {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .period-selector label {
      font-size: 0.8rem;
      color: #7a9bb5;
    }

    .period-selector input[type="month"] {
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.15);
      color: #e0e6f0;
      padding: 0.3rem 0.6rem;
      border-radius: 6px;
      font-size: 0.875rem;
    }

    main {
      max-width: 1400px;
      margin: 0 auto;
      padding: 2rem;
    }

    .summary-bar {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 1rem;
      margin-bottom: 2rem;
    }

    .summary-card {
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 12px;
      padding: 1.25rem;
      text-align: center;
    }

    .summary-card .value {
      font-size: 1.8rem;
      font-weight: 700;
      color: #4caf50;
    }

    .summary-card .label {
      font-size: 0.8rem;
      color: #7a9bb5;
      margin-top: 0.25rem;
    }

    .summary-card .trend {
      font-size: 0.9rem;
      margin-top: 0.5rem;
    }

    .trend.up   { color: #4caf50; }
    .trend.down { color: #f44336; }

    .loading {
      text-align: center;
      padding: 4rem;
      color: #7a9bb5;
    }

    .spinner {
      width: 48px;
      height: 48px;
      border: 4px solid rgba(76, 175, 80, 0.2);
      border-top-color: #4caf50;
      border-radius: 50%;
      animation: spin 1s linear infinite;
      margin: 0 auto 1rem;
    }

    @keyframes spin { to { transform: rotate(360deg); } }

    .error-msg {
      background: rgba(244, 67, 54, 0.1);
      border: 1px solid rgba(244, 67, 54, 0.3);
      border-radius: 8px;
      padding: 1rem;
      color: #ef9a9a;
      text-align: center;
      margin: 2rem 0;
    }
  `;

  constructor() {
    super();
    this.leaderboard = [];
    this.summary = null;
    this.period = this._currentPeriod();
    this.loading = false;
    this.error = null;
    this.activeView = 'leaderboard';
  }

  _currentPeriod() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
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

      if (!lbRes.ok) throw new Error(`Leaderboard fetch failed: ${lbRes.status}`);
      if (!sumRes.ok) throw new Error(`Summary fetch failed: ${sumRes.status}`);

      this.leaderboard = await lbRes.json();
      this.summary = await sumRes.json();
    } catch (err) {
      this.error = err.message || 'Failed to load leaderboard data.';
      // Load demo data so the UI is still useful without a backend
      this._loadDemoData();
    } finally {
      this.loading = false;
    }
  }

  _loadDemoData() {
    const teams = [
      { name: 'Team Alpha', color: '#4CAF50', icon: '🌱' },
      { name: 'Team Beta',  color: '#2196F3', icon: '💧' },
      { name: 'Team Gamma', color: '#FF9800', icon: '⚡' },
      { name: 'Team Delta', color: '#9C27B0', icon: '🚀' },
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
        reductionPercent: (Math.random() * 30 - 5),
        awsCo2Kg: baseCo2[i] * 0.4,
        azureCo2Kg: baseCo2[i] * 0.35,
        gcpCo2Kg: baseCo2[i] * 0.25,
        companySharePercent: 0,
        achievements: i === 0 ? [
          { id: 1, title: '🌍 Carbon Neutral Pioneer', badgeIcon: '🌍', type: 'FIRST_NET_ZERO' },
          { id: 2, title: '🏆 Reduction Champion', badgeIcon: '🏆', type: 'HIGHEST_REDUCTION' },
        ] : i === 3 ? [
          { id: 3, title: '🥇 Leaderboard Leader', badgeIcon: '🥇', type: 'LEADERBOARD_LEADER' },
        ] : [],
      }))
      .sort((a, b) => a.totalCo2Kg - b.totalCo2Kg)
      .map((t, i) => ({ ...t, rank: i + 1 }));

    const total = this.leaderboard.reduce((s, t) => s + (t.totalCo2Kg || 0), 0);
    const prevTotal = this.leaderboard.reduce((s, t) => s + (t.previousCo2Kg || 0), 0);
    this.leaderboard = this.leaderboard.map(t => ({
      ...t,
      companySharePercent: total > 0 ? (t.totalCo2Kg / total) * 100 : 0,
    }));
    this.summary = {
      period: this.period,
      totalCo2Kg: total,
      previousTotalCo2Kg: prevTotal,
      reductionPercent: prevTotal > 0 ? ((prevTotal - total) / prevTotal) * 100 : null,
      teamCount: teams.length,
    };
  }

  _onPeriodChange(e) {
    this.period = e.target.value;
    this._loadData();
  }

  _formatCo2(kg) {
    if (kg === null || kg === undefined) return '—';
    if (kg >= 1_000_000) return `${(kg / 1_000_000).toFixed(2)} t`;
    if (kg >= 1_000) return `${(kg / 1_000).toFixed(2)} t`;
    return `${kg.toFixed(0)} kg`;
  }

  render() {
    const s = this.summary;
    return html`
      <header>
        <div class="logo">
          <span class="logo-emoji">🌍</span>
          <div>
            <div class="logo-text">Green Software Champions</div>
            <div class="logo-subtitle">Cloud Carbon Footprint Leaderboard</div>
          </div>
        </div>
        <nav>
          <button class="nav-btn ${this.activeView === 'leaderboard' ? 'active' : ''}"
                  @click="${() => { this.activeView = 'leaderboard'; }}">🏆 Leaderboard</button>
          <button class="nav-btn ${this.activeView === 'map' ? 'active' : ''}"
                  @click="${() => { this.activeView = 'map'; }}">🗺️ World Map</button>
        </nav>
        <div class="period-selector">
          <label for="period">Period:</label>
          <input type="month" id="period" .value="${this.period}"
                 @change="${this._onPeriodChange}" />
        </div>
      </header>

      <main>
        ${this.loading ? html`
          <div class="loading">
            <div class="spinner"></div>
            <p>Loading carbon data…</p>
          </div>
        ` : ''}

        ${this.error ? html`
          <div class="error-msg">
            ⚠️ ${this.error} — Showing demo data.
          </div>
        ` : ''}

        ${s ? html`
          <div class="summary-bar">
            <div class="summary-card">
              <div class="value">${this._formatCo2(s.totalCo2Kg)}</div>
              <div class="label">Total Company CO₂e</div>
            </div>
            <div class="summary-card">
              <div class="value ${s.reductionPercent != null ? (s.reductionPercent >= 0 ? 'up' : 'down') : ''}">
                ${s.reductionPercent != null
                  ? `${s.reductionPercent >= 0 ? '↓' : '↑'} ${Math.abs(s.reductionPercent).toFixed(1)}%`
                  : '—'}
              </div>
              <div class="label">vs. Previous Period</div>
            </div>
            <div class="summary-card">
              <div class="value">${s.teamCount}</div>
              <div class="label">Teams Competing</div>
            </div>
            <div class="summary-card">
              <div class="value">${this.period}</div>
              <div class="label">Current Period</div>
            </div>
          </div>
        ` : ''}

        ${this.activeView === 'leaderboard' ? html`
          <gsc-leaderboard
            .entries="${this.leaderboard}"
            .period="${this.period}">
          </gsc-leaderboard>
        ` : html`
          <gsc-world-map
            .entries="${this.leaderboard}"
            .period="${this.period}">
          </gsc-world-map>
        `}
      </main>
    `;
  }
}

customElements.define('gsc-app', GscApp);
