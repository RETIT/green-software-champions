import { LitElement, css, html } from "lit";
import { customElement, property } from "lit/decorators.js";
import type { Achievement } from "../types.ts";

/**
 * Achievement badge component. Renders a small pill/chip showing the
 * achievement icon and title. Supports a compact mode for inline display.
 */
@customElement("gsc-achievement-badge")
export class GscAchievementBadge extends LitElement {
  @property({ type: Object }) achievement?: Achievement;
  @property({ type: Boolean }) compact = false;

  static override styles = css`
    :host {
      display: inline-block;
      font-family: "Source Sans 3", "Source Sans Pro", Helvetica, sans-serif;
    }

    .badge {
      display: inline-flex;
      align-items: center;
      gap: 0.3em;
      background: rgba(230, 168, 23, 0.1);
      border: 1px solid rgba(230, 168, 23, 0.35);
      border-radius: 3px;
      padding: 0.15em 0.6em;
      font-size: 0.78em;
      font-weight: 400;
      color: #9a6e00;
      white-space: nowrap;
      cursor: help;
      transition: all 0.2s ease;
      letter-spacing: 0.01em;
    }

    .badge:hover {
      background: rgba(230, 168, 23, 0.18);
      border-color: rgba(230, 168, 23, 0.6);
      box-shadow: 0 1px 4px rgba(230, 168, 23, 0.2);
    }

    .badge.compact {
      padding: 0.1em 0.25em;
      font-size: 1em;
      background: transparent;
      border: none;
    }

    .badge.compact:hover {
      transform: scale(1.15);
    }

    .icon {
      font-size: 1em;
      line-height: 1;
    }
    .title {
      font-size: 0.9em;
    }
  `;

  override render() {
    const a = this.achievement;
    if (!a) return html``;

    return html`
      <span
        class="badge ${this.compact ? "compact" : ""}"
        title="${a.description || a.title}"
      >
        <span class="icon">${a.badgeIcon || "🏅"}</span>
        ${!this.compact ? html`<span class="title">${a.title}</span>` : ""}
      </span>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    "gsc-achievement-badge": GscAchievementBadge;
  }
}
