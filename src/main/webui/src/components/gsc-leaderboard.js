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
      font-family: 'Source Sans 3', 'Source Sans Pro', Helvetica, sans-serif;
      font-weight: 300;
    }

    .section-header {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      margin-bottom: 1em;
      border-bottom: 1px solid rgba(144, 144, 144, 0.4);
      padding-bottom: 0.5em;
    }

    .section-title {
      font-size: 1.1em;
      font-weight: 400;
      color: #555;
      display: flex;
      align-items: center;
      gap: 0.4em;
    }

    .section-meta {
      font-size: 0.8em;
      color: #999;
      font-weight: 300;
    }

    .leaderboard-grid {
      display: grid;
      gap: 0.75em;
    }

    .empty-state {
      text-align: center;
      padding: 4em 2em;
      color: #999;
      background: #fff;
      border-radius: 3px;
      box-shadow: 0px 3px 10px 3px rgba(0, 0, 0, 0.08);
    }

    .empty-state .empty-icon {
      font-size: 2.5em;
      margin-bottom: 0.75em;
      opacity: 0.4;
    }

    .empty-state p {
      font-size: 0.9em;
      line-height: 1.6;
    }

    .legend {
      display: flex;
      gap: 1.25em;
      margin-bottom: 1em;
      font-size: 0.78em;
      color: #999;
      flex-wrap: wrap;
      align-items: center;
    }

    .legend-item {
      display: flex;
      align-items: center;
      gap: 0.35em;
      font-weight: 400;
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
      background: rgba(144, 144, 144, 0.4);
    }
  `;

  render() {
    if (!this.entries || this.entries.length === 0) {
      return html`
        <div class="empty-state">
          <div class="empty-icon">📊</div>
          <p>No leaderboard data available for <strong>${this.period}</strong>.<br/>
          Run a carbon data sync to populate the leaderboard.</p>
        </div>
      `;
    }

    return html`
      <div class="section-header">
        <div class="section-title">🏆 Carbon Footprint Leaderboard</div>
        <div class="section-meta">${this.period}</div>
      </div>

      <div class="legend">
        <div class="legend-item">
          <div class="legend-dot" style="background:#75b729"></div>
          <span>Low footprint</span>
        </div>
        <div class="legend-item">
          <div class="legend-dot" style="background:#e09c1a"></div>
          <span>Medium footprint</span>
        </div>
        <div class="legend-item">
          <div class="legend-dot" style="background:#c0392b"></div>
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
