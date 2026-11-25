package io.retit.greensoftware.web.model;

import io.retit.greensoftware.emissions.Periods;
import io.retit.greensoftware.leaderboard.CompanySummary;
import io.retit.greensoftware.web.format.Format;
import io.retit.greensoftware.web.format.Trend;

/** The company-wide figures above the views. */
public record Summary(
        String period, String previousPeriod,
        String total, Sparkline sparkline, int historyMonths,
        Trend trend, String reduction
) {

    public static Summary of(CompanySummary summary) {
        int historyMonths = summary.history().size();
        return new Summary(
                Format.period(summary.period()),
                Format.period(Periods.previous(summary.period())),
                Format.co2(summary.totalCo2Kg()),
                Sparkline.of("Company CO₂e over the last " + historyMonths + " months", summary.history()),
                historyMonths,
                Trend.of(summary.reductionPercent()),
                Format.reduction(summary.reductionPercent()));
    }
}
