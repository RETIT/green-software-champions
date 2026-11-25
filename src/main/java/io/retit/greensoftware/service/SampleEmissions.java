package io.retit.greensoftware.service;

import io.retit.greensoftware.entity.CloudProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Generates a team's sample emissions: {@code monthlyReduction} is the average reduction
 * per month (negative = growing), {@code wiggle} the frequency of the noise on top. The
 * older periods are derived from the latest one's emissions per provider (null if unused).
 */
record SampleEmissions(
        Double awsLatest, Double azureLatest, Double gcpLatest,
        double monthlyReduction, double wiggle
) {

    /**
     * Random, but seeded with the team name, so that a team always gets the same values
     * and later months continue its curve.
     */
    static SampleEmissions of(String teamName, Set<CloudProvider> providers) {
        Random random = new Random(teamName.hashCode());
        // Drawn even for unused providers, so adding a project leaves the other values unchanged
        Double aws   = latestCo2Kg(random, providers.contains(CloudProvider.AWS));
        Double azure = latestCo2Kg(random, providers.contains(CloudProvider.AZURE));
        Double gcp   = latestCo2Kg(random, providers.contains(CloudProvider.GCP));
        double monthlyReduction = -0.015 + random.nextDouble() * 0.065; // -1.5 % to 5 %
        double wiggle = 0.8 + random.nextDouble() * 1.7;                // 0.8 to 2.5
        return new SampleEmissions(aws, azure, gcp, monthlyReduction, wiggle);
    }

    /** A provider's emissions in the latest period, 40 to 220 kg, or null if unused. */
    private static Double latestCo2Kg(Random random, boolean used) {
        double co2Kg = Math.round((40 + random.nextDouble() * 180) * 10) / 10.0;
        return used ? co2Kg : null;
    }

    /** The emissions by period in consecutive {@code periods}, oldest first. */
    Map<String, Emissions> in(List<String> periods) {
        Map<String, Emissions> emissions = new LinkedHashMap<>();
        for (int i = 0; i < periods.size(); i++) {
            int monthsAgo = periods.size() - 1 - i;
            double factor = Math.pow(1 + monthlyReduction, monthsAgo)
                    * (1 + 0.08 * Math.sin(monthsAgo * wiggle));
            emissions.put(periods.get(i), Emissions.ofProviders(
                    scale(awsLatest, factor), scale(azureLatest, factor), scale(gcpLatest, factor)));
        }
        return emissions;
    }

    /** Scales a provider value by {@code factor}, rounded to one decimal. */
    private static Double scale(Double value, double factor) {
        return value == null ? null : Math.round(value * factor * 10) / 10.0;
    }
}
