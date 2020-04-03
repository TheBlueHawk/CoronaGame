package ch.epfl.sdp.contamination;

import android.location.Location;

import androidx.annotation.VisibleForTesting;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.Date;

import ch.epfl.sdp.Account;

public class ConcreteDataSender implements DataSender {
    private Account account;
    private GridFirestoreInteractor interactor;

    // Default success listener
    private OnSuccessListener successListener = o -> { };

    // Default Failure listener
    private OnFailureListener failureListener = e -> { };

    public ConcreteDataSender(GridFirestoreInteractor interactor, Account account) {
        this.interactor = interactor;
        this.account = account;
    }

    public ConcreteDataSender setOnSuccessListener(OnSuccessListener successListener) {
        this.successListener = successListener;
        return this;
    }

    public ConcreteDataSender setOnFailureListener(OnFailureListener failureListener) {
        this.failureListener = failureListener;
        return this;
    }

    @VisibleForTesting
    void setInteractor(GridFirestoreInteractor interactor) {
        this.interactor = interactor;
    }

    @Override
    public void registerLocation(Carrier carrier, Location location, Date time) {

        interactor.write(location, String.valueOf(time.getTime()), carrier, successListener, failureListener);
    }
}