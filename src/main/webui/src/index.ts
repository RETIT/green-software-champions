import "./components/gsc-sparkline.ts";
import "./components/gsc-world-map.ts";

const periodForm = document.querySelector<HTMLFormElement>(".period-form");
periodForm?.addEventListener("change", () => periodForm.requestSubmit());

// The back button may restore the page with the period that was picked to leave it
window.addEventListener("pageshow", (e) => {
  if (e.persisted) periodForm?.reset();
});

/** Chart.js is most of the code, so it is loaded once a card with a chart is open. */
async function loadChart(card: Element) {
  const chart = card.querySelector("gsc-history-chart");
  if (!chart) return;
  try {
    await import("./components/gsc-history-chart.ts");
  } catch (e) {
    // E.g. the session has expired, or a new version has replaced the file
    console.error("Loading the chart failed.", e);
    showChartError(chart);
  }
}

/** A link rather than a reload right away, which would close the card and might fail again. */
function showChartError(chart: Element) {
  const reload = document.createElement("a");
  reload.href = location.pathname + location.search;
  reload.textContent = "Reload the page";
  chart.replaceChildren("The chart could not be loaded. ", reload);
}

// Captured, as toggle events don't bubble
document.addEventListener(
  "toggle",
  ({ target }) => {
    if (target instanceof HTMLDetailsElement && target.open) {
      void loadChart(target);
    }
  },
  { capture: true },
);
// Opened before this script was loaded
document
  .querySelectorAll("details[open]")
  .forEach((card) => void loadChart(card));
