package ch.epfl.sdp.contamination;

import android.location.Location;

import androidx.test.rule.ActivityTestRule;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import ch.epfl.sdp.R;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.assertThat;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static ch.epfl.sdp.TestTools.sleep;
import static ch.epfl.sdp.TestUtils.buildLocation;
import static org.hamcrest.CoreMatchers.is;

public class RealGridSenderTest {

    @Rule
    public final ActivityTestRule<DataExchangeActivity> mActivityRule = new ActivityTestRule<>(DataExchangeActivity.class);

    OnSuccessListener exchangeSucceeded;
    OnFailureListener exchangeFailed;

    private void resetRealSenderAndReceiver() {
        GridFirestoreInteractor gridInteractor = new GridFirestoreInteractor();
        mActivityRule.getActivity().getService().setReceiver(new ConcreteDataReceiver(gridInteractor));
        mActivityRule.getActivity().getService().setSender(new ConcreteCachingDataSender(gridInteractor));
    }

    @Before
    public void setupTests() {
        exchangeSucceeded = mActivityRule.getActivity().successListener;
        exchangeFailed = mActivityRule.getActivity().failureListener;
        resetRealSenderAndReceiver();
    }


    @Test
    public void complexQueriesComeAndGoFromServer() throws Throwable {
        // The following test uses the actual Firestore

        Carrier aFakeCarrier = new Layman(Carrier.InfectionStatus.UNKNOWN, 0.2734f);
        Carrier trulyHealthy = new Layman(Carrier.InfectionStatus.HEALTHY, 0f);

        Location somewhereInTheWorld = buildLocation(12, 73);

        Date rightNow = new Date(System.currentTimeMillis());
        Date aLittleLater = new Date(rightNow.getTime() + 10);

        mActivityRule.getActivity().getService().getSender().registerLocation(
                aFakeCarrier,
                somewhereInTheWorld,
                rightNow,
                exchangeSucceeded,
                exchangeFailed);
        mActivityRule.getActivity().getService().getSender().registerLocation(
                trulyHealthy,
                somewhereInTheWorld,
                aLittleLater);

        sleep();

        onView(withId(R.id.exchange_status)).check(matches(withText("EXCHANGE Succeeded")));

        Map<Carrier, Integer> result = getBackRangeData(somewhereInTheWorld, rightNow, aLittleLater);

        assertThat(result.size(), is(2));
        assertThat(result.containsKey(aFakeCarrier), is(true));
        assertThat(result.containsKey(trulyHealthy), is(true));
        assertThat(result.get(aFakeCarrier), is(1));
        assertThat(result.get(trulyHealthy), is(1));

    }

    private Map<Carrier, Boolean> getBackSliceData(Location somewhere, Date rightNow) throws Throwable {
        Map<Carrier, Boolean> result = new ConcurrentHashMap<>();

        AtomicBoolean done = new AtomicBoolean();
        done.set(false);

        mActivityRule.runOnUiThread(() -> mActivityRule.getActivity().getService().getReceiver().getUserNearby(somewhere, rightNow, people -> {
            for (Carrier c : people) {
                result.put(c, false);
            }
            done.set(true);
        }));

        while (!done.get()) { } // Busy wait

        return result;
    }

    @Test
    public void dataReallyComeAndGoFromServer() throws Throwable {
        // The following test uses the actual Firestore

        Carrier aFakeCarrier = new Layman(Carrier.InfectionStatus.UNKNOWN, 0.2734f);
        Date rightNow = new Date(System.currentTimeMillis());

        mActivityRule.getActivity().getService().getSender().registerLocation(
                aFakeCarrier,
                buildLocation(12, 73),
                rightNow,
                exchangeSucceeded,
                exchangeFailed);

        Thread.sleep(2000);

        Map<Carrier, Boolean> result = getBackSliceData(buildLocation(12, 73), rightNow);

        assertThat(result.size(), is(1));
        assertThat(result.containsKey(aFakeCarrier), is(true));
    }

    private Map<Carrier, Integer> getBackRangeData(Location somewhere, Date rangeStart, Date rangeEnd) throws Throwable {
        AtomicBoolean done = new AtomicBoolean(false);

        Map<Carrier, Integer> result = new ConcurrentHashMap<>();

        // Get data back
        mActivityRule.runOnUiThread(() -> mActivityRule.getActivity().getService().getReceiver().getUserNearbyDuring(somewhere, rangeStart, rangeEnd, contactFrequency -> {
            result.putAll(contactFrequency);
            done.set(true);
        }));

        while (!done.get()) { } // Busy wait

        return result;
    }

    @Test
    public void repetitionsOfSameCarrierAreDetected() throws Throwable {
        // The following test uses the actual Firestore

        Carrier aFakeCarrier = new Layman(Carrier.InfectionStatus.UNKNOWN, 0.2734f);

        Location somewhereInTheWorld = buildLocation(12, 73);

        Date rightNow = new Date(System.currentTimeMillis());
        Date aLittleLater = new Date(rightNow.getTime() + 1000);

        mActivityRule.runOnUiThread(() -> {
            mActivityRule.getActivity().getService().getSender().registerLocation(
                    aFakeCarrier,
                    somewhereInTheWorld,
                    rightNow,
                    exchangeSucceeded,
                    exchangeFailed);
            mActivityRule.getActivity().getService().getSender().registerLocation(
                    aFakeCarrier,
                    somewhereInTheWorld,
                    aLittleLater);
        });

        Thread.sleep(1000);

        onView(withId(R.id.exchange_status)).check(matches(withText("EXCHANGE Succeeded")));

        Map<Carrier, Integer> result = getBackRangeData(somewhereInTheWorld, rightNow, aLittleLater);

        assertThat(result.size(), is(1));
        assertThat(result.containsKey(aFakeCarrier), is(true));
        assertThat(result.get(aFakeCarrier), is(2));

    }
}
