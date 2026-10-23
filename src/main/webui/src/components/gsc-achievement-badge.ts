import { LitElement, css, html, nothing } from "lit";
import { customElement, property } from "lit/decorators.js";
import { classMap } from "lit/directives/class-map.js";
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
    if (!a) return nothing;

    return html`
      <span
        class="badge ${classMap({ compact: this.compact })}"
        title="${a.description || a.title}"
      >
        <span class="icon">${a.badgeIcon || "🏅"}</span>
        ${!this.compact ? html`<span class="title">${a.title}</span>` : nothing}
      </span>
    `;
  }

  static override styles = css`
    :host {
      display: inline-block;
      font-family: var(--retit-font);
    }

    .badge {
      display: inline-flex;
      align-items: center;
      gap: 0.3em;
      background: color-mix(
        in srgb,
        var(--retit-gold) 10%,
        var(--retit-surface)
      );
      border: 1px solid
        color-mix(in srgb, var(--retit-gold) 35%, var(--retit-surface));
      border-radius: 3px;
      padding: 0.15em 0.6em;
      font-size: 0.78em;
      font-weight: 400;
      color: var(--retit-gold-text);
      white-space: nowrap;
      cursor: help;
      letter-spacing: 0.01em;
    }

    .badge.compact {
      padding: 0.1em 0.25em;
      font-size: 1em;
      background: transparent;
      border: none;
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
