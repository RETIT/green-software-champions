import { LitElement, css, html, nothing } from "lit";
import { customElement, property, state } from "lit/decorators.js";
import { classMap } from "lit/directives/class-map.js";
import { styleMap } from "lit/directives/style-map.js";
import type { LeaderboardEntry } from "../types.ts";
import { groupAchievements, groupLabel } from "../util/achievements.ts";
import {
  formatCo2,
  formatPercent,
  formatPeriod,
  formatReductionPercent,
  reductionTrend,
} from "../util/format.ts";
import { daysInPeriod } from "../util/period.ts";
import "./gsc-achievement-badge.ts";
import "./gsc-history-chart.ts";
import "./gsc-sparkline.ts";

@customElement("gsc-team-card")
export class GscTeamCard extends LitElement {
  @property({ type: Object }) entry?: LeaderboardEntry;
  @state() private _expanded = false;

  override render() {
    const e = this.entry;
    if (!e) return nothing;

    const achievementGroups = groupAchievements(e.achievements || []);
    const hiddenGroups = achievementGroups.slice(3);
    const history = e.history || [];

    return html`
      <div
        class="card ${classMap({ [`rank-${e.rank}`]: e.rank <= 3 })}"
        style="${styleMap({ "--team-color": e.teamColor })}"
        @click="${() => {
          this._expanded = !this._expanded;
        }}"
      >
        <div class="top-row">
          <div class="rank-badge">${e.rank}</div>
          <div class="team-icon">${e.teamIcon || "🌍"}</div>
          <div class="team-info">
            <div class="team-name">${e.teamName}</div>
            <div class="team-color-bar"></div>
          </div>

          <div class="achievements">
            ${achievementGroups
              .slice(0, 3)
              .map(
                (group) => html`
                  <gsc-achievement-badge
                    .achievements="${group}"
                    compact
                  ></gsc-achievement-badge>
                `,
              )}
            ${
              hiddenGroups.length > 0
                ? html`
                    <span
                      class="more-achievements"
                      title="${hiddenGroups.map(groupLabel).join(",\n")}"
                    >
                      +${hiddenGroups.length}
                    </span>
                  `
                : nothing
            }
          </div>

          <gsc-sparkline
            class="trend"
            .points="${history.map((p) => ({
              label: formatPeriod(p.period, "long"),
              value: p.totalCo2Kg,
              days: daysInPeriod(p.period),
            }))}"
            label="${e.teamName} CO₂e over the last ${history.length} months"
          ></gsc-sparkline>

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

          <span class="chevron ${classMap({ open: this._expanded })}">▼</span>
        </div>

        ${
          this._expanded
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
                        style="${styleMap({
                          width: `${Math.min(e.companySharePercent || 0, 100)}%`,
                        })}"
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
                    history.some((p) => p.totalCo2Kg !== null)
                      ? html`
                          <div>
                            <div class="expand-label">
                              ${
                                history.length === 1
                                  ? formatPeriod(history[0].period, "long")
                                  : `Last ${history.length} Months`
                              }
                            </div>
                            <!-- Legend clicks toggle providers, not the card -->
                            <gsc-history-chart
                              .history="${history}"
                              @click="${(ev: Event) => ev.stopPropagation()}"
                            ></gsc-history-chart>
                          </div>
                        `
                      : nothing
                  }
                  ${
                    achievementGroups.length > 0
                      ? html`
                          <div>
                            <div class="expand-label">🏅 Achievements</div>
                            <div class="achievement-list">
                              ${achievementGroups.map(
                                (group) => html`
                                  <gsc-achievement-badge
                                    .achievements="${group}"
                                  ></gsc-achievement-badge>
                                `,
                              )}
                            </div>
                          </div>
                        `
                      : nothing
                  }
                </div>
              `
            : nothing
        }
      </div>
    `;
  }

  static override styles = css`
    :host {
      display: block;
      font-family: var(--retit-font);
      font-weight: 300;
    }

    /* ── Card shell ─────────────────────────────────────── */
    .card {
      background: var(--retit-surface);
      border-radius: 3px;
      box-shadow: var(--retit-shadow);
      padding: 1em 1.5em;
      cursor: pointer;
      transition:
        box-shadow 0.2s ease,
        transform 0.2s ease;
      position: relative;
      overflow: hidden;
    }

