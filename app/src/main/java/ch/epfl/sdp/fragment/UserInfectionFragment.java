package ch.epfl.sdp.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;

import java.util.Date;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import ch.epfl.sdp.Account;
import ch.epfl.sdp.AuthenticationManager;
import ch.epfl.sdp.R;
import ch.epfl.sdp.Tools;
import ch.epfl.sdp.contamination.Carrier;
import ch.epfl.sdp.contamination.Layman;
import ch.epfl.sdp.firestore.ConcreteFirestoreInteractor;
import ch.epfl.sdp.firestore.FirestoreInteractor;
import ch.epfl.sdp.location.LocationService;

import static android.content.Context.BIND_AUTO_CREATE;
import static ch.epfl.sdp.Tools.IS_ONLINE;
import static ch.epfl.sdp.Tools.checkNetworkStatus;
import static ch.epfl.sdp.contamination.CachingDataSender.privateRecoveryCounter;
import static ch.epfl.sdp.contamination.CachingDataSender.privateUserFolder;
import static ch.epfl.sdp.contamination.Carrier.InfectionStatus.HEALTHY;
import static ch.epfl.sdp.contamination.Carrier.InfectionStatus.INFECTED;
import static ch.epfl.sdp.firestore.FirestoreInteractor.documentReference;

public class UserInfectionFragment extends Fragment implements View.OnClickListener, Observer {
    private static final String TAG = "User Infection Activity";
    private Button infectionStatusButton;
    private TextView infectionStatusView;
    private TextView onlineStatusView;
    private Button refreshButton;
    private Account account;
    private FirestoreInteractor fsi = new ConcreteFirestoreInteractor();
    private String userName;
    private View view;
    private LocationService service;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private SharedPreferences sharedPref;

    private TextView userInfectionProbability;

    @VisibleForTesting
    public boolean isImmediatelyNowIll() {
        CharSequence buttonText = infectionStatusButton.getText();
        boolean healthy = buttonText.equals(getResources().getString(R.string.i_am_infected));
        return !healthy;
    }

