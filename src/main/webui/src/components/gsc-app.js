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
      background: var(--bg-base, #07101F);
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    }

    /* ── Header ─────────────────────────────────────────── */
    header {
      background: rgba(13, 25, 41, 0.95);
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
      border-bottom: 1px solid rgba(255, 255, 255, 0.07);
      padding: 0 2rem;
      height: 64px;
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
      text-decoration: none;
    }

    .logo-mark {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 36px;
      height: 36px;
      background: linear-gradient(135deg, #10B981 0%, #059669 100%);
      border-radius: 9px;
      font-size: 1.1rem;
      flex-shrink: 0;
      box-shadow: 0 0 0 1px rgba(16, 185, 129, 0.3), 0 4px 12px rgba(16, 185, 129, 0.2);
    }

    .logo-text-group { display: flex; flex-direction: column; }

    .logo-brand {
      font-size: 0.95rem;
      font-weight: 700;
      color: #F1F5F9;
      letter-spacing: 0.04em;
      line-height: 1;
    }

    .logo-brand span {
      color: #34D399;
    }

    .logo-subtitle {
      font-size: 0.65rem;
      font-weight: 400;
      color: #4B6A8A;
      letter-spacing: 0.03em;
      margin-top: 2px;
      text-transform: uppercase;
    }

    /* ── Navigation ─────────────────────────────────────── */
    nav {
      display: flex;
      gap: 2px;
      background: rgba(255, 255, 255, 0.04);
      border: 1px solid rgba(255, 255, 255, 0.07);
      border-radius: 10px;
      padding: 3px;
    }

    .nav-btn {
      background: transparent;
      border: none;
      color: #94A3B8;
      padding: 0.4rem 1rem;
      border-radius: 7px;
      cursor: pointer;
      font-size: 0.8125rem;
      font-weight: 500;
      font-family: inherit;
      transition: all 0.15s ease;
      letter-spacing: 0.01em;
      white-space: nowrap;
    }

    .nav-btn:hover {
      color: #F1F5F9;
      background: rgba(255, 255, 255, 0.06);
    }

    .nav-btn.active {
      background: #10B981;
      color: #fff;
      box-shadow: 0 1px 6px rgba(16, 185, 129, 0.35);
    }

    /* ── Period selector ────────────────────────────────── */
    .period-selector {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .period-selector label {
      font-size: 0.75rem;
      font-weight: 500;
      color: #4B6A8A;
      text-transform: uppercase;
      letter-spacing: 0.06em;
    }

    .period-selector input[type="month"] {
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.09);
      color: #CBD5E1;
      padding: 0.35rem 0.65rem;
      border-radius: 8px;
      font-size: 0.8125rem;
      font-family: inherit;
      outline: none;
      transition: border-color 0.15s;
      cursor: pointer;
    }

    .period-selector input[type="month"]:hover,
    .period-selector input[type="month"]:focus {
      border-color: rgba(16, 185, 129, 0.5);
    }

    /* ── Main content ───────────────────────────────────── */
    main {
      max-width: 1360px;
      margin: 0 auto;
      padding: 2rem 2rem 3rem;
    }

    /* ── Hero band ──────────────────────────────────────── */
    .hero-band {
      background: linear-gradient(135deg, #0D1929 0%, #0F2035 50%, #091522 100%);
      border: 1px solid rgba(16, 185, 129, 0.12);
      border-radius: 16px;
      padding: 1.5rem 2rem;
      margin-bottom: 1.5rem;
      display: flex;
      align-items: center;
      gap: 1rem;
    }

    .hero-icon {
      font-size: 2.5rem;
      flex-shrink: 0;
    }

    .hero-text h1 {
      font-size: 1.375rem;
      font-weight: 700;
      color: #F1F5F9;
      letter-spacing: -0.02em;
      line-height: 1.2;
    }

    .hero-text p {
      font-size: 0.8125rem;
      color: #64748B;
      margin-top: 0.25rem;
      font-weight: 400;
    }

    .hero-pill {
      margin-left: auto;
      background: rgba(16, 185, 129, 0.1);
      border: 1px solid rgba(16, 185, 129, 0.25);
      border-radius: 20px;
      padding: 0.3rem 0.9rem;
      font-size: 0.75rem;
      font-weight: 600;
      color: #34D399;
      letter-spacing: 0.03em;
      white-space: nowrap;
    }

    /* ── Summary bar ────────────────────────────────────── */
    .summary-bar {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 1rem;
      margin-bottom: 1.5rem;
    }

    .summary-card {
      background: #0D1929;
      border: 1px solid rgba(255, 255, 255, 0.07);
      border-radius: 12px;
      padding: 1.25rem 1.5rem;
      display: flex;
      align-items: flex-start;
      gap: 1rem;
      transition: border-color 0.2s;
    }

    .summary-card:hover {
      border-color: rgba(255, 255, 255, 0.12);
    }

    .summary-card-icon {
      width: 38px;
      height: 38px;
      border-radius: 9px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.1rem;
      flex-shrink: 0;
    }

    .summary-card-icon.green  { background: rgba(16, 185, 129, 0.12); }
    .summary-card-icon.blue   { background: rgba(59, 130, 246, 0.12); }
    .summary-card-icon.amber  { background: rgba(251, 191, 36, 0.12); }
    .summary-card-icon.purple { background: rgba(139, 92, 246, 0.12); }

    .summary-card-body { flex: 1; min-width: 0; }

    .summary-card .value {
      font-size: 1.625rem;
      font-weight: 700;
      color: #F1F5F9;
      letter-spacing: -0.02em;
      line-height: 1;
    }

    .summary-card .value.green { color: #34D399; }
    .summary-card .value.red   { color: #F87171; }

    .summary-card .label {
      font-size: 0.72rem;
      font-weight: 500;
      color: #4B6A8A;
      margin-top: 0.35rem;
      text-transform: uppercase;
      letter-spacing: 0.07em;
    }

    /* ── Loading / Error ────────────────────────────────── */
    .loading {
      text-align: center;
      padding: 5rem 2rem;
      color: #4B6A8A;
    }

    .spinner {
      width: 40px;
      height: 40px;
      border: 3px solid rgba(16, 185, 129, 0.15);
      border-top-color: #10B981;
      border-radius: 50%;
      animation: spin 0.75s linear infinite;
      margin: 0 auto 1.25rem;
    }

    .loading p {
      font-size: 0.875rem;
      font-weight: 500;
      letter-spacing: 0.01em;
    }

    @keyframes spin { to { transform: rotate(360deg); } }

    .error-msg {
      background: rgba(248, 113, 113, 0.07);
      border: 1px solid rgba(248, 113, 113, 0.2);
      border-radius: 10px;
      padding: 0.875rem 1.25rem;
      color: #FCA5A5;
      font-size: 0.8125rem;
      font-weight: 500;
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin-bottom: 1.5rem;
    }

    /* ── Footer ─────────────────────────────────────────── */
    footer {
      text-align: center;
      padding: 2rem;
      color: #1E3A5A;
      font-size: 0.75rem;
      border-top: 1px solid rgba(255, 255, 255, 0.04);
      margin-top: 2rem;
    }

    footer a {
      color: #34D399;
      text-decoration: none;
    }

    footer a:hover { text-decoration: underline; }
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
      this._loadDemoData();
    } finally {
      this.loading = false;
    }
  }

  _loadDemoData() {
    const teams = [
      { name: 'Team Alpha', color: '#10B981', icon: '🌱' },
      { name: 'Team Beta',  color: '#3B82F6', icon: '💧' },
      { name: 'Team Gamma', color: '#F59E0B', icon: '⚡' },
      { name: 'Team Delta', color: '#8B5CF6', icon: '🚀' },
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
    if (kg >= 1_000) return `${(kg / 1_000).toFixed(2)} t`;
    return `${Math.round(kg)} kg`;
  }

  render() {
    const s = this.summary;
    const reductionGood = s?.reductionPercent != null && s.reductionPercent >= 0;

    return html`
      <header>
        <div class="logo">
          <div class="logo-mark">🌿</div>
          <div class="logo-text-group">
            <div class="logo-brand">RET<span>IT</span></div>
            <div class="logo-subtitle">Green Software Champions</div>
          </div>
        </div>

        <nav>
          <button class="nav-btn ${this.activeView === 'leaderboard' ? 'active' : ''}"
                  @click="${() => { this.activeView = 'leaderboard'; }}">
            🏆 Leaderboard
          </button>
          <button class="nav-btn ${this.activeView === 'map' ? 'active' : ''}"
                  @click="${() => { this.activeView = 'map'; }}">
            🗺️ World Map
          </button>
        </nav>

        <div class="period-selector">
          <label for="period">Period</label>
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
            <span>⚠️</span>
            <span>${this.error} — Showing demo data.</span>
          </div>
        ` : ''}

        <div class="hero-band">
          <span class="hero-icon">🌍</span>
          <div class="hero-text">
            <h1>Cloud Carbon Footprint Leaderboard</h1>
            <p>Track and reduce your team's cloud CO₂ emissions. Compete, improve, and champion sustainable software.</p>
          </div>
          <div class="hero-pill">🟢 Live</div>
        </div>

        ${s ? html`
          <div class="summary-bar">
            <div class="summary-card">
              <div class="summary-card-icon green">🌫️</div>
              <div class="summary-card-body">
                <div class="value">${this._formatCo2(s.totalCo2Kg)}</div>
                <div class="label">Total Company CO₂e</div>
              </div>
            </div>
            <div class="summary-card">
              <div class="summary-card-icon ${reductionGood ? 'green' : 'blue'}">
                ${reductionGood ? '📉' : '📈'}
              </div>
              <div class="summary-card-body">
                <div class="value ${s.reductionPercent != null ? (s.reductionPercent >= 0 ? 'green' : 'red') : ''}">
                  ${s.reductionPercent != null
                    ? `${s.reductionPercent >= 0 ? '↓' : '↑'} ${Math.abs(s.reductionPercent).toFixed(1)}%`
                    : '—'}
                </div>
                <div class="label">vs. Previous Period</div>
              </div>
            </div>
            <div class="summary-card">
              <div class="summary-card-icon amber">👥</div>
              <div class="summary-card-body">
                <div class="value">${s.teamCount}</div>
                <div class="label">Teams Competing</div>
              </div>
            </div>
            <div class="summary-card">
              <div class="summary-card-icon purple">📅</div>
              <div class="summary-card-body">
                <div class="value" style="font-size:1.1rem">${this.period}</div>
                <div class="label">Current Period</div>
              </div>
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

      <footer>
        Powered by <a href="https://www.retit.io" target="_blank" rel="noopener">retit.io</a>
        &nbsp;·&nbsp; Green Software Champions &nbsp;·&nbsp; Cloud Carbon Footprint Tracking
      </footer>
    `;
  }
}

customElements.define('gsc-app', GscApp);
