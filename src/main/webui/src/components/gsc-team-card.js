import { LitElement, html, css } from 'lit';

/**
 * Individual team card in the leaderboard.
 */
class GscTeamCard extends LitElement {
  static properties = {
    entry: { type: Object },
    expanded: { type: Boolean, state: true },
  };

  static styles = css`
    :host {
      display: block;
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    }

    /* ── Card shell ─────────────────────────────────────── */
    .card {
      background: #0D1929;
      border: 1px solid rgba(255, 255, 255, 0.07);
      border-radius: 14px;
      padding: 1.125rem 1.5rem;
      transition: border-color 0.2s ease, box-shadow 0.2s ease;
      cursor: pointer;
      user-select: none;
    }

    .card:hover {
      border-color: rgba(255, 255, 255, 0.13);
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
    }

    .card.rank-1 {
      border-color: rgba(255, 215, 0, 0.22);
      background: linear-gradient(135deg, #0D1929 0%, #181206 100%);
    }

    .card.rank-2 {
      border-color: rgba(192, 192, 192, 0.2);
      background: linear-gradient(135deg, #0D1929 0%, #121417 100%);
    }

    .card.rank-3 {
      border-color: rgba(205, 127, 50, 0.2);
      background: linear-gradient(135deg, #0D1929 0%, #170F07 100%);
    }

    /* ── Top row ────────────────────────────────────────── */
    .top-row {
      display: flex;
      align-items: center;
      gap: 1rem;
    }

    /* Rank badge */
    .rank-badge {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 36px;
      height: 36px;
      border-radius: 10px;
      font-size: 1rem;
      font-weight: 800;
      background: rgba(255, 255, 255, 0.05);
      color: #4B6A8A;
      flex-shrink: 0;
      letter-spacing: -0.02em;
    }

    .rank-1 .rank-badge { background: rgba(255, 215, 0, 0.1);  color: #FBBF24; }
    .rank-2 .rank-badge { background: rgba(192, 192, 192, 0.1); color: #CBD5E1; }
    .rank-3 .rank-badge { background: rgba(205, 127, 50, 0.1);  color: #D97706; }

    /* Team icon */
    .team-icon {
      font-size: 1.625rem;
      flex-shrink: 0;
      line-height: 1;
    }

    /* Team name / color bar */
    .team-info { flex: 1; min-width: 0; }

    .team-name {
      font-size: 0.9375rem;
      font-weight: 600;
      color: #E2E8F0;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .team-color-bar {
      height: 2px;
      border-radius: 1px;
      margin-top: 5px;
      width: 32px;
      opacity: 0.7;
    }

    /* Metrics row */
    .metrics {
      display: flex;
      gap: 2rem;
      align-items: center;
    }

    .metric { text-align: right; }

    .metric .value {
      font-size: 1.0625rem;
      font-weight: 700;
      color: #E2E8F0;
      letter-spacing: -0.02em;
    }

    .metric .label {
      font-size: 0.67rem;
      color: #4B6A8A;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      margin-top: 2px;
      font-weight: 500;
    }

    .reduction.positive { color: #34D399; }
    .reduction.negative { color: #F87171; }
    .reduction.neutral  { color: #4B6A8A; }

    /* Achievements */
    .achievements {
      display: flex;
      gap: 0.35rem;
    }

    /* Expand chevron */
    .chevron {
      font-size: 0.75rem;
      color: #4B6A8A;
      transition: transform 0.2s ease;
      flex-shrink: 0;
      margin-left: 0.25rem;
    }

    .chevron.open { transform: rotate(180deg); }

    /* ── Expanded section ───────────────────────────────── */
    .expand-section {
      margin-top: 1.125rem;
      padding-top: 1.125rem;
      border-top: 1px solid rgba(255, 255, 255, 0.06);
      display: grid;
      gap: 1rem;
    }

    .expand-label {
      font-size: 0.68rem;
      font-weight: 600;
      color: #4B6A8A;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      margin-bottom: 0.5rem;
    }

    /* Provider chips */
    .provider-breakdown {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 0.625rem;
    }

    .provider-chip {
      background: rgba(255, 255, 255, 0.04);
      border: 1px solid rgba(255, 255, 255, 0.06);
      border-radius: 10px;
      padding: 0.75rem;
      text-align: center;
      transition: border-color 0.15s;
    }

    .provider-chip:hover {
      border-color: rgba(255, 255, 255, 0.11);
    }

    .provider-chip .pname {
      font-size: 0.68rem;
      color: #4B6A8A;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      font-weight: 600;
      margin-bottom: 0.35rem;
    }

    .provider-chip .pvalue {
      font-size: 0.9375rem;
      font-weight: 700;
      color: #CBD5E1;
      letter-spacing: -0.01em;
    }

    /* Share bar */
    .share-bar-container { }

    .share-bar-label {
      display: flex;
      justify-content: space-between;
      font-size: 0.72rem;
      color: #4B6A8A;
      font-weight: 500;
      margin-bottom: 0.4rem;
    }

    .share-bar-label span:last-child {
      color: #94A3B8;
      font-weight: 600;
    }

    .share-bar-track {
      height: 5px;
      background: rgba(255, 255, 255, 0.06);
      border-radius: 3px;
      overflow: hidden;
    }

    .share-bar-fill {
      height: 100%;
      border-radius: 3px;
      transition: width 0.8s ease;
    }

    /* Achievements expanded */
    .achievements-section { }
  `;