    .card:hover {
      box-shadow: var(--retit-shadow-raised);
      transform: scale(1.005);
    }

    /* top accent bar, only shown for the top three ranks */
    .card::before {
      content: "";
      position: absolute;
      top: 0;
      left: 0;
      height: 3px;
      width: 0;
    }

    /* Rank-specific top color */
    .card.rank-1::before {
      background: var(--retit-gold);
      width: 100%;
    }
    .card.rank-2::before {
      background: var(--retit-silver);
      width: 100%;
    }
    .card.rank-3::before {
      background: var(--retit-bronze);
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
      background: var(--retit-bg);
      color: var(--retit-text-light);
      flex-shrink: 0;
      border: 1px solid var(--retit-border);
    }

    .rank-1 .rank-badge {
      background: color-mix(
        in srgb,
        var(--retit-gold) 10%,
        var(--retit-surface)
      );
      color: var(--retit-gold-text);
      border-color: color-mix(
        in srgb,
        var(--retit-gold) 35%,
        var(--retit-surface)
      );
    }
    .rank-2 .rank-badge {
      background: color-mix(
        in srgb,
        var(--retit-silver) 10%,
        var(--retit-surface)
      );
      color: var(--retit-text);
      border-color: color-mix(
        in srgb,
        var(--retit-silver) 30%,
        var(--retit-surface)
      );
    }
    .rank-3 .rank-badge {
      background: color-mix(
        in srgb,
        var(--retit-bronze) 10%,
        var(--retit-surface)
      );
      color: var(--retit-bronze-text);
      border-color: color-mix(
        in srgb,
        var(--retit-bronze) 30%,
        var(--retit-surface)
      );
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
      color: var(--retit-text);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .team-color-bar {
      background: var(--team-color);
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
      color: var(--retit-text);
    }

    .metric .label {
      font-size: 0.68em;
      color: var(--retit-text-light);
      text-transform: uppercase;
      letter-spacing: 0.06em;
      margin-top: 1px;
      font-weight: 400;
    }

    .reduction.positive {
      color: var(--retit-green-dark);
    }
    .reduction.negative {
      color: var(--retit-red);
    }
    .reduction.neutral {
      color: var(--retit-text-light);
    }

    /* Trend sparkline */
    .trend {
      flex-shrink: 0;
      --sparkline-color: var(--team-color);
    }

    /* Achievements */
    .achievements {
      display: flex;
      align-items: center;
      gap: 0.3em;
    }

    .more-achievements {
      font-size: 0.8em;
      font-weight: 600;
      color: var(--retit-text-light);
      white-space: nowrap;
      cursor: help;
    }

    .achievement-list {
      display: flex;
      gap: 0.5em;
      flex-wrap: wrap;
    }

    /* Expand chevron */
    .chevron {
      font-size: 0.7em;
      color: var(--retit-text-light);
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
      border-top: 1px solid var(--retit-border);
      display: grid;
      grid-template-columns: minmax(0, 1fr);
      gap: 1em;
    }

    .expand-label {
      font-size: 0.7em;
      font-weight: 600;
      color: var(--retit-text-light);
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
      background: var(--retit-bg);
      border: 1px solid var(--retit-border);
      border-radius: 3px;
      padding: 0.65em 0.75em;
      text-align: center;
    }

    .provider-chip .pname {
      font-size: 0.7em;
      color: var(--retit-text-light);
      text-transform: uppercase;
      letter-spacing: 0.05em;
      font-weight: 400;
      margin-bottom: 0.25em;
    }

    .provider-chip .pvalue {
      font-size: 0.95em;
      font-weight: 600;
      color: var(--retit-text);
    }

    /* Share bar */
    .share-bar-label {
      display: flex;
      justify-content: space-between;
      font-size: 0.75em;
      color: var(--retit-text-light);
      font-weight: 400;
      margin-bottom: 0.3em;
    }

    .share-bar-label span:last-child {
      color: var(--retit-text);
      font-weight: 600;
    }

    .share-bar-track {
      height: 5px;
      background: var(--retit-border);
      border-radius: 3px;
      overflow: hidden;
    }

    .share-bar-fill {
      background: var(--team-color);
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
