import { LitElement, css, html, nothing } from "lit";
import { customElement, property, query, state } from "lit/decorators.js";
import { styleMap } from "lit/directives/style-map.js";
import type { SparklinePoint } from "../types.ts";

/** Adds a hover readout to the prerendered trend line it contains. */
@customElement("gsc-sparkline")
export class GscSparkline extends LitElement {
  /** Evenly spaced, oldest first; empty without a line. */
  @property({ type: Array }) points: SparklinePoint[] = [];
  @state() private _hovered: SparklinePoint | null = null;
  @query(".tooltip") private _tooltip!: HTMLElement;

  constructor() {
    super();
    this.addEventListener("pointermove", this._onPointerMove);
    this.addEventListener("pointerleave", this._onPointerLeave);
  }

  private _onPointerMove(e: PointerEvent) {
    const rect = this.getBoundingClientRect();
    const ratio = Math.min(
      Math.max((e.clientX - rect.left) / rect.width, 0),
      1,
    );
    this._hovered =
      this.points[Math.round(ratio * (this.points.length - 1))] ?? null;
  }

  private _onPointerLeave() {
    this._hovered = null;
  }

  override updated() {
    // A popover in the top layer, so that cards with overflow: hidden don't clip it
    const tooltip = this._tooltip;
    if (this._hovered === null) {
      if (tooltip.matches(":popover-open")) tooltip.hidePopover();
      return;
    }
    const rect = this.getBoundingClientRect();
    tooltip.style.left = `${rect.left + (this._hovered.x / 100) * rect.width}px`;
    tooltip.style.top = `${rect.top}px`;
    if (!tooltip.matches(":popover-open")) tooltip.showPopover();
  }

  override render() {
    const p = this._hovered;
    return html`
      <slot></slot>
      ${
        p?.y !== undefined
          ? html`<span
              class="dot"
              style="${styleMap({ left: `${p.x}%`, top: `${p.y}%` })}"
            ></span>`
          : nothing
      }
      <div class="tooltip" popover="manual">
        ${
          p
            ? html`<span class="tip-label">${p.label}</span>
                <span class="tip-value">${p.value}</span>
                ${
                  p.normalized
                    ? html`<span class="tip-normalized">${p.normalized}</span>`
                    : nothing
                }
                ${
                  p.change
                    ? html`<span class="tip-change ${p.trend ?? ""}"
                        >${p.change}</span
                      >`
                    : nothing
                }`
            : nothing
        }
      </div>
    `;
  }

  /** The line and its dots are styled by sparkline.css. */
  static override styles = css`
    .dot {
      position: absolute;
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--_color);
      box-shadow: 0 0 0 2px var(--retit-surface);
      transform: translate(-50%, -50%);
      pointer-events: none;
    }

    .tooltip {
      position: fixed;
      inset: auto;
      margin: 0;
      transform: translate(-50%, calc(-100% - 6px));
      pointer-events: none;
      background: var(--retit-surface);
      color: var(--retit-text);
      border: 1px solid var(--retit-border);
      border-radius: 3px;
      box-shadow: var(--retit-shadow-raised);
      padding: 0.3em 0.6em;
      font-size: 0.75rem;
      line-height: 1.3;
      white-space: nowrap;
    }

    .tip-label {
      color: var(--retit-text-light);
      margin-right: 0.5em;
    }

    .tip-value {
      font-weight: 600;
    }

    .tip-normalized {
      margin-left: 0.4em;
      color: var(--retit-text-light);
    }

    .tip-change {
      margin-left: 0.5em;
      color: var(--retit-text-light);
    }

    .tip-change.positive {
      color: var(--retit-green-dark);
    }

    .tip-change.negative {
      color: var(--retit-red);
    }
  `;
}

declare global {
  interface HTMLElementTagNameMap {
    "gsc-sparkline": GscSparkline;
  }
}
