package io.retit.greensoftware.sync.provider;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.retit.greensoftware.emissions.PeriodRange;
import io.retit.greensoftware.team.CloudProject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Returns {@link #co2KgByPeriod} for every project, 0 for other periods. Leaves out
 * {@link #failingPeriods} like unpublished data and fails like an unreachable API
 * for {@link #failingProjectIds}.
 */
public class FakeAwsProvider extends AwsCarbonProvider {

    public final Map<String, Double> co2KgByPeriod = new HashMap<>();
    public final Set<String> failingPeriods = new HashSet<>();
    public final Set<String> failingProjectIds = new HashSet<>();
    public final List<PeriodRange> fetchedRanges = new ArrayList<>();
    public boolean fetchedInTransaction = false;
    public boolean enabled = true;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Map<String, List<CarbonData>> fetchCarbonData(List<CloudProject> projects, PeriodRange range) {
        fetchedRanges.add(range);
        fetchedInTransaction |= QuarkusTransaction.isActive();
        if (projects.stream().anyMatch(p -> failingProjectIds.contains(p.projectId))) {
            throw new CarbonDataUnavailableException("AWS unavailable for some projects");
        }
        Map<String, List<CarbonData>> results = new HashMap<>();
        for (String period : range.periods()) {
            if (!failingPeriods.contains(period)) {
                double co2Kg = co2KgByPeriod.getOrDefault(period, 0.0);
                results.put(period, projects.stream()
                        .map(p -> new CarbonData(p.projectId, co2Kg))
                        .toList());
            }
        }
        return results;
    }
}
