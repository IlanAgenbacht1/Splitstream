package dev.splitstream.race;

final class Geo {

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private Geo() {
    }

    /** Haversine great-circle distance. Accurate to well under a metre at running-ping spacing. */
    static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dPhi / 2), 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.pow(Math.sin(dLambda / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
    }
}
