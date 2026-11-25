package io.retit.greensoftware.service;

import io.retit.greensoftware.entity.CarbonEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmissionsTest {

    @Test
    void ofProviders_addsUpTheProvidersWithData() {
        assertEquals(new Emissions(7.0, 1.0, 2.0, 4.0), Emissions.ofProviders(1.0, 2.0, 4.0));
        assertEquals(new Emissions(4.0, null, null, 4.0), Emissions.ofProviders(null, null, 4.0));
        assertEquals(new Emissions(0.0, 0.0, null, null), Emissions.ofProviders(0.0, null, null));
    }

    @Test
    void ofProviders_withoutAnyData_hasNoTotal() {
        assertEquals(Emissions.NONE, Emissions.ofProviders(null, null, null));
    }

    @Test
    void copyTo_replacesTheValuesOfAnEntry() {
        CarbonEntry entry = new CarbonEntry();
        Emissions.ofProviders(1.0, 2.0, 4.0).copyTo(entry);
        assertEquals(new Emissions(7.0, 1.0, 2.0, 4.0), Emissions.of(entry));

        Emissions.NONE.copyTo(entry);
        assertEquals(Emissions.NONE, Emissions.of(entry));
    }
}
