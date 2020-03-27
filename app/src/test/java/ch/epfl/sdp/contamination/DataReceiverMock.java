package ch.epfl.sdp.contamination;

public class DataReceiverMock {

}

/*
public class DataReceiverMock implements DataReceiver {
    private Map<Date, List<? extends Carrier>> wentThere;

    @TargetApi(17)
    private Location buildLocation(double latitude, double longitude) {
        Location l = new Location(LocationManager.GPS_PROVIDER);
        l.setLatitude(latitude);
        l.setLongitude(longitude);
        l.setTime(System.currentTimeMillis());
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
            // Also need to set the et field
            l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        }
        l.setAltitude(400);
        l.setAccuracy(1);
        return l;
    }

    private Location crowdedPlace = buildLocation(10, 20);
    private Location emptyPlace = buildLocation(1, 2);

    DataReceiverMock() {
        wentThere = new HashMap<>();
        wentThere.put(new Date(2020, 03, 24, 00, 48), Lists.newArrayList(new Layman(Carrier.InfectionStatus.HEALTHY)));
        wentThere.put(new Date(2020, 03, 24, 00, 30), Lists.newArrayList(new Layman(Carrier.InfectionStatus.HEALTHY)));
        wentThere.put(new Date(2020, 03, 24, 00, 20), Lists.newArrayList(new Layman(Carrier.InfectionStatus.IMMUNE)));
        wentThere.put(new Date(2020, 03, 24, 00, 15), Lists.newArrayList(new Layman(Carrier.InfectionStatus.HEALTHY)));
        wentThere.put(new Date(2020, 03, 23, 23, 48), Lists.newArrayList(new Layman(Carrier.InfectionStatus.HEALTHY)));
    }

    @Override
    public Set<? extends Carrier> getUserNearby(Location location, Date date) {
        Set<Carrier> res = new HashSet<>();
        if (location == crowdedPlace) {
            for (Map.Entry<Date, List<? extends Carrier>> e : wentThere.entrySet()) {
                if (e.getKey().after(date)) {
                    for (Carrier p : e.getValue()) {
                        res.add(p);
                    }
                }
            }
        }

        return res;
    }

    @Override
    public Set<Carrier> getUserNearbyDuring(Location location, Date startDate, Date endDate) {
        Set<Carrier> res = new HashSet<>();
        if (location == crowdedPlace) {
            for (Map.Entry<Date, List<? extends Carrier>> e : wentThere.entrySet()) {
                if (e.getKey().after(startDate) && e.getKey().before(endDate)) {
                    for (Carrier p : e.getValue()) {
                        res.add(p);
                    }
                }
            }
        }

        return res;
    }

    @Override
    public void getUserNearby(Location location, Date date, Callback<Set<? extends Carrier>> callback) {

    }

    @Override
    public void getUserNearbyDuring(Location location, Date startDate, Date endDate, Callback<Map<? extends Carrier, Integer>> callback) {

    }

    @Override
    public Location getMyLocationAtTime(Date date) {
        return crowdedPlace;
    }
}

 */