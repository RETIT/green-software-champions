import { LitElement, css, html, nothing, svg } from "lit";
import { customElement, property, query, state } from "lit/decorators.js";
import { styleMap } from "lit/directives/style-map.js";
import {
  formatCo2,
  formatReductionPercent,
  reductionTrend,
} from "../util/format.ts";
import { NORMALIZED_DAYS, normalizeToMonth } from "../util/period.ts";

export interface SparklinePoint {
  label: string;
  /** null marks a period without data; it is drawn as a gap. */
  value: number | null;
  /** Length of the period, to normalize the value for the line and the change. */
  days: number;
}

/** The point's value normalized to NORMALIZED_DAYS, or null if it has no data. */
function normalized(point: SparklinePoint | undefined): number | null {
  return point?.value != null ? normalizeToMonth(point.value, point.days) : null;
}

/** viewBox height; the width is 100, so x coordinates are percentages. */
const HEIGHT = 30;
/** Vertical padding, so the line never touches the edges. */
const PADDING = 4;
/**
 * Minimum y-range relative to the largest value, so that small month-to-month
 * noise stays small instead of being stretched to the full height.
 */
const MIN_SPAN_RATIO = 0.3;

/**
 * The y-range [low, high] to plot `values` in: centered on the data and at least
 * MIN_SPAN_RATIO of the largest value, shifted up rather than reaching below 0.
 */
function domain(values: number[]): [number, number] {
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = Math.max(max - min, MIN_SPAN_RATIO * Math.abs(max));
  let low = (min + max) / 2 - span / 2;
  if (min >= 0 && low < 0) low = 0;
  return [low, low + span];
}

/**
 * Trend line with a marker on the last point and a hover readout; nothing until two
 * points have data. Colored by `--sparkline-color` and `--sparkline-marker-color`
 * (defaults to the line color).
 */
@customElement("gsc-sparkline")
export class GscSparkline extends LitElement {
  /** Points ordered oldest first. */
  @property({ attribute: false }) points: SparklinePoint[] = [];
  /** Accessible description of what the line shows. */
  @property() label = "";
  @state() private _hoverIndex: number | null = null;
  @query(".tooltip") private _tooltip?: HTMLElement;

  private _x(index: number): number {
    return this.points.length > 1
      ? (index / (this.points.length - 1)) * 100
      : 50;
  }

  private _y(value: number, [low, high]: [number, number]): number {
    // Only all-zero data has an empty range; draw it at the bottom
    if (high === low) return HEIGHT - PADDING;
    return (
      PADDING + (1 - (value - low) / (high - low)) * (HEIGHT - 2 * PADDING)
    );
  }

  /** Runs of consecutive points with data, as [x, y] coordinates. */
  private _segments(range: [number, number]): [number, number][][] {
    const segments: [number, number][][] = [];
    let current: [number, number][] = [];
    this.points.forEach((p, i) => {
      const value = normalized(p);
      if (value === null) {
        if (current.length) segments.push(current);
        current = [];
      } else {
        current.push([this._x(i), this._y(value, range)]);
      }
    });
    if (current.length) segments.push(current);
    return segments;
  }

  /** Of the normalized values; null if either has no data or the previous is 0. */
  private _reductionFromPrevious(index: number): number | null {
    const current = normalized(this.points[index]);
    const previous = normalized(this.points[index - 1]);
    if (current === null || previous === null || previous === 0) return null;
    return ((previous - current) / previous) * 100;
  }

  private _onPointerMove(e: PointerEvent) {
    const rect = this.getBoundingClientRect();
    const ratio = Math.min(
      Math.max((e.clientX - rect.left) / rect.width, 0),
      1,
    );
    this._hoverIndex = Math.round(ratio * (this.points.length - 1));
  }

  private _onPointerLeave() {
    this._hoverIndex = null;
  }

