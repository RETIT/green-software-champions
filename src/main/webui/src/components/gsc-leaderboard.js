import { LitElement, html, css } from 'lit';

/**
 * Leaderboard component that displays all teams ranked by their
 * carbon footprint with reduction metrics and achievements.
 */
class GscLeaderboard extends LitElement {
  static properties = {
    entries: { type: Array },
    period: { type: String },
  };

  static styles = css`
    :host {
      display: block;
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    }

    .section-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 1.25rem;
      gap: 1rem;
    }

    .section-title {
      font-size: 1rem;
      font-weight: 600;
      color: #CBD5E1;
      letter-spacing: -0.01em;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .section-meta {
      font-size: 0.72rem;
      color: #4B6A8A;
      font-weight: 400;
      background: rgba(255, 255, 255, 0.04);
      border: 1px solid rgba(255, 255, 255, 0.07);
      border-radius: 20px;
      padding: 0.2rem 0.75rem;
    }

    .leaderboard-grid {
      display: grid;
      gap: 0.75rem;
    }

    gsc-team-card {
      display: block;
    }

    .empty-state {
      text-align: center;
      padding: 5rem 2rem;
      color: #4B6A8A;
      background: #0D1929;
      border: 1px solid rgba(255, 255, 255, 0.06);
      border-radius: 14px;
    }

    .empty-state .empty-icon {
      font-size: 3rem;
      margin-bottom: 1rem;
      opacity: 0.5;
    }

    .empty-state p {
      font-size: 0.875rem;
      line-height: 1.6;
    }

    .legend {
      display: flex;
      gap: 1rem;
      margin-bottom: 1rem;
      font-size: 0.75rem;
      color: #4B6A8A;
      flex-wrap: wrap;
      align-items: center;
    }

    .legend-item {
      display: flex;
      align-items: center;
      gap: 0.35rem;
      font-weight: 500;
    }

    .legend-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      flex-shrink: 0;
    }

    .legend-divider {
      width: 1px;
      height: 12px;
      background: rgba(255, 255, 255, 0.08);
    }
  `;

  render() {
    if (!this.entries || this.entries.length === 0) {
      return html`
        <div class="empty-state">
          <div class="empty-icon">📊</div>
          <p>No leaderboard data available for <strong style="color:#94A3B8">${this.period}</strong>.<br/>
          Run a carbon data sync to populate the leaderboard.</p>
        </div>
      `;
    }

    return html`
      <div class="section-header">
        <div class="section-title">
          🏆 Carbon Footprint Leaderboard
        </div>
        <div class="section-meta">${this.period}</div>
      </div>

      <div class="legend">
        <div class="legend-item">
          <div class="legend-dot" style="background:#10B981"></div>
          <span>Low footprint</span>
        </div>
        <div class="legend-item">
          <div class="legend-dot" style="background:#F59E0B"></div>
          <span>Medium footprint</span>
        </div>
        <div class="legend-item">
          <div class="legend-dot" style="background:#F87171"></div>
          <span>High footprint</span>
        </div>
        <div class="legend-divider"></div>
        <div class="legend-item">↓ reduction is good &nbsp;·&nbsp; ↑ increase needs attention</div>
      </div>

      <div class="leaderboard-grid">
        ${this.entries.map(entry => html`
          <gsc-team-card .entry="${entry}"></gsc-team-card>
        `)}
      </div>
    `;
  }
}

customElements.define('gsc-leaderboard', GscLeaderboard);