    @VisibleForTesting
    public LocationService getLocationService() {
        return service;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        view = inflater.inflate(R.layout.fragment_user_infection, container, false);
        infectionStatusView = view.findViewById(R.id.infectionStatusView);
        infectionStatusButton = view.findViewById(R.id.infectionStatusButton);
        infectionStatusButton.setOnClickListener(this);

        userInfectionProbability = view.findViewById(R.id.user_prob);

        checkOnline();
        getLoggedInUser();
        Executor executor = ContextCompat.getMainExecutor(requireActivity());
        if (Tools.canAuthenticate(getActivity())) {
            this.biometricPrompt = biometricPromptBuilder(executor);
            this.promptInfo = promptInfoBuilder();
        }
        ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                UserInfectionFragment.this.service = ((LocationService.LocationBinder) service).getService();
                ((Layman) UserInfectionFragment.this.service.getAnalyst().getCarrier()).addObserver(UserInfectionFragment.this);
                UserInfectionFragment.this.update(null, null);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                service = null;
            }
        };

        /*
         * startService() overrides the default service lifetime that is managed by  bindService
         * (Intent, ServiceConnection, int):it requires the service to remain running until
         * stopService(Intent) is called, regardless of whether any clients are connected to it.
         */
        //TODO: do we need this ComponentName?
        ComponentName myService = requireActivity().startService(new Intent(getContext(), LocationService.class));
        requireActivity().bindService(new Intent(getActivity(), LocationService.class), conn, BIND_AUTO_CREATE);
        sharedPref = requireActivity().getSharedPreferences("UserInfectionPrefFile", Context.MODE_PRIVATE);
        return view;
    }

    @Override
    public void update(Observable o, Object arg) {
        Carrier me = service.getAnalyst().getCarrier();

        getActivity().runOnUiThread(() -> {
            setInfectionColorAndMessage(me.getInfectionStatus() == INFECTED);
            userInfectionProbability.setText(String.format("%s With probability: %f", me.getUniqueId(), me.getIllnessProbability()));
        });
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.infectionStatusButton: {
                onClickChangeStatus();
            }
            break;
            case R.id.refreshButton: {
                onClickRefresh();
            }
            break;
        }
    }

    private void onClickChangeStatus() {
        if (checkOnline()) {
            if (checkElapsedTimeSinceLastChange()) {
                if (Tools.canAuthenticate(getActivity())) {
                    biometricPrompt.authenticate(promptInfo);
                    // TODO: @Lucie, after authentication when is executeHealthStatusChange called?
                } else {
                    executeHealthStatusChange();
                }
            } else {
                Toast.makeText(getActivity(),
                        R.string.error_infection_status_ratelimit, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void onClickRefresh() {
        checkOnline();
    }

    private boolean checkOnline() {
        onlineStatusView = view.findViewById(R.id.onlineStatusView);
        refreshButton = view.findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(this);
        checkNetworkStatus(getActivity());
        setOnlineOfflineVisibility(IS_ONLINE);
        return IS_ONLINE;
    }

    private void setOnlineOfflineVisibility(boolean isOnline) {
        int onlineVisibility = isOnline ? View.VISIBLE : View.INVISIBLE;
        int offlineVisibility = isOnline ? View.INVISIBLE : View.VISIBLE;
        onlineStatusView.setVisibility(offlineVisibility);
        refreshButton.setVisibility(offlineVisibility);
        infectionStatusButton.setVisibility(onlineVisibility);
        infectionStatusView.setVisibility(onlineVisibility);
    }

    private void getLoggedInUser() {
        account = AuthenticationManager.getAccount(getActivity());
        userName = account.getDisplayName();
        retrieveUserInfectionStatus().thenAccept(this::setInfectionColorAndMessage);
    }

    // TODO: @Lucie, why does this method return TRUE when the time is not elapsed?
    private boolean checkElapsedTimeSinceLastChange() {

        // TODO: @Lucie TEMPORARILY DISABLED !!!!!!!!! Re-enable
        return true;

        /*
        Date currentTime = Calendar.getInstance().getTime();
        //TODO: what does this means?
        /* get 1 jan 1970 by default. It's definitely wrong but works as we want t check that
         * the status has not been updated less than a day ago.

        Date lastStatusChange = new Date(sharedPref.getLong("lastStatusChange", 0));
        long difference = Math.abs(currentTime.getTime() - lastStatusChange.getTime());
        long differenceDays = difference / (24 * 60 * 60 * 1000);

        sharedPref.edit().putLong("lastStatusChange", currentTime.getTime()).apply();
        return differenceDays > 1;
        */
    }

    private void executeHealthStatusChange() {
        CharSequence buttonText = infectionStatusButton.getText();
        boolean infected = buttonText.equals(getResources().getString(R.string.i_am_infected));
        if (infected) {
            service.getAnalyst().getCarrier().evolveInfection(new Date(), INFECTED, 1f);
            setInfectionColorAndMessage(true);
        } else {
            service.getAnalyst().getCarrier().evolveInfection(new Date(), HEALTHY, 1f);
            sendRecoveryToFirebase();
            setInfectionColorAndMessage(false);
        }
    }

    private void sendRecoveryToFirebase() {
        // TODO: @Lucie I think that doesn't upload anything to Firestore
        // TODO: [LOG]
        Log.e("RECOVERY_SENDER", account.getId());
        DocumentReference ref = documentReference(privateUserFolder, account.getId());
        ref.update(privateRecoveryCounter, FieldValue.increment(1));
    }

    // TODO: This function does not belong here!!!!!!!!!!!!!!!
    // It should be moved to Layman


    private CompletableFuture<Boolean> retrieveUserInfectionStatus() {
        return fsi.readDocument(documentReference("Users", userName))
                .thenApply(stringObjectMap -> {
                    Boolean infected = (Boolean) stringObjectMap.get("Infected");
                    return infected == null ? false : infected;
                }).exceptionally(e -> {
                    Log.w(TAG, "Error retrieving infection status from " +
                            "Firestore.", e);
                    return null;
                });
    }

    private void setInfectionColorAndMessage(boolean infected) {
        int buttonTextID = infected ? R.string.i_am_cured : R.string.i_am_infected;
        int messageID = infected ?
                R.string.your_user_status_is_set_to_infected :
                R.string.your_user_status_is_set_to_not_infected;
        int colorID = infected ? R.color.colorRedInfected : R.color.colorGreenCured;
        clickAction(infectionStatusButton, infectionStatusView, buttonTextID,
                messageID, colorID);
    }

    private void clickAction(Button button, TextView textView, int buttonText, int textViewText, int textColor) {
        button.setText(buttonText);
        textView.setTextColor(getResources().getColorStateList(textColor, requireActivity().getTheme()));
        textView.setText(textViewText);
    }

    private BiometricPrompt biometricPromptBuilder(Executor executor) {
        return new BiometricPrompt(
                UserInfectionFragment.this,
                executor, new BiometricPrompt.AuthenticationCallback() {

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                displayNegativeButtonToast(errorCode);
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                executeAndDisplayAuthSuccessToast();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                displayAuthFailedToast();
            }
        });
    }

    private void displayAuthFailedToast() {
        Toast.makeText(requireActivity().getApplicationContext(), R.string.authentication_failed,
                Toast.LENGTH_SHORT)
                .show();
    }

    private void displayNegativeButtonToast(int errorCode) {
        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
            Toast.makeText(requireActivity().getApplicationContext(),
                    R.string.bio_auth_negative_button_toast, Toast.LENGTH_LONG)
                    .show();
        }
    }

    private void executeAndDisplayAuthSuccessToast() {
        Toast.makeText(requireActivity().getApplicationContext(),
                R.string.bio_auth_success_toast, Toast.LENGTH_SHORT).show();
        executeHealthStatusChange();
    }

    private BiometricPrompt.PromptInfo promptInfoBuilder() {
        return new BiometricPrompt.PromptInfo.Builder()
                .setConfirmationRequired(true)
                .setTitle(getString(R.string.bio_auth_prompt_title))
                .setSubtitle(getString(R.string.bio_auth_prompt_subtitle))
                .setNegativeButtonText(getString(R.string.bio_auth_prompt_negative_button))
                .build();
    }
}