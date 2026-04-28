import { LitElement, html, css } from 'lit';

/**
 * World map visualization component.
 *
 * Renders an SVG world map with team "carbon bubbles" overlaid on major
 * cloud data-centre regions. Bubble size represents the team's share of
 * the company's total cloud carbon footprint.
 *
 * The map uses a simplified equirectangular SVG projection. Team positions
 * are mapped to the geographic regions of their primary cloud providers.
 */
class GscWorldMap extends LitElement {
  static properties = {
    entries: { type: Array },
    period:  { type: String },
  };

  static styles = css`
    :host { display: block; }

    h2 {
      font-size: 1.4rem;
      margin-bottom: 1.5rem;
      color: #c0d0e0;
    }

    .map-container {
      background: rgba(255, 255, 255, 0.03);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 16px;
      padding: 2rem;
      position: relative;
      overflow: hidden;
    }

    svg {
      width: 100%;
      height: auto;
      display: block;
    }

    /* Continents */
    .land {
      fill: #1a2744;
      stroke: #2a3f6a;
      stroke-width: 0.5;
    }

    /* Ocean */
    .ocean {
      fill: #0d1829;
    }

    /* Grid lines */
    .graticule {
      fill: none;
      stroke: rgba(255, 255, 255, 0.04);
      stroke-width: 0.3;
    }

    /* Team bubble */
    .team-bubble {
      cursor: pointer;
      transition: all 0.3s ease;
    }

    .team-bubble circle {
      opacity: 0.75;
      transition: all 0.3s ease;
    }

    .team-bubble:hover circle {
      opacity: 1;
      filter: drop-shadow(0 0 8px currentColor);
    }

    .team-bubble text {
      fill: #fff;
      font-size: 10px;
      text-anchor: middle;
      dominant-baseline: central;
      pointer-events: none;
      font-weight: 600;
    }

    .team-label {
      fill: #c0d0e0;
      font-size: 9px;
      text-anchor: middle;
      pointer-events: none;
    }

    /* Pulse animation for top-ranked team */
    @keyframes pulse {
      0%   { r: var(--base-r); opacity: 0.5; }
      50%  { r: calc(var(--base-r) + 8px); opacity: 0; }
      100% { r: var(--base-r); opacity: 0; }
    }

    .pulse-ring {
      fill: none;
      stroke-width: 2;
      animation: pulse-ring 2s ease-out infinite;
    }

    @keyframes pulse-ring {
      0%   { r: 0; opacity: 0.8; }
      100% { r: 40px; opacity: 0; }
    }

    .legend {
      display: flex;
      gap: 2rem;
      margin-top: 1.5rem;
      flex-wrap: wrap;
      font-size: 0.8rem;
      color: #7a9bb5;
      align-items: center;
    }

    .legend-bubble-demo {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .legend-circle {
      border-radius: 50%;
      background: rgba(76, 175, 80, 0.5);
      border: 2px solid #4caf50;
    }

    .tooltip {
      position: absolute;
      background: rgba(10, 15, 30, 0.95);
      border: 1px solid rgba(255, 255, 255, 0.15);
      border-radius: 8px;
      padding: 0.75rem 1rem;
      pointer-events: none;
      font-size: 0.8rem;
      min-width: 160px;
      transition: opacity 0.2s;
      z-index: 10;
    }

    .tooltip.hidden { opacity: 0; }

    .tooltip-name  { font-weight: 700; color: #e0e6f0; margin-bottom: 0.25rem; }
    .tooltip-value { color: #7a9bb5; }
    .tooltip-value span { color: #4caf50; font-weight: 600; }
  `;

  constructor() {
    super();
    this.entries = [];
    this.period = '';
    this._tooltip = { visible: false, x: 0, y: 0, entry: null };
  }

  /**
   * Map team index → approximate SVG coordinate (on a 960×500 viewBox).
   * Positions correspond to major cloud data-centre regions worldwide.
   */
  _teamRegions() {
    const regions = [
      { x: 760, y: 200 }, // US West (e.g., AWS us-west)
      { x: 680, y: 165 }, // US East (e.g., AWS us-east)
      { x: 460, y: 170 }, // Europe West (e.g., Azure West Europe)
      { x: 530, y: 175 }, // Europe North (e.g., GCP europe-north)
      { x: 780, y: 290 }, // South America
      { x: 680, y: 350 }, // South America East
      { x: 820, y: 150 }, // Canada
      { x: 120, y: 200 }, // Asia Pacific (APAC)
      { x: 200, y: 250 }, // Southeast Asia
      { x: 480, y: 330 }, // Africa
    ];
    return regions;
  }

