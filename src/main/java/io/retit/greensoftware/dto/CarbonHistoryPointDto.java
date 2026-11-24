package io.retit.greensoftware.dto;

/** Emissions of one period; all values are null if there is no data for it. */
public class CarbonHistoryPointDto {

    /** YYYY-MM */
    public String period;

    public Double totalCo2Kg;
    public Double awsCo2Kg;
    public Double azureCo2Kg;
    public Double gcpCo2Kg;
}
