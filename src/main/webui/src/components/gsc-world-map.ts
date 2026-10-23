import { LitElement, css, html, nothing, svg } from "lit";
import { customElement, property, query, state } from "lit/decorators.js";
import { classMap } from "lit/directives/class-map.js";
import { repeat } from "lit/directives/repeat.js";
import { styleMap } from "lit/directives/style-map.js";
import type { LeaderboardEntry } from "../types.ts";
import {
  formatCo2,
  formatPercent,
  formatReductionPercent,
  reductionTrend,
} from "../util/format.ts";

interface Point {
  x: number;
  y: number;
}

interface Tooltip extends Point {
  visible: boolean;
  entry: LeaderboardEntry | null;
}

/**
 * World map visualization component.
 */
@customElement("gsc-world-map")
export class GscWorldMap extends LitElement {
  @property({ type: Array }) entries: LeaderboardEntry[] = [];
  @property({ type: String }) period = "";

  @query(".map-container") private _container!: HTMLElement;

  @state() private _tooltip: Tooltip = {
    visible: false,
    x: 0,
    y: 0,
    entry: null,
  };

  private _teamRegions(): Point[] {
    return [
      { x: 760, y: 200 },
      { x: 680, y: 165 },
      { x: 460, y: 170 },
      { x: 530, y: 175 },
      { x: 780, y: 290 },
      { x: 680, y: 350 },
      { x: 820, y: 150 },
      { x: 120, y: 200 },
      { x: 200, y: 250 },
      { x: 480, y: 330 },
    ];
  }

  private _getPosition(index: number): Point {
    const regions = this._teamRegions();
    return regions[index % regions.length]!;
  }

  private _bubbleRadius(sharePercent: number | null): number {
    if (!sharePercent) return 12;
    return 12 + (sharePercent / 100) * 38;
  }

  private _showTooltip(e: MouseEvent, entry: LeaderboardEntry) {
    const rect = this._container.getBoundingClientRect();
    const x = e.clientX - rect.left + 12;
    const y = e.clientY - rect.top + 12;
    this._tooltip = { visible: true, x, y, entry };
  }

  private _hideTooltip() {
    this._tooltip = { ...this._tooltip, visible: false };
  }

  private _renderMapPaths() {
    return svg`
      <rect class="ocean" x="0" y="0" width="960" height="500" />
      <g class="graticule">
        ${[80, 60, 40, 20, 0, -20, -40, -60, -80].map((lat) => {
          const y = ((90 - lat) / 180) * 500;
          return svg`<line x1="0" y1="${y}" x2="960" y2="${y}"/>`;
        })}
        ${[-160, -120, -80, -40, 0, 40, 80, 120, 160].map((lon) => {
          const x = ((lon + 180) / 360) * 960;
          return svg`<line x1="${x}" y1="0" x2="${x}" y2="500"/>`;
        })}
      </g>
      <g class="land">
        <path d="M 115,80 L 200,70 L 280,80 L 300,100 L 320,140 L 340,180 L 310,220 L 280,260 L 250,280 L 220,290 L 200,280 L 180,260 L 160,240 L 140,220 L 120,200 L 100,180 L 90,150 L 95,120 Z"/>
        <path d="M 250,280 L 270,300 L 260,320 L 240,310 L 230,295 Z"/>
        <path d="M 260,320 L 300,320 L 340,340 L 360,380 L 370,420 L 350,460 L 320,480 L 290,470 L 270,440 L 255,400 L 240,360 L 245,330 Z"/>
        <path d="M 420,80 L 500,75 L 530,90 L 540,110 L 520,130 L 500,140 L 480,145 L 460,140 L 440,130 L 430,110 Z"/>
        <path d="M 470,55 L 500,50 L 510,70 L 495,90 L 475,85 Z"/>
        <path d="M 400,90 L 415,85 L 420,100 L 408,108 Z"/>
        <path d="M 420,120 L 445,115 L 450,140 L 430,148 L 418,135 Z"/>
        <path d="M 440,150 L 520,145 L 560,170 L 575,210 L 565,270 L 540,330 L 510,370 L 490,390 L 470,375 L 450,340 L 435,300 L 428,250 L 430,200 L 435,170 Z"/>
        <path d="M 530,130 L 590,125 L 610,145 L 600,170 L 570,175 L 540,165 L 530,148 Z"/>
        <path d="M 490,60 L 600,50 L 700,55 L 760,65 L 780,85 L 750,100 L 700,105 L 640,100 L 580,95 L 530,90 L 510,75 Z"/>
        <path d="M 620,150 L 680,145 L 700,165 L 700,200 L 680,230 L 660,240 L 640,225 L 625,200 L 615,175 Z"/>
        <path d="M 730,190 L 780,185 L 800,205 L 790,230 L 760,235 L 735,220 Z"/>
        <path d="M 760,250 L 810,245 L 840,255 L 845,270 L 810,275 L 770,268 Z"/>
        <path d="M 720,90 L 800,85 L 840,100 L 850,130 L 830,160 L 800,170 L 760,165 L 730,145 L 715,120 Z"/>
        <path d="M 855,100 L 870,95 L 880,115 L 865,125 L 852,112 Z"/>
        <path d="M 750,320 L 840,315 L 880,335 L 890,370 L 870,405 L 830,420 L 790,415 L 760,390 L 745,355 L 745,330 Z"/>
        <path d="M 895,400 L 905,390 L 912,405 L 905,418 L 895,412 Z"/>
        <path d="M 295,30 L 340,20 L 370,35 L 365,60 L 340,70 L 310,65 L 295,50 Z"/>
      </g>
    `;
  }

