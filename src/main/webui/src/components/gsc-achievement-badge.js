import { LitElement, html, css } from 'lit';

/**
 * Achievement badge component. Renders a small pill/chip showing the
 * achievement icon and title. Supports a compact mode for inline display.
 */
class GscAchievementBadge extends LitElement {
  static properties = {
    achievement: { type: Object },
    compact: { type: Boolean },
  };

  static styles = css`
    :host { display: inline-block; }

    .badge {
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
      background: rgba(255, 215, 0, 0.1);
      border: 1px solid rgba(255, 215, 0, 0.3);
      border-radius: 20px;
      padding: 0.2rem 0.6rem;
      font-size: 0.8rem;
      color: #ffd700;
      white-space: nowrap;
      cursor: help;
      transition: all 0.2s;
    }

    .badge:hover {
      background: rgba(255, 215, 0, 0.2);
      transform: scale(1.05);
    }

    .badge.compact {
      padding: 0.15rem 0.4rem;
      font-size: 1rem;
      background: transparent;
      border: none;
    }

    .badge.compact:hover {
      transform: scale(1.2);
    }

    .icon { font-size: 1em; }
    .title { font-size: 0.75em; }
  `;

  render() {
    const a = this.achievement;
    if (!a) return html``;

    return html`
      <span class="badge ${this.compact ? 'compact' : ''}"
            title="${a.description || a.title}">
        <span class="icon">${a.badgeIcon || '🏅'}</span>
        ${!this.compact ? html`<span class="title">${a.title}</span>` : ''}
      </span>
    `;
  }
}

customElements.define('gsc-achievement-badge', GscAchievementBadge);