  constructor() {
    super();
    this.expanded = false;
  }

  _formatCo2(kg) {
    if (kg === null || kg === undefined) return '—';
    if (kg >= 1_000) return `${(kg / 1_000).toFixed(2)} t`;
    return `${Math.round(kg)} kg`;
  }

  _reductionClass(pct) {
    if (pct === null || pct === undefined) return 'neutral';
    return pct >= 0 ? 'positive' : 'negative';
  }

  _reductionLabel(pct) {
    if (pct === null || pct === undefined) return '—';
    const sign = pct >= 0 ? '↓' : '↑';
    return `${sign} ${Math.abs(pct).toFixed(1)}%`;
  }

  render() {
    const e = this.entry;
    if (!e) return html``;

    const rankClass = e.rank <= 3 ? `rank-${e.rank}` : '';

    return html`
      <div class="card ${rankClass}" @click="${() => { this.expanded = !this.expanded; }}">
        <div class="top-row">
          <div class="rank-badge">${e.rank}</div>
          <div class="team-icon">${e.teamIcon || '🌍'}</div>
          <div class="team-info">
            <div class="team-name">${e.teamName}</div>
            <div class="team-color-bar" style="background:${e.teamColor}"></div>
          </div>

          <div class="metrics">
            <div class="metric">
              <div class="value">${this._formatCo2(e.totalCo2Kg)}</div>
              <div class="label">CO₂e Total</div>
            </div>
            <div class="metric">
              <div class="value reduction ${this._reductionClass(e.reductionPercent)}">
                ${this._reductionLabel(e.reductionPercent)}
              </div>
              <div class="label">vs. Prev Period</div>
            </div>
            <div class="metric">
              <div class="value">
                ${e.companySharePercent != null ? e.companySharePercent.toFixed(1) + '%' : '—'}
              </div>
              <div class="label">Company Share</div>
            </div>
          </div>

          <div class="achievements">
            ${(e.achievements || []).slice(0, 3).map(a => html`
              <gsc-achievement-badge .achievement="${a}" compact></gsc-achievement-badge>
            `)}
          </div>

          <span class="chevron ${this.expanded ? 'open' : ''}">▼</span>
        </div>

        ${this.expanded ? html`
          <div class="expand-section">
            <div class="share-bar-container">
              <div class="expand-label">Company share of cloud emissions</div>
              <div class="share-bar-label">
                <span>Cloud carbon share</span>
                <span>${e.companySharePercent != null ? e.companySharePercent.toFixed(2) + '%' : '—'}</span>
              </div>
              <div class="share-bar-track">
                <div class="share-bar-fill"
                     style="width:${Math.min(e.companySharePercent || 0, 100)}%;background:${e.teamColor}">
                </div>
              </div>
            </div>

            <div>
              <div class="expand-label">Provider Breakdown</div>
              <div class="provider-breakdown">
                <div class="provider-chip">
                  <div class="pname">☁️ AWS</div>
                  <div class="pvalue">${this._formatCo2(e.awsCo2Kg)}</div>
                </div>
                <div class="provider-chip">
                  <div class="pname">🔷 Azure</div>
                  <div class="pvalue">${this._formatCo2(e.azureCo2Kg)}</div>
                </div>
                <div class="provider-chip">
                  <div class="pname">🟡 GCP</div>
                  <div class="pvalue">${this._formatCo2(e.gcpCo2Kg)}</div>
                </div>
              </div>
            </div>

            ${e.achievements && e.achievements.length > 0 ? html`
              <div class="achievements-section">
                <div class="expand-label">🏅 Achievements</div>
                <div style="display:flex;gap:0.5rem;flex-wrap:wrap">
                  ${e.achievements.map(a => html`
                    <gsc-achievement-badge .achievement="${a}"></gsc-achievement-badge>
                  `)}
                </div>
              </div>
            ` : ''}
          </div>
        ` : ''}
      </div>
    `;
  }
}

customElements.define('gsc-team-card', GscTeamCard);
