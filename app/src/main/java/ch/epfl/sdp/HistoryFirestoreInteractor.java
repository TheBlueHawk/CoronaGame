package ch.epfl.sdp;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import ch.epfl.sdp.firestore.ConcreteFirestoreInteractor;

public class HistoryFirestoreInteractor extends ConcreteFirestoreInteractor {

    private Account user;

    HistoryFirestoreInteractor(Account user) {
        super();
        this.user = user;
    }

    public CompletableFuture<Map<String, Map<String, Object>>> readHistory() {
        String path = "History/" + user.getId() + "/Positions";
        return readCollection(collectionReference(path));
    }


    public CompletableFuture<Void> write(Map<String, Object> content) {
        String path = "History/" + user.getId() + "/Positions";
        PositionRecord posRec = (PositionRecord) content.values().toArray()[0];

        Map<String, Object> lastPos = new HashMap<>();
        lastPos.put("geoPoint", posRec.getGeoPoint());
        lastPos.put("timeStamp", posRec.getTimestamp());

        return writeDocumentWithID(documentReference(path, posRec.calculateID()), content).thenRun(() ->
                writeDocumentWithID(documentReference("LastPositions", user.getId()), lastPos));
    }
}
