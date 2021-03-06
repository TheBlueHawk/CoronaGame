package ch.epfl.sdp.contamination.databaseIO;

import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.GeoPoint;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import ch.epfl.sdp.contamination.Carrier;
import ch.epfl.sdp.contamination.Neighbor;

import static ch.epfl.sdp.firestore.FirestoreInteractor.documentReference;
import static ch.epfl.sdp.firestore.FirestoreInteractor.getTag;
import static ch.epfl.sdp.firestore.FirestoreLabels.GEOPOINT_TAG;
import static ch.epfl.sdp.firestore.FirestoreLabels.ILLNESS_PROBABILITY_TAG;
import static ch.epfl.sdp.firestore.FirestoreLabels.INFECTION_STATUS_TAG;
import static ch.epfl.sdp.firestore.FirestoreLabels.UNIQUE_ID_TAG;
import static ch.epfl.sdp.firestore.FirestoreLabels.UNIXTIME_TAG;
import static ch.epfl.sdp.firestore.FirestoreLabels.privateUserFolder;
import static ch.epfl.sdp.firestore.FirestoreLabels.publicUserFolder;

public class ConcreteDataReceiver implements DataReceiver {

    private GridFirestoreInteractor interactor;

    public ConcreteDataReceiver(GridFirestoreInteractor gridInteractor) {
        this.interactor = gridInteractor;
    }

    @VisibleForTesting
    void setInteractor(GridFirestoreInteractor interactor) {
        this.interactor = interactor;
    }

    @Override
    public CompletableFuture<Set<Carrier>> getUserNearby(Location location, Date date) {

        return interactor.gridRead(location, date.getTime()).thenApply(stringMapMap -> {
            Set<Carrier> carriers = new HashSet<>();
            for (Map.Entry<String, Map<String, Object>> doc : stringMapMap.entrySet()) {
                carriers.add(createNeighbor(doc)
                );
            }
            return carriers;
        }).exceptionally(exception -> Collections.emptySet());
    }

    @NotNull
    private Neighbor createNeighbor(Map.Entry<String, Map<String, Object>> doc) {
        return new Neighbor(Enum.valueOf(Carrier.InfectionStatus.class,
                getTag(doc.getValue(), INFECTION_STATUS_TAG, String.class)),
                getTag(doc.getValue(), ILLNESS_PROBABILITY_TAG, Double.class).floatValue(),
                getTag(doc.getValue(), UNIQUE_ID_TAG, String.class));
    }

    private Set<Long> filterValidTimes(long startDate, long endDate, Map<String, Map<String, Object>> snapshot) {
        Set<Long> validTimes = new HashSet<>();
        for (Map.Entry<String, Map<String, Object>> q : snapshot.entrySet()) {
            try {
                long time = Long.decode(getTag(q.getValue(), UNIXTIME_TAG, String.class));

                if (startDate <= time && time <= endDate) {
                    validTimes.add(time);
                }
            } catch (NumberFormatException ignore) {
            }
        }
        return validTimes;
    }

    private Map<Carrier, Integer> collectCarriersMetDuringInterval(Stream<Map<String, Map<String, Object>>> results) {

        Map<Carrier, Integer> metDuringInterval = new HashMap<>();
        results.forEach(res -> {
            for (Map.Entry<String, Map<String, Object>> doc : res.entrySet()) {
                Carrier c = createNeighbor(doc);

                Log.e("MET_DURING_INTERVAL", c.getInfectionStatus() + ", " + c.getIllnessProbability());

                int numberOfMeetings = 1;
                if (metDuringInterval.containsKey(c)) {
                    numberOfMeetings += metDuringInterval.get(c);
                }
                metDuringInterval.put(c, numberOfMeetings);
            }
        });

        return metDuringInterval;
    }

    @Override
    public CompletableFuture<Map<Carrier, Integer>> getUserNearbyDuring(Location location,
                                                                        Date startDate, Date endDate) {
        return interactor.getTimes(location)
                .thenApply(stringMapMap -> filterValidTimes(startDate.getTime(), endDate.getTime(), stringMapMap))
                .thenApply(filtered -> {
                    if (filtered.size() > 0) {
                        Log.e("FILTERED_TIMES", Long.toString(filtered.iterator().next()));
                    }
                    return filtered;
                })
                .thenCompose(validTimes -> {
                    // Retrieve all the Carrier met at each time
                    List<CompletableFuture<Map<String, Map<String, Object>>>> metDuringSlices = new ArrayList<>();
                    validTimes.forEach(tm -> metDuringSlices.add(interactor.gridRead(location, tm)));

                    CompletableFuture<Void> carriersForTimeSlice = CompletableFuture.allOf(metDuringSlices.toArray(new CompletableFuture[metDuringSlices.size()]));

                    return carriersForTimeSlice.thenApply(ignoredVoid -> {
                        // Create a Map containing all the Carriers met during this interval
                        Stream<Map<String, Map<String, Object>>> results = metDuringSlices.stream().map(CompletableFuture::join);
                        return collectCarriersMetDuringInterval(results);
                    });
                })
                .exceptionally(exception -> Collections.emptyMap());
    }

    @Override
    public CompletableFuture<Location> getMyLastLocation(String accountId) {
        return interactor.readLastLocation(accountId).thenApply(result -> {
            if (result.entrySet().iterator().hasNext()) {
                GeoPoint geoPoint = getTag(result, GEOPOINT_TAG, GeoPoint.class);
                Location location = new Location(LocationManager.GPS_PROVIDER);
                location.setLatitude(geoPoint.getLatitude());
                location.setLongitude(geoPoint.getLongitude());
                return location;
            } else {
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> getNumberOfSickNeighbors(String userId) {
        DocumentReference ref = documentReference(publicUserFolder, userId);
        return interactor.readDocument(ref);
    }

    public CompletableFuture<Map<String, Object>> getRecoveryCounter(String userId) {
        return interactor.readDocument(documentReference(privateUserFolder, userId));
    }
}
