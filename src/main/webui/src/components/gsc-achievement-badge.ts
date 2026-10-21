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

  static override styles = css`
    :host {
      display: inline-block;
      font-family: "Source Sans 3", "Source Sans Pro", Helvetica, sans-serif;
    }

    .badge {
      display: inline-flex;
      align-items: center;
      gap: 0.3em;
      background: color-mix(in srgb, var(--retit-gold) 10%, var(--retit-white));
      border: 1px solid color-mix(in srgb, var(--retit-gold) 35%, var(--retit-white));
      border-radius: 3px;
      padding: 0.15em 0.6em;
      font-size: 0.78em;
      font-weight: 400;
      color: var(--retit-gold-text);
      white-space: nowrap;
      cursor: help;
      transition: all 0.2s ease;
      letter-spacing: 0.01em;
    }

    .badge:hover {
      background: color-mix(in srgb, var(--retit-gold) 18%, var(--retit-white));
      border-color: color-mix(in srgb, var(--retit-gold) 60%, var(--retit-white));
      box-shadow: 0 1px 4px color-mix(in srgb, var(--retit-gold) 20%, transparent);
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
}

declare global {
  interface HTMLElementTagNameMap {
    "gsc-achievement-badge": GscAchievementBadge;
  }
}
