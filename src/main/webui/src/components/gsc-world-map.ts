import { LitElement, css, html } from "lit";
import { customElement, state } from "lit/decorators.js";
import { classMap } from "lit/directives/class-map.js";
import { styleMap } from "lit/directives/style-map.js";

/**
 * Adds tooltips to the prerendered map it contains. Their contents are prerendered too,
 * as `<template>`s with the `data-rank` of their bubbles.
 */
@customElement("gsc-world-map")
export class GscWorldMap extends LitElement {
  @state() private _visible = false;
  @state() private _position = { left: "0", top: "0" };
  @state() private _content: DocumentFragment | null = null;
  private _hovered: Element | null = null;

  constructor() {
    super();
    this.addEventListener("mousemove", this._onMouseMove);
    this.addEventListener("mouseleave", this._onMouseLeave);
  }

  private _onMouseMove(e: MouseEvent) {
    const bubble =
      e.target instanceof Element
        ? e.target.closest<SVGGElement>(".team-bubble")
        : null;
    this._visible = bubble !== null;
    if (!bubble) return;

    const rect = this.getBoundingClientRect();
    this._position = {
      left: `${e.clientX - rect.left + 12}px`,
      top: `${e.clientY - rect.top + 12}px`,
    };
    if (bubble !== this._hovered) {
      this._hovered = bubble;
      const template = this.querySelector<HTMLTemplateElement>(
        `template[data-rank="${bubble.dataset.rank}"]`,
      );
      this._content = template
        ? (template.content.cloneNode(true) as DocumentFragment)
        : null;
    }
  }

  private _onMouseLeave() {
    this._visible = false;
  }

  override render() {
    return html`
      <slot></slot>
      <div
        class="tooltip ${classMap({ hidden: !this._visible })}"
        style="${styleMap(this._position)}"
      >
        ${this._content}
      </div>
    `;
  }

  /** The map is styled by map.css. */
  static override styles = css`
    .tooltip {
      position: absolute;
      background: var(--retit-surface);
      border: 1px solid var(--retit-border);
      border-radius: 3px;
      padding: 0.7em 1em;
      pointer-events: none;
      font-size: 0.8em;
      min-width: 160px;
      transition: opacity 0.15s;
      z-index: 10;
      box-shadow: var(--retit-shadow-raised);
    }

    .tooltip.hidden {
      opacity: 0;
    }

    .tooltip-name {
      font-weight: 600;
      color: var(--retit-text);
      margin-bottom: 0.35em;
      font-size: 0.95em;
    }

    .tooltip-row {
      display: flex;
      justify-content: space-between;
      gap: 0.75em;
      color: var(--retit-text-light);
      font-weight: 400;
      margin-top: 0.15em;
      font-size: 0.9em;
    }

    .tooltip-achievements {
      justify-content: flex-start;
      gap: 0.25em;
      margin-top: 0.3em;
    }

    .tooltip-row span:last-child {
      font-weight: 600;
      color: var(--retit-green-dark);
    }

    .tooltip-row span.negative {
      color: var(--retit-red);
    }
  `;
}

declare global {
  interface HTMLElementTagNameMap {
    "gsc-world-map": GscWorldMap;
  }
}
