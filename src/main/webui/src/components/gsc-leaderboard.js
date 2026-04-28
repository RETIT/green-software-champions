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
    :host { display: block; }

    h2 {
      font-size: 1.4rem;
      margin-bottom: 1.5rem;
      color: #c0d0e0;
    }

    .leaderboard-grid {
      display: grid;
      gap: 1rem;
    }

    gsc-team-card {
      display: block;
    }

    .empty-state {
      text-align: center;
      padding: 4rem;
      color: #7a9bb5;
    }

    .legend {
      display: flex;
      gap: 1.5rem;
      margin-bottom: 1.5rem;
      font-size: 0.8rem;
      color: #7a9bb5;
      flex-wrap: wrap;
    }

    .legend-item {
      display: flex;
      align-items: center;
      gap: 0.4rem;
    }

    .legend-dot {
      width: 10px;
      height: 10px;
      border-radius: 50%;
    }
  `;

  render() {
    if (!this.entries || this.entries.length === 0) {
      return html`
        <div class="empty-state">
          <p>No leaderboard data available for ${this.period}.</p>
          <p>Run a carbon data sync to populate the leaderboard.</p>
        </div>
      `;
    }

    return html`
      <h2>🏆 Carbon Footprint Leaderboard — ${this.period}</h2>
      <div class="legend">
        <div class="legend-item">
          <div class="legend-dot" style="background:#4caf50"></div>
          <span>Low footprint</span>
        </div>
        <div class="legend-item">
          <div class="legend-dot" style="background:#ff9800"></div>
          <span>Medium footprint</span>
        </div>
        <div class="legend-item">
          <div class="legend-dot" style="background:#f44336"></div>
          <span>High footprint</span>
        </div>
        <div class="legend-item">↓ Reduction is good, ↑ increase is bad</div>
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
