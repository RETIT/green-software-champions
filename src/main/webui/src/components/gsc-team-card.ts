import { LitElement, css, html } from "lit";
import { customElement, property, state } from "lit/decorators.js";
import type { LeaderboardEntry } from "../types.ts";
import {
  formatCo2,
  formatPercent,
  formatReductionPercent,
  reductionTrend,
} from "../util/format.ts";
import "./gsc-achievement-badge.ts";

@customElement("gsc-team-card")
export class GscTeamCard extends LitElement {
  @property({ type: Object }) entry?: LeaderboardEntry;
  @state() expanded = false;

  override render() {
    const e = this.entry;
    if (!e) return html``;
    const rankClass = e.rank <= 3 ? `rank-${e.rank}` : "";

    return html`
      <div
        class="card ${rankClass}"
        @click="${() => {
          this.expanded = !this.expanded;
        }}"
      >
        <div class="top-row">
          <div class="rank-badge">${e.rank}</div>
          <div class="team-icon">${e.teamIcon || "🌍"}</div>
          <div class="team-info">
            <div class="team-name">${e.teamName}</div>
            <div class="team-color-bar" style="background:${e.teamColor}"></div>
          </div>

          <div class="metrics">
            <div class="metric">
              <div class="value">${formatCo2(e.totalCo2Kg)}</div>
              <div class="label">CO₂e Total</div>
            </div>
            <div class="metric">
              <div
                class="value reduction ${reductionTrend(e.reductionPercent)}"
              >
                ${formatReductionPercent(e.reductionPercent)}
              </div>
              <div class="label">vs. Prev Period</div>
            </div>
            <div class="metric">
              <div class="value">${formatPercent(e.companySharePercent)}</div>
              <div class="label">Company Share</div>
            </div>
          </div>

          <div class="achievements">
            ${(e.achievements || [])
              .slice(0, 3)
              .map(
                (a) => html`
                  <gsc-achievement-badge
                    .achievement="${a}"
                    compact
                  ></gsc-achievement-badge>
                `,
              )}
          </div>

          <span class="chevron ${this.expanded ? "open" : ""}">▼</span>
        </div>

        ${
          this.expanded
            ? html`
                <div class="expand-section">
                  <div>
                    <div class="expand-label">
                      Company share of cloud emissions
                    </div>
                    <div class="share-bar-label">
                      <span>Cloud carbon share</span>
                      <span>${formatPercent(e.companySharePercent, 2)}</span>
                    </div>
                    <div class="share-bar-track">
                      <div
                        class="share-bar-fill"
                        style="width:${Math.min(e.companySharePercent || 0, 100)}%;background:${e.teamColor}"
                      ></div>
                    </div>
                  </div>

                  <div>
                    <div class="expand-label">Provider Breakdown</div>
                    <div class="provider-breakdown">
                      <div class="provider-chip">
                        <div class="pname">☁️ AWS</div>
                        <div class="pvalue">${formatCo2(e.awsCo2Kg)}</div>
                      </div>
                      <div class="provider-chip">
                        <div class="pname">🔷 Azure</div>
                        <div class="pvalue">${formatCo2(e.azureCo2Kg)}</div>
                      </div>
                      <div class="provider-chip">
                        <div class="pname">🟡 GCP</div>
                        <div class="pvalue">${formatCo2(e.gcpCo2Kg)}</div>
                      </div>
                    </div>
                  </div>

                  ${
                    e.achievements && e.achievements.length > 0
                      ? html`
                          <div>
                            <div class="expand-label">🏅 Achievements</div>
                            <div style="display:flex;gap:0.5em;flex-wrap:wrap">
                              ${e.achievements.map(
                                (a) => html`
                                  <gsc-achievement-badge
                                    .achievement="${a}"
                                  ></gsc-achievement-badge>
                                `,
                              )}
                            </div>
                          </div>
                        `
                      : ""
                  }
                </div>
              `
            : ""
        }
      </div>
    `;
  }

