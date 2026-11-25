package io.retit.greensoftware.dto;

import io.retit.greensoftware.service.HistoryPoint;

/** Emissions of one period; all values are null if there is no data for it. */
public class CarbonHistoryPointDto {

    /** YYYY-MM */
    public String period;

    public Double totalCo2Kg;
    public Double awsCo2Kg;
    public Double azureCo2Kg;
    public Double gcpCo2Kg;

    public static CarbonHistoryPointDto from(HistoryPoint point) {
        CarbonHistoryPointDto dto = new CarbonHistoryPointDto();
        dto.period = point.period();
        dto.totalCo2Kg = point.emissions().totalCo2Kg();
        dto.awsCo2Kg = point.emissions().awsCo2Kg();
        dto.azureCo2Kg = point.emissions().azureCo2Kg();
        dto.gcpCo2Kg = point.emissions().gcpCo2Kg();
        return dto;
    }
}