  _getPosition(index) {
    const regions = this._teamRegions();
    return regions[index % regions.length];
  }

  _bubbleRadius(sharePercent) {
    if (!sharePercent) return 12;
    // Scale: 0% → r=12, 100% → r=50
    return 12 + (sharePercent / 100) * 38;
  }

  _formatCo2(kg) {
    if (kg === null || kg === undefined) return '—';
    if (kg >= 1_000) return `${(kg / 1_000).toFixed(2)} t`;
    return `${Math.round(kg)} kg`;
  }

  _showTooltip(e, entry) {
    const container = this.shadowRoot.querySelector('.map-container');
    const rect = container.getBoundingClientRect();
    const x = e.clientX - rect.left + 10;
    const y = e.clientY - rect.top + 10;
    this._tooltip = { visible: true, x, y, entry };
    this.requestUpdate();
  }

  _hideTooltip() {
    this._tooltip = { ...this._tooltip, visible: false };
    this.requestUpdate();
  }

  /**
   * Simplified SVG world map paths (equirectangular projection).
   * Major landmasses are approximated for visual clarity.
   */
  _renderMapPaths() {
    return html`
      <!-- Ocean background -->
      <rect class="ocean" x="0" y="0" width="960" height="500" />

      <!-- Graticule (grid lines) -->
      <g class="graticule">
        <!-- Latitude lines -->
        ${[80, 60, 40, 20, 0, -20, -40, -60, -80].map(lat => {
          const y = (90 - lat) / 180 * 500;
          return html`<line x1="0" y1="${y}" x2="960" y2="${y}"/>`;
        })}
        <!-- Longitude lines -->
        ${[-160, -120, -80, -40, 0, 40, 80, 120, 160].map(lon => {
          const x = (lon + 180) / 360 * 960;
          return html`<line x1="${x}" y1="0" x2="${x}" y2="500"/>`;
        })}
      </g>

      <!-- Simplified continent shapes -->
      <g class="land">
        <!-- North America -->
        <path d="M 115,80 L 200,70 L 280,80 L 300,100 L 320,140 L 340,180
                 L 310,220 L 280,260 L 250,280 L 220,290 L 200,280
                 L 180,260 L 160,240 L 140,220 L 120,200 L 100,180
                 L 90,150 L 95,120 Z"/>
        <!-- Central America & Caribbean -->
        <path d="M 250,280 L 270,300 L 260,320 L 240,310 L 230,295 Z"/>

        <!-- South America -->
        <path d="M 260,320 L 300,320 L 340,340 L 360,380 L 370,420
                 L 350,460 L 320,480 L 290,470 L 270,440 L 255,400
                 L 240,360 L 245,330 Z"/>

        <!-- Europe -->
        <path d="M 420,80 L 500,75 L 530,90 L 540,110 L 520,130
                 L 500,140 L 480,145 L 460,140 L 440,130 L 430,110 Z"/>
        <!-- Scandinavia -->
        <path d="M 470,55 L 500,50 L 510,70 L 495,90 L 475,85 Z"/>
        <!-- UK / Ireland -->
        <path d="M 400,90 L 415,85 L 420,100 L 408,108 Z"/>
        <!-- Iberian Peninsula -->
        <path d="M 420,120 L 445,115 L 450,140 L 430,148 L 418,135 Z"/>

        <!-- Africa -->
        <path d="M 440,150 L 520,145 L 560,170 L 575,210 L 565,270
                 L 540,330 L 510,370 L 490,390 L 470,375 L 450,340
                 L 435,300 L 428,250 L 430,200 L 435,170 Z"/>

        <!-- Middle East -->
        <path d="M 530,130 L 590,125 L 610,145 L 600,170 L 570,175
                 L 540,165 L 530,148 Z"/>

        <!-- Russia / Central Asia -->
        <path d="M 490,60 L 600,50 L 700,55 L 760,65 L 780,85
                 L 750,100 L 700,105 L 640,100 L 580,95 L 530,90
                 L 510,75 Z"/>

        <!-- South Asia / India -->
        <path d="M 620,150 L 680,145 L 700,165 L 700,200 L 680,230
                 L 660,240 L 640,225 L 625,200 L 615,175 Z"/>

        <!-- Southeast Asia -->
        <path d="M 730,190 L 780,185 L 800,205 L 790,230 L 760,235
                 L 735,220 Z"/>
        <!-- Indonesia (simplified) -->
        <path d="M 760,250 L 810,245 L 840,255 L 845,270 L 810,275
                 L 770,268 Z"/>

        <!-- East Asia (China / Korea / Japan) -->
        <path d="M 720,90 L 800,85 L 840,100 L 850,130 L 830,160
                 L 800,170 L 760,165 L 730,145 L 715,120 Z"/>
        <!-- Japan -->
        <path d="M 855,100 L 870,95 L 880,115 L 865,125 L 852,112 Z"/>

        <!-- Australia -->
        <path d="M 750,320 L 840,315 L 880,335 L 890,370 L 870,405
                 L 830,420 L 790,415 L 760,390 L 745,355 L 745,330 Z"/>

        <!-- New Zealand -->
        <path d="M 895,400 L 905,390 L 912,405 L 905,418 L 895,412 Z"/>

        <!-- Greenland -->
        <path d="M 295,30 L 340,20 L 370,35 L 365,60 L 340,70
                 L 310,65 L 295,50 Z"/>
      </g>
    `;
  }