  override updated() {
    // A popover in the top layer, so that cards with overflow: hidden don't clip it
    const tooltip = this._tooltip;
    if (!tooltip) return;
    if (this._hoverIndex === null) {
      if (tooltip.matches(":popover-open")) tooltip.hidePopover();
      return;
    }
    const rect = this.getBoundingClientRect();
    tooltip.style.left = `${rect.left + (this._x(this._hoverIndex) / 100) * rect.width}px`;
    tooltip.style.top = `${rect.top}px`;
    if (!tooltip.matches(":popover-open")) tooltip.showPopover();
  }

  override render() {
    const values = this.points
      .map(normalized)
      .filter((v): v is number => v !== null);
    // No trend yet; the host keeps its size, so the layout doesn't shift once there is one
    if (values.length < 2) return nothing;

    const range = domain(values);
    const segments = this._segments(range);
    const last = this.points[this.points.length - 1];
    const hovered =
      this._hoverIndex !== null ? this.points[this._hoverIndex] : null;
    const change =
      this._hoverIndex !== null
        ? this._reductionFromPrevious(this._hoverIndex)
        : null;

    const dot = (index: number, cls: string) => html`
      <span
        class="dot ${cls}"
        style="${styleMap({
          left: `${this._x(index)}%`,
          top: `${(this._y(normalized(this.points[index])!, range) / HEIGHT) * 100}%`,
        })}"
      ></span>
    `;

    return html`
      <div
        class="plot"
        role="img"
        aria-label="${this.label}"
        @pointermove="${this._onPointerMove}"
        @pointerleave="${this._onPointerLeave}"
      >
        <svg
          viewBox="0 0 100 ${HEIGHT}"
          preserveAspectRatio="none"
          aria-hidden="true"
        >
          ${segments.map((seg) => {
            const line = seg
              .map(([x, y], i) => `${i ? "L" : "M"}${x},${y}`)
              .join("");
            const area = `${line}L${seg[seg.length - 1][0]},${HEIGHT}L${seg[0][0]},${HEIGHT}Z`;
            return svg`
              <path class="area" d="${area}"></path>
              <path class="line" d="${line}"></path>
            `;
          })}
        </svg>
        ${
          // Isolated points have no line, so show them as small dots
          this.points.map((p, i) =>
            p.value !== null &&
            (this.points[i - 1]?.value ?? null) === null &&
            (this.points[i + 1]?.value ?? null) === null &&
            i !== this.points.length - 1
              ? dot(i, "isolated")
              : nothing,
          )
        }
        ${last.value !== null ? dot(this.points.length - 1, "current") : nothing}
        ${
          hovered?.value != null && this._hoverIndex !== null
            ? dot(this._hoverIndex, "hover")
            : nothing
        }
      </div>
      <div class="tooltip" popover="manual">
        ${
          hovered
            ? html`<span class="tip-label">${hovered.label}</span>
                <span class="tip-value">${formatCo2(hovered.value)}</span>
                ${
                  // Equal to the value for periods of the normalized length
                  hovered.value !== null && hovered.days !== NORMALIZED_DAYS
                    ? html`<span class="tip-normalized">
                        (${formatCo2(normalized(hovered))} per
                        ${NORMALIZED_DAYS} days)
                      </span>`
                    : nothing
                }
                ${
                  change !== null
                    ? html`<span class="tip-change ${reductionTrend(change)}">
                        ${formatReductionPercent(change)}
                      </span>`
                    : nothing
                }`
            : nothing
        }
      </div>
    `;
  }

  static override styles = css`
    :host {
      display: block;
      width: 6em;
      height: 2em;
      --_color: var(--sparkline-color, var(--retit-green));
    }

    .plot {
      position: relative;
      width: 100%;
      height: 100%;
    }

    svg {
      display: block;
      width: 100%;
      height: 100%;
      overflow: visible;
    }

    .line {
      fill: none;
      stroke: var(--_color);
      stroke-width: 2px;
      stroke-linejoin: round;
      stroke-linecap: round;
      vector-effect: non-scaling-stroke;
    }

    .area {
      fill: color-mix(in srgb, var(--_color) 10%, transparent);
    }

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

    .dot.isolated {
      width: 4px;
      height: 4px;
      box-shadow: none;
    }

    .dot.current {
      background: var(--sparkline-marker-color, var(--_color));
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
      font-family: var(--retit-font);
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
