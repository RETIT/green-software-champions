import { LitElement, css, html, nothing } from "lit";
import { customElement, property } from "lit/decorators.js";
import { classMap } from "lit/directives/class-map.js";
import type { Achievement } from "../types.ts";
import { groupBadge, groupTooltip } from "../util/achievements.ts";

/**
 * Achievement badge component. Renders a small pill/chip showing the icon and
 * title of a group of achievements of the same kind (see `groupAchievements`),
 * with the highest tier reached for achievements with tiers, and the number of
 * times it was awarded or that tier was reached if more than once (see
 * `groupBadge`). Supports a compact mode for inline display, which leaves out the
 * title and tier, as the tooltip names both.
 */
@customElement("gsc-achievement-badge")
export class GscAchievementBadge extends LitElement {
  /** Achievements of the same kind, latest first. */
  @property({ type: Array }) achievements: Achievement[] = [];
  @property({ type: Boolean }) compact = false;

  override render() {
    const latest = this.achievements[0];
    if (!latest) return nothing;
    const { tier, count } = groupBadge(this.achievements);

    return html`
      <span
        class="badge ${classMap({ compact: this.compact })}"
        title="${groupTooltip(this.achievements)}"
      >
        <span class="icon">${latest.badgeIcon || "🏅"}</span>
        ${!this.compact ? html`<span class="title">${latest.title}</span>` : nothing}
        ${tier && !this.compact ? html`<span class="tier">${tier}</span>` : nothing}
        ${count > 1 ? html`<span class="count">×${count}</span>` : nothing}
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
    .tier {
      font-size: 0.9em;
      font-weight: 600;
    }
    .count {
      font-size: 0.8em;
      font-weight: 600;
      font-variant-numeric: tabular-nums;
    }
    .compact .count {
      font-size: 0.6em;
      align-self: flex-end;
    }
  `;
}

declare global {
  interface HTMLElementTagNameMap {
    "gsc-achievement-badge": GscAchievementBadge;
  }
}