  render() {
    const t = this._tooltip;

    return html`
      <h2>🗺️ Carbon Footprint World Map — ${this.period}</h2>
      <div class="map-container" @mouseleave="${this._hideTooltip}">
        <svg viewBox="0 0 960 500" xmlns="http://www.w3.org/2000/svg">
          ${this._renderMapPaths()}

          <!-- Team bubbles -->
          ${(this.entries || []).map((entry, i) => {
            const pos = this._getPosition(i);
            const r = this._bubbleRadius(entry.companySharePercent);
            return html`
              <g class="team-bubble"
                 @mouseenter="${(e) => this._showTooltip(e, entry)}"
                 @mousemove="${(e) => this._showTooltip(e, entry)}"
                 @mouseleave="${this._hideTooltip}">
                ${i === 0 ? html`
                  <circle class="pulse-ring" cx="${pos.x}" cy="${pos.y}"
                          stroke="${entry.teamColor}" opacity="0.6" r="${r}"/>
                ` : ''}
                <circle cx="${pos.x}" cy="${pos.y}" r="${r}"
                        fill="${entry.teamColor}"
                        stroke="${entry.teamColor}"
                        stroke-width="2"/>
                <text x="${pos.x}" y="${pos.y}">${entry.teamIcon || entry.rank}</text>
                <text class="team-label" x="${pos.x}" y="${pos.y + r + 12}">
                  ${entry.teamName}
                </text>
              </g>
            `;
          })}
        </svg>

        <!-- Tooltip -->
        <div class="tooltip ${t.visible ? '' : 'hidden'}"
             style="left:${t.x}px;top:${t.y}px">
          ${t.entry ? html`
            <div class="tooltip-name">${t.entry.teamIcon} ${t.entry.teamName}</div>
            <div class="tooltip-value">
              CO₂e: <span>${this._formatCo2(t.entry.totalCo2Kg)}</span>
            </div>
            <div class="tooltip-value">
              Company share: <span>${t.entry.companySharePercent != null
                ? t.entry.companySharePercent.toFixed(1) + '%'
                : '—'}</span>
            </div>
            <div class="tooltip-value">
              Change: <span style="color:${(t.entry.reductionPercent || 0) >= 0 ? '#4caf50' : '#f44336'}">
                ${t.entry.reductionPercent != null
                  ? `${t.entry.reductionPercent >= 0 ? '↓' : '↑'} ${Math.abs(t.entry.reductionPercent).toFixed(1)}%`
                  : '—'}
              </span>
            </div>
            ${t.entry.achievements && t.entry.achievements.length > 0 ? html`
              <div class="tooltip-value" style="margin-top:0.3rem">
                ${t.entry.achievements.map(a => a.badgeIcon).join(' ')}
              </div>
            ` : ''}
          ` : ''}
        </div>
      </div>

      <div class="legend">
        <span>Bubble size = share of company cloud carbon</span>
        <div class="legend-bubble-demo">
          <div class="legend-circle" style="width:12px;height:12px"></div>
          <span>Small share</span>
        </div>
        <div class="legend-bubble-demo">
          <div class="legend-circle" style="width:30px;height:30px"></div>
          <span>Large share</span>
        </div>
        <span>📍 Bubbles positioned at primary cloud region</span>
      </div>
    `;
  }
}

customElements.define('gsc-world-map', GscWorldMap);
