package com.bettercontent.railbeetle.navigation;

public record RouteSupplyStatus(int missingRails, int missingSupports, boolean missingFuel) {
    public static final RouteSupplyStatus READY = new RouteSupplyStatus(0, 0, false);

    public RouteSupplyStatus {
        missingRails = Math.max(0, missingRails);
        missingSupports = Math.max(0, missingSupports);
    }

    public boolean hasMissing() {
        return missingRails > 0 || missingSupports > 0 || missingFuel;
    }
}
