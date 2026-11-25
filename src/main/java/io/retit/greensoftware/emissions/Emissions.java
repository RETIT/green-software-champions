package io.retit.greensoftware.emissions;

import java.util.Objects;
import java.util.stream.Stream;

/** Emissions in one period, null for each provider without data. */
public record Emissions(Double totalCo2Kg, Double awsCo2Kg, Double azureCo2Kg, Double gcpCo2Kg) {

    public static final Emissions NONE = new Emissions(null, null, null, null);

    /**
     * With their sum as the total, null rather than 0 without any provider data, so it is not
     * mistaken for zero emissions.
     */
    public static Emissions ofProviders(Double awsCo2Kg, Double azureCo2Kg, Double gcpCo2Kg) {
        Double totalCo2Kg = Stream.of(awsCo2Kg, azureCo2Kg, gcpCo2Kg)
                .filter(Objects::nonNull)
                .reduce(Double::sum)
                .orElse(null);
        return new Emissions(totalCo2Kg, awsCo2Kg, azureCo2Kg, gcpCo2Kg);
    }

    public static Emissions of(CarbonEntry entry) {
        return new Emissions(entry.totalCo2Kg, entry.awsCo2Kg, entry.azureCo2Kg, entry.gcpCo2Kg);
    }

    public void copyTo(CarbonEntry entry) {
        entry.totalCo2Kg = totalCo2Kg;
        entry.awsCo2Kg = awsCo2Kg;
        entry.azureCo2Kg = azureCo2Kg;
        entry.gcpCo2Kg = gcpCo2Kg;
    }
}
