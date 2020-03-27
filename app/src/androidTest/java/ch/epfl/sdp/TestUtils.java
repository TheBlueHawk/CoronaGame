package ch.epfl.sdp;

import android.annotation.TargetApi;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.SystemClock;

public class TestUtils {

    @TargetApi(17)
    public static Location buildLocation(double latitude, double longitude) {
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

}
