import { LitElement, html, css } from 'lit';

/**
 * Individual team card in the leaderboard.
 * Shows rank, name, total CO2, reduction percentage, provider breakdown,
 * company share, and earned achievements.
 */
class GscTeamCard extends LitElement {
  static properties = {
    entry: { type: Object },
    expanded: { type: Boolean, state: true },
  };

  static styles = css`
    :host { display: block; }

    .card {
      background: rgba(255, 255, 255, 0.04);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 16px;
      padding: 1.25rem 1.5rem;
      transition: all 0.3s ease;
      cursor: pointer;
    }

    .card:hover {
      background: rgba(255, 255, 255, 0.07);
      transform: translateY(-2px);
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
    }

    .card.rank-1 {
      border-color: rgba(255, 215, 0, 0.4);
      background: rgba(255, 215, 0, 0.05);
    }

    .card.rank-2 {
      border-color: rgba(192, 192, 192, 0.4);
      background: rgba(192, 192, 192, 0.04);
    }

    .card.rank-3 {
      border-color: rgba(205, 127, 50, 0.4);
      background: rgba(205, 127, 50, 0.04);
    }

    .top-row {
      display: flex;
      align-items: center;
      gap: 1rem;
    }

    .rank {
      font-size: 1.5rem;
      font-weight: 800;
      min-width: 2.5rem;
      text-align: center;
      color: #7a9bb5;
    }

    .rank-1 .rank { color: #ffd700; }
    .rank-2 .rank { color: #c0c0c0; }
    .rank-3 .rank { color: #cd7f32; }

    .team-icon {
      font-size: 2rem;
    }

    .team-info {
      flex: 1;
    }

    .team-name {
      font-size: 1.1rem;
      font-weight: 600;
      color: #e0e6f0;
    }

    .team-color-bar {
      height: 3px;
      border-radius: 2px;
      margin-top: 4px;
      width: 40px;
    }

    .metrics {
      display: flex;
      gap: 2rem;
      align-items: center;
      flex-wrap: wrap;
    }

    .metric {
      text-align: right;
    }

    .metric .value {
      font-size: 1.2rem;
      font-weight: 700;
    }

    .metric .label {
      font-size: 0.7rem;
      color: #7a9bb5;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .reduction.positive { color: #4caf50; }
    .reduction.negative { color: #f44336; }
    .reduction.neutral  { color: #7a9bb5; }

    .achievements {
      display: flex;
      gap: 0.5rem;
      flex-wrap: wrap;
    }

    .expand-section {
      margin-top: 1rem;
      padding-top: 1rem;
      border-top: 1px solid rgba(255, 255, 255, 0.07);
    }

    .provider-breakdown {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 0.75rem;
      margin-bottom: 1rem;
    }

    .provider-chip {
      background: rgba(255, 255, 255, 0.05);
      border-radius: 8px;
      padding: 0.6rem;
      text-align: center;
    }

    .provider-chip .pname {
      font-size: 0.7rem;
      color: #7a9bb5;
      text-transform: uppercase;
    }

    .provider-chip .pvalue {
      font-size: 1rem;
      font-weight: 600;
      color: #c0d0e0;
    }

    .share-bar-container {
      margin-bottom: 0.75rem;
    }

    .share-bar-label {
      display: flex;
      justify-content: space-between;
      font-size: 0.75rem;
      color: #7a9bb5;
      margin-bottom: 0.25rem;
    }

    .share-bar-track {
      height: 6px;
      background: rgba(255, 255, 255, 0.08);
      border-radius: 3px;
      overflow: hidden;
    }

    .share-bar-fill {
      height: 100%;
      border-radius: 3px;
      transition: width 0.8s ease;
    }

    .achievements-section h4 {
      font-size: 0.8rem;
      color: #7a9bb5;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      margin-bottom: 0.5rem;
    }
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
          <div class="rank">${e.rank}</div>
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
              <div class="value">${e.companySharePercent != null ? e.companySharePercent.toFixed(1) + '%' : '—'}</div>
              <div class="label">Company Share</div>
            </div>
          </div>
          <div class="achievements">
            ${(e.achievements || []).slice(0, 3).map(a => html`
              <gsc-achievement-badge .achievement="${a}" compact></gsc-achievement-badge>
            `)}
          </div>
        </div>

        ${this.expanded ? html`
          <div class="expand-section">
            <div class="share-bar-container">
              <div class="share-bar-label">
                <span>Company share of cloud emissions</span>
                <span>${e.companySharePercent != null ? e.companySharePercent.toFixed(2) + '%' : '—'}</span>
              </div>
              <div class="share-bar-track">
                <div class="share-bar-fill"
                     style="width:${Math.min(e.companySharePercent || 0, 100)}%;background:${e.teamColor}">
                </div>
              </div>
            </div>

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

            ${e.achievements && e.achievements.length > 0 ? html`
              <div class="achievements-section">
                <h4>🏅 Achievements</h4>
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
