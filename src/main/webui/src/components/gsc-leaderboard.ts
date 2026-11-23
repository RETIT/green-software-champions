import { LitElement, css, html } from "lit";
import { customElement, property } from "lit/decorators.js";
import { repeat } from "lit/directives/repeat.js";
import type { LeaderboardEntry } from "../types.ts";
import { formatPeriod } from "../util/format.ts";
import "./gsc-team-card.ts";

@customElement("gsc-leaderboard")
export class GscLeaderboard extends LitElement {
  @property({ type: Array }) entries: LeaderboardEntry[] = [];
  @property({ type: String }) period = "";

  override render() {
    if (!this.entries || this.entries.length === 0) {
      return html`
        <div class="empty-state">
          <div class="empty-icon">📊</div>
          <p>
            No leaderboard data available for
            <strong>${formatPeriod(this.period, "long")}</strong>.<br />
            Run a carbon data sync to populate the leaderboard.
          </p>
        </div>
      `;
    }

    return html`
      <div class="section-header">
        <div class="section-title">🏆 Carbon Footprint Leaderboard</div>
        <div class="section-meta">
          ${this.entries.length} ${this.entries.length === 1 ? "team" : "teams"}
          · ${formatPeriod(this.period, "long")}
        </div>
      </div>

      <div class="legend">
        <div class="legend-item">
          ↓ reduction is good &nbsp;·&nbsp; ↑ increase needs attention
        </div>
      </div>

      <div class="leaderboard-grid">
        ${repeat(
          this.entries,
          (entry) => entry.teamId,
          (entry) => html` <gsc-team-card .entry="${entry}"></gsc-team-card> `,
        )}
      </div>
    `;
  }

  static override styles = css`
    :host {
      display: block;
      font-family: var(--retit-font);
      font-weight: 300;
    }

    .section-header {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      margin-bottom: 1em;
      border-bottom: 1px solid var(--retit-border);
      padding-bottom: 0.5em;
    }

    .section-title {
      font-size: 1.1em;
      font-weight: 400;
      color: var(--retit-text);
      display: flex;
      align-items: center;
      gap: 0.4em;
    }

    .section-meta {
      font-size: 0.8em;
      color: var(--retit-text-light);
      font-weight: 300;
    }

    .leaderboard-grid {
      display: grid;
      /* minmax(0, …) so card content can never widen the column */
      grid-template-columns: minmax(0, 1fr);
      gap: 0.75em;
    }

    .empty-state {
      text-align: center;
      padding: 4em 2em;
      color: var(--retit-text-light);
      background: var(--retit-surface);
      border-radius: 3px;
      box-shadow: var(--retit-shadow);
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
      color: var(--retit-text-light);
      flex-wrap: wrap;
      align-items: center;
    }

    .legend-item {
      display: flex;
      align-items: center;
      gap: 0.35em;
      font-weight: 400;
    }
  `;
}

declare global {
  interface HTMLElementTagNameMap {
    "gsc-leaderboard": GscLeaderboard;
  }
}
