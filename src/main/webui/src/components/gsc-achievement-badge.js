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
    :host { display: inline-block; font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; }

    .badge {
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
      background: rgba(251, 191, 36, 0.08);
      border: 1px solid rgba(251, 191, 36, 0.22);
      border-radius: 20px;
      padding: 0.2rem 0.65rem;
      font-size: 0.72rem;
      font-weight: 600;
      color: #FBBF24;
      white-space: nowrap;
      cursor: help;
      transition: all 0.15s ease;
      letter-spacing: 0.01em;
    }

    .badge:hover {
      background: rgba(251, 191, 36, 0.15);
      border-color: rgba(251, 191, 36, 0.4);
    }

    .badge.compact {
      padding: 0.1rem 0.3rem;
      font-size: 1rem;
      background: transparent;
      border: none;
      gap: 0;
    }

    .badge.compact:hover {
      transform: scale(1.15);
    }

    .icon { font-size: 1em; line-height: 1; }
    .title { font-size: 0.72em; }
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