  override render() {
    const t = this._tooltip;

    return html`
      <div class="section-header">
        <div class="section-title">🗺️ Carbon Footprint World Map</div>
        <div class="section-meta">${this.period}</div>
      </div>

      <div class="map-container" @mouseleave="${this._hideTooltip}">
        <svg viewBox="0 0 960 500" xmlns="http://www.w3.org/2000/svg">
          ${this._renderMapPaths()}
          ${repeat(
            this.entries || [],
            (entry) => entry.teamId,
            (entry, i) => {
              const pos = this._getPosition(i);
              const r = this._bubbleRadius(entry.companySharePercent);
              return svg`
              <g class="team-bubble"
                 @mouseenter="${(e: MouseEvent) => this._showTooltip(e, entry)}"
                 @mousemove="${(e: MouseEvent) => this._showTooltip(e, entry)}"
                 @mouseleave="${this._hideTooltip}">
                ${
                  i === 0
                    ? svg`
                  <circle class="pulse-ring" cx="${pos.x}" cy="${pos.y}"
                          stroke="${entry.teamColor}" opacity="0.5" r="${r}"/>
                `
                    : nothing
                }
                <circle cx="${pos.x}" cy="${pos.y}" r="${r}"
                        fill="${entry.teamColor}"
                        stroke="${entry.teamColor}"
                        stroke-width="1.5"/>
                <text x="${pos.x}" y="${pos.y}">${entry.teamIcon || entry.rank}</text>
                <text class="team-label" x="${pos.x}" y="${pos.y + r + 13}">
                  ${entry.teamName}
                </text>
              </g>
            `;
            },
          )}
        </svg>

        <div
          class="tooltip ${classMap({ hidden: !t.visible })}"
          style="${styleMap({ left: `${t.x}px`, top: `${t.y}px` })}"
        >
          ${
            t.entry
              ? html`
                  <div class="tooltip-name">
                    ${t.entry.teamIcon} ${t.entry.teamName}
                  </div>
                  <div class="tooltip-row">
                    <span>CO₂e</span>
                    <span>${formatCo2(t.entry.totalCo2Kg)}</span>
                  </div>
                  <div class="tooltip-row">
                    <span>Company share</span>
                    <span>${formatPercent(t.entry.companySharePercent)}</span>
                  </div>
                  <div class="tooltip-row">
                    <span>Change</span>
                    <span class="${reductionTrend(t.entry.reductionPercent)}">
                      ${formatReductionPercent(t.entry.reductionPercent)}
                    </span>
                  </div>
                  ${
                    t.entry.achievements && t.entry.achievements.length > 0
                      ? html`
                          <div class="tooltip-row tooltip-achievements">
                            ${t.entry.achievements.map((a) => html`<span title="${a.title}">${a.badgeIcon || "🏅"}</span>`)}
                          </div>
                        `
                      : nothing
                  }
                `
              : nothing
          }
        </div>
      </div>

      <div class="legend">
        <span>Bubble size = share of company cloud carbon</span>
        <div class="legend-bubble-demo">
          <div class="legend-circle small"></div>
          <span>Smaller share</span>
        </div>
        <div class="legend-bubble-demo">
          <div class="legend-circle large"></div>
          <span>Larger share</span>
        </div>
        <span>📍 Bubbles positioned at primary cloud region</span>
      </div>
    `;
  }