  static override styles = css`
    :host {
      display: block;
      font-family: "Source Sans 3", "Source Sans Pro", Helvetica, sans-serif;
      font-weight: 300;
    }

    /* ── Card shell ─────────────────────────────────────── */
    .card {
      background: #fff;
      border-radius: 3px;
      box-shadow: 0px 3px 10px 3px rgba(0, 0, 0, 0.08);
      padding: 1em 1.5em;
      cursor: pointer;
      transition:
        box-shadow 0.2s ease,
        transform 0.2s ease;
      position: relative;
      overflow: hidden;
    }

    /* top accent bar that expands on hover */
    .card::before {
      content: "";
      position: absolute;
      top: 0;
      left: 0;
      height: 3px;
      width: 0;
      background: #568139;
      transition: width 0.4s ease;
    }

    .card:hover {
      box-shadow: 0px 3px 7px 2px rgba(0, 0, 0, 0.18);
      transform: scale(1.005);
    }

    .card:hover::before {
      width: 100%;
    }

    /* Rank-specific top color */
    .card.rank-1::before {
      background: #d4ac0d;
      width: 100%;
    }
    .card.rank-2::before {
      background: #808080;
      width: 100%;
    }
    .card.rank-3::before {
      background: #a0522d;
      width: 100%;
    }

    /* ── Top row ────────────────────────────────────────── */
    .top-row {
      display: flex;
      align-items: center;
      gap: 1em;
    }

    /* Rank badge */
    .rank-badge {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 2.2em;
      height: 2.2em;
      border-radius: 3px;
      font-size: 1em;
      font-weight: 700;
      background: rgba(144, 144, 144, 0.1);
      color: #999;
      flex-shrink: 0;
      border: 1px solid rgba(144, 144, 144, 0.3);
    }

    .rank-1 .rank-badge {
      background: rgba(212, 172, 13, 0.1);
      color: #9a7b00;
      border-color: rgba(212, 172, 13, 0.35);
    }
    .rank-2 .rank-badge {
      background: rgba(128, 128, 128, 0.1);
      color: #555;
      border-color: rgba(128, 128, 128, 0.3);
    }
    .rank-3 .rank-badge {
      background: rgba(160, 82, 45, 0.1);
      color: #7a3b1a;
      border-color: rgba(160, 82, 45, 0.3);
    }

    .team-icon {
      font-size: 1.6em;
      flex-shrink: 0;
      line-height: 1;
    }

    .team-info {
      flex: 1;
      min-width: 0;
    }

    .team-name {
      font-size: 1em;
      font-weight: 600;
      color: #555;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .team-color-bar {
      height: 2px;
      border-radius: 1px;
      margin-top: 4px;
      width: 28px;
    }

    /* Metrics */
    .metrics {
      display: flex;
      gap: 2em;
      align-items: center;
    }

    .metric {
      text-align: right;
    }

    .metric .value {
      font-size: 1.05em;
      font-weight: 600;
      color: #555;
    }

    .metric .label {
      font-size: 0.68em;
      color: #999;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      margin-top: 1px;
      font-weight: 400;
    }

    .reduction.positive {
      color: #568139;
    }
    .reduction.negative {
      color: #c0392b;
    }
    .reduction.neutral {
      color: #999;
    }

    /* Achievements */
    .achievements {
      display: flex;
      gap: 0.3em;
    }

    /* Expand chevron */
    .chevron {
      font-size: 0.7em;
      color: #bbb;
      transition: transform 0.2s ease;
      flex-shrink: 0;
    }

    .chevron.open {
      transform: rotate(180deg);
    }

    /* ── Expanded section ───────────────────────────────── */
    .expand-section {
      margin-top: 1em;
      padding-top: 1em;
      border-top: 1px solid rgba(144, 144, 144, 0.25);
      display: grid;
      gap: 1em;
    }

    .expand-label {
      font-size: 0.7em;
      font-weight: 600;
      color: #999;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      margin-bottom: 0.4em;
    }

    /* Provider chips */
    .provider-breakdown {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 0.6em;
    }

    .provider-chip {
      background: #f9f9f9;
      border: 1px solid rgba(144, 144, 144, 0.3);
      border-radius: 3px;
      padding: 0.65em 0.75em;
      text-align: center;
      transition:
        border-color 0.15s,
        box-shadow 0.15s;
    }

    .provider-chip:hover {
      border-color: #75b729;
      box-shadow: 0 1px 4px rgba(117, 183, 41, 0.15);
    }

    .provider-chip .pname {
      font-size: 0.7em;
      color: #999;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      font-weight: 400;
      margin-bottom: 0.25em;
    }

    .provider-chip .pvalue {
      font-size: 0.95em;
      font-weight: 600;
      color: #555;
    }

    /* Share bar */
    .share-bar-label {
      display: flex;
      justify-content: space-between;
      font-size: 0.75em;
      color: #999;
      font-weight: 400;
      margin-bottom: 0.3em;
    }

    .share-bar-label span:last-child {
      color: #555;
      font-weight: 600;
    }

    .share-bar-track {
      height: 5px;
      background: rgba(144, 144, 144, 0.15);
      border-radius: 3px;
      overflow: hidden;
    }

    .share-bar-fill {
      height: 100%;
      border-radius: 3px;
      transition: width 0.8s ease;
    }
  `;
}

declare global {
  interface HTMLElementTagNameMap {
    "gsc-team-card": GscTeamCard;
  }
}
