package ch.epfl.sdp.contamination.databaseIO;

import org.junit.Before;
import org.junit.Test;

import ch.epfl.sdp.identity.User;

import static ch.epfl.sdp.TestTools.sleep;
import static ch.epfl.sdp.firestore.FirestoreLabels.publicAlertAttribute;
import static org.junit.Assert.assertEquals;

public class DataReceiverTest {
    private DataReceiver receiver;
    private ConcreteCachingDataSender sender;

    @Before
    public void init() {
        sender = new ConcreteCachingDataSender(new GridFirestoreInteractor());
        receiver = new ConcreteDataReceiver(new GridFirestoreInteractor());

        /*receiver = fragment.getLocationService().getReceiver();
        sender = (ConcreteCachingDataSender)fragment.getLocationService().getSender();*/
    }

    @Test
    public void getSickNeighborDoesGetIt() {
        sender.sendAlert(User.DEFAULT_USERID).thenRun(() ->
                receiver.getNumberOfSickNeighbors(User.DEFAULT_USERID).thenAccept(res ->
                        assertEquals(1f, ((float) (double) (res.get(publicAlertAttribute))),
                                0.00001)));
        sleep();
    }

}