  static override styles = css`
    :host {
      display: block;
      font-family: "Source Sans 3", "Source Sans Pro", Helvetica, sans-serif;
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
    }

    .section-meta {
      font-size: 0.8em;
      color: var(--retit-text-light);
      font-weight: 300;
    }

    .map-container {
      background: var(--retit-surface);
      border-radius: 3px;
      box-shadow: var(--retit-shadow);
      padding: 1.5em;
      position: relative;
      overflow: hidden;
    }

    svg {
      width: 100%;
      height: auto;
      display: block;
    }

    /* Map colors aligned to RETIT light theme */
    .land {
      fill: var(--retit-map-land);
      stroke: color-mix(
        in srgb,
        var(--retit-map-land) 70%,
        var(--retit-green-dark)
      );
      stroke-width: 0.5;
    }

    .ocean {
      fill: var(--retit-map-ocean);
    }

    .graticule {
      fill: none;
      stroke: var(--retit-border);
      stroke-width: 0.3;
    }

    .team-bubble {
      cursor: pointer;
      transition: all 0.25s ease;
    }

    .team-bubble circle {
      opacity: 0.82;
      transition: all 0.25s ease;
    }

    .team-bubble:hover circle {
      opacity: 1;
      filter: drop-shadow(0 0 6px currentColor);
    }

    .team-bubble text {
      fill: var(--retit-white);
      font-size: 10px;
      text-anchor: middle;
      dominant-baseline: central;
      pointer-events: none;
      font-weight: 700;
    }

    .team-label {
      fill: var(--retit-text);
      font-size: 9px;
      text-anchor: middle;
      pointer-events: none;
      font-family: "Source Sans 3", "Source Sans Pro", sans-serif;
      font-weight: 600;
    }

    @keyframes pulse-ring {
      0% {
        r: 0;
        opacity: 0.6;
      }
      100% {
        r: 40px;
        opacity: 0;
      }
    }

    .pulse-ring {
      fill: none;
      stroke-width: 2;
      animation: pulse-ring 2.5s ease-out infinite;
    }

    /* Legend */
    .legend {
      display: flex;
      gap: 1.5em;
      margin-top: 1em;
      flex-wrap: wrap;
      font-size: 0.78em;
      color: var(--retit-text-light);
      align-items: center;
      font-weight: 400;
    }

    .legend-bubble-demo {
      display: flex;
      align-items: center;
      gap: 0.4em;
    }

    .legend-circle {
      border-radius: 50%;
      background: color-mix(
        in srgb,
        var(--retit-green-dark) 25%,
        var(--retit-bg)
      );
      border: 2px solid var(--retit-green-dark);
    }

    .legend-circle.small {
      width: 10px;
      height: 10px;
    }

    .legend-circle.large {
      width: 26px;
      height: 26px;
    }

    /* Tooltip */
    .tooltip {
      position: absolute;
      background: var(--retit-surface);
      border: 1px solid var(--retit-border);
      border-radius: 3px;
      padding: 0.7em 1em;
      pointer-events: none;
      font-size: 0.8em;
      min-width: 160px;
      transition: opacity 0.15s;
      z-index: 10;
      box-shadow: var(--retit-shadow-raised);
    }

    .tooltip.hidden {
      opacity: 0;
    }

    .tooltip-name {
      font-weight: 600;
      color: var(--retit-text);
      margin-bottom: 0.35em;
      font-size: 0.95em;
    }

    .tooltip-row {
      display: flex;
      justify-content: space-between;
      gap: 0.75em;
      color: var(--retit-text-light);
      font-weight: 400;
      margin-top: 0.15em;
      font-size: 0.9em;
    }

    .tooltip-achievements {
      justify-content: flex-start;
      gap: 0.25em;
      margin-top: 0.3em;
    }

    .tooltip-row span:last-child {
      font-weight: 600;
      color: var(--retit-green-dark);
    }

    .tooltip-row span.negative {
      color: var(--retit-red);
    }
  `;
}

declare global {
  interface HTMLElementTagNameMap {
    "gsc-world-map": GscWorldMap;
  }
}
