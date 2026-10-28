import {
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  Legend,
  LinearScale,
  Tooltip,
  type ScriptableContext,
} from "chart.js";
import { LitElement, css, html } from "lit";
import { customElement, property, query } from "lit/decorators.js";
import type { HistoryPoint } from "../types.ts";
import { formatCo2, formatPeriod } from "../util/format.ts";

Chart.register(
  BarController,
  BarElement,
  CategoryScale,
  LinearScale,
  Legend,
  Tooltip,
);

const PROVIDERS = [
  { label: "AWS", key: "awsCo2Kg", color: "--provider-aws" },
  { label: "Azure", key: "azureCo2Kg", color: "--provider-azure" },
  { label: "GCP", key: "gcpCo2Kg", color: "--provider-gcp" },
] as const;

const darkMode = window.matchMedia("(prefers-color-scheme: dark)");

/**
 * Stacked bar chart of the monthly CO₂e per cloud provider.
 */
@customElement("gsc-history-chart")
export class GscHistoryChart extends LitElement {
  /** Points ordered oldest first. */
  @property({ attribute: false }) history: HistoryPoint[] = [];
  @query("canvas") private _canvas!: HTMLCanvasElement;
  private _chart?: Chart<"bar", (number | null)[], string>;

  private _onThemeChange = () => this._renderChart();

  override connectedCallback() {
    super.connectedCallback();
    darkMode.addEventListener("change", this._onThemeChange);
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
    darkMode.removeEventListener("change", this._onThemeChange);
    this._chart?.destroy();
    this._chart = undefined;
  }

  override updated() {
    this._renderChart();
  }

  /** Index of the top-most dataset with a value in the given bar. */
  private _topDataset(dataIndex: number): number {
    for (let i = PROVIDERS.length - 1; i >= 0; i--) {
      if (this.history[dataIndex]?.[PROVIDERS[i].key]) return i;
    }
    return -1;
  }

  /**
   * (Re)creates the chart. Chart.js draws on a canvas, so the theme tokens are
   * resolved to concrete colors here and again whenever the color scheme changes.
   */
  private _renderChart() {
    this._chart?.destroy();
    const style = getComputedStyle(this);
    const token = (name: string) => style.getPropertyValue(name).trim();
    const surface = token("--retit-surface");
    const muted = token("--retit-text-light");
    const font = { family: token("--retit-font"), size: 11 };

    const isTop = (ctx: ScriptableContext<"bar">) =>
      ctx.datasetIndex === this._topDataset(ctx.dataIndex);

    this._chart = new Chart(this._canvas, {
      type: "bar",
      data: {
        labels: this.history.map((p) => p.period),
        datasets: PROVIDERS.map((provider) => ({
          label: provider.label,
          data: this.history.map((p) => p[provider.key]),
          backgroundColor: token(provider.color),
          // Keeps the bars slim while there are only a few months of history
          maxBarThickness: 48,
          // Square at the baseline, rounded data-end on the top-most segment,
          // and a surface-colored gap between stacked segments.
          borderRadius: (ctx: ScriptableContext<"bar">) =>
            isTop(ctx)
              ? { topLeft: 4, topRight: 4, bottomLeft: 0, bottomRight: 0 }
              : 0,
          borderSkipped: false,
          borderWidth: (ctx: ScriptableContext<"bar">) =>
            isTop(ctx) ? 0 : { top: 2 },
          borderColor: surface,
        })),
      },
      options: {
        responsive: true,
        animation: false,
        maintainAspectRatio: false,
        interaction: { mode: "index", intersect: false },
        scales: {
          x: {
            stacked: true,
            grid: { display: false },
            border: { color: token("--retit-border") },
            ticks: {
              color: muted,
              font,
              callback: (_, i) => formatPeriod(this.history[i]?.period ?? ""),
            },
          },
          y: {
            stacked: true,
            beginAtZero: true,
            border: { display: false },
            grid: { color: token("--retit-border") },
            ticks: {
              color: muted,
              font,
              maxTicksLimit: 4,
              callback: (value) =>
                value === 0 ? "0" : formatCo2(Number(value)),
            },
          },
        },
        plugins: {
          legend: {
            position: "top",
            align: "end",
            labels: {
              color: token("--retit-text"),
              font,
              boxWidth: 10,
              boxHeight: 10,
              useBorderRadius: true,
              borderRadius: 2,
            },
          },
          tooltip: {
            backgroundColor: surface,
            titleColor: token("--retit-text"),
            bodyColor: token("--retit-text"),
            footerColor: token("--retit-text"),
            borderColor: token("--retit-border"),
            borderWidth: 1,
            titleFont: { ...font, weight: "bold" },
            bodyFont: font,
            footerFont: { ...font, weight: "bold" },
            boxPadding: 4,
            // Skip providers without data instead of listing them as "—"
            filter: (item) => item.raw !== null,
            callbacks: {
              title: ([item]) =>
                item
                  ? formatPeriod(this.history[item.dataIndex].period, "long")
                  : "",
              label: (item) =>
                ` ${item.dataset.label}: ${formatCo2(item.raw as number)}`,
              footer: ([item]) =>
                item
                  ? `Total: ${formatCo2(this.history[item.dataIndex].totalCo2Kg)}`
                  : "",
            },
          },
        },
      },
    });
  }

  override render() {
    return html`<canvas
      role="img"
      aria-label="Monthly CO₂e by cloud provider"
    ></canvas>`;
  }

  static override styles = css`
    :host {
      display: block;
      position: relative;
      height: 200px;
    }

    /* Out of flow, so the canvas size Chart.js sets (rounded to device pixels)
       never feeds back into the layout; Chart.js sizes it from the host. */
    canvas {
      position: absolute;
      inset: 0;
    }
  `;
}

declare global {
  interface HTMLElementTagNameMap {
    "gsc-history-chart": GscHistoryChart;
  }
}
