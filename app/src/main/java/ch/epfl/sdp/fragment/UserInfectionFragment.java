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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import ch.epfl.sdp.Account;
import ch.epfl.sdp.AuthenticationManager;
import ch.epfl.sdp.Callback;
import ch.epfl.sdp.R;
import ch.epfl.sdp.biometric.BiometricPromptWrapper;
import ch.epfl.sdp.biometric.ConcreteBiometricPromptWrapper;
import ch.epfl.sdp.contamination.Carrier;
import ch.epfl.sdp.firestore.FirestoreInteractor;
import ch.epfl.sdp.location.LocationService;

import static android.content.Context.BIND_AUTO_CREATE;
import static ch.epfl.sdp.MainActivity.IS_ONLINE;
import static ch.epfl.sdp.MainActivity.checkNetworkStatus;
import static ch.epfl.sdp.contamination.CachingDataSender.privateRecoveryCounter;
import static ch.epfl.sdp.contamination.CachingDataSender.privateUserFolder;

public class UserInfectionFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = "User Infection Activity";
    private Button infectionStatusButton;
    private TextView infectionStatusView;
    private TextView onlineStatusView;
    private Button refreshButton;
    private Account account;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String userName;
    private View view;
    private LocationService service;
    private BiometricPromptWrapper biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private SharedPreferences sharedPref;

    @VisibleForTesting
    public void setBiometricPrompt(BiometricPromptWrapper biometricPrompt) {
        this.biometricPrompt = biometricPrompt;
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

        checkOnline();
        getLoggedInUser();

        Executor executor = ContextCompat.getMainExecutor(requireContext());

        if (BiometricPromptWrapper.canAuthenticate(getActivity())) {
            this.biometricPrompt =
                    new ConcreteBiometricPromptWrapper(UserInfectionFragment.this, executor,
                            getActivity(),
                            CompletableFuture.completedFuture(null).thenRun(this::executeHealthStatusChange));
            this.promptInfo = ConcreteBiometricPromptWrapper.promptInfoBuilder(true,
                    getString(R.string.bio_auth_prompt_title),
                    getString(R.string.bio_auth_prompt_subtitle),
                    getString(R.string.bio_auth_prompt_negative_button));
        }

        ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                UserInfectionFragment.this.service = ((LocationService.LocationBinder) service).getService();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                service = null;
            }
        };

        // startService() overrides the default service lifetime that is managed by
        // bindService(Intent, ServiceConnection, int):
        // it requires the service to remain running until stopService(Intent) is called,
        // regardless of whether any clients are connected to it.
        //TODO: is myService useful?
        ComponentName myService = requireActivity().startService(new Intent(getContext(), LocationService.class));
        requireActivity().bindService(new Intent(getActivity(), LocationService.class), conn, BIND_AUTO_CREATE);
        sharedPref = requireActivity().getSharedPreferences("UserInfectionPrefFile", Context.MODE_PRIVATE);
        return view;
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
            if (checkDayDifference()) {
                if (BiometricPromptWrapper.canAuthenticate(getActivity())) {
                    biometricPrompt.authenticate(promptInfo);
                } else {
                    executeHealthStatusChange();
                }
            } else {
                Toast.makeText(getActivity(),
                        R.string.error_infection_status_ratelimit, Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getActivity(),
                    R.string.you_cannot_update_your_status_you_are_offline, Toast.LENGTH_LONG).show();
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
        retrieveUserInfectionStatus(this::setInfectionColorAndMessage);
    }

    private boolean checkDayDifference() {
        Date currentTime = Calendar.getInstance().getTime();
        /* get 1 jan 1970 by default. It's definitely wrong but works as we want t check that
         * the status has not been updated less than a day ago.
         */
        Date lastStatusChange = new Date(sharedPref.getLong("lastStatusChange", 0));
        long difference = Math.abs(currentTime.getTime() - lastStatusChange.getTime());
        long differenceDays = difference / (24 * 60 * 60 * 1000);
        sharedPref.edit().putLong("lastStatusChange", currentTime.getTime()).apply();
        return differenceDays > 1;
    }

    private void executeHealthStatusChange() {
        CharSequence buttonText = infectionStatusButton.getText();
        boolean infected = buttonText.equals(getResources().getString(R.string.i_am_infected));
        if (infected) {
            //Tell the analyst we are now sick !
            service.getAnalyst().updateStatus(Carrier.InfectionStatus.INFECTED);
            setInfectionColorAndMessage(true);
            modifyUserInfectionStatus(userName, true,
                    value -> {
                    });
        } else {
            //Tell analyst we are now healthy !
            service.getAnalyst().updateStatus(Carrier.InfectionStatus.HEALTHY);
            sendRecoveryToFirebase();
            setInfectionColorAndMessage(false);
            modifyUserInfectionStatus(userName, false,
                    value -> {
                    });
        }
    }

    private void sendRecoveryToFirebase() {
        DocumentReference ref = FirestoreInteractor.documentReference(privateUserFolder, account.getId());
        ref.update(privateRecoveryCounter, FieldValue.increment(1));
    }

    private void modifyUserInfectionStatus(String userPath, Boolean infected, Callback<String> callback) {
        Map<String, Object> user = new HashMap<>();
        user.put("Infected", infected);
        db.collection("Users").document(userPath)
                .set(user, SetOptions.merge());

        DocumentReference userRef = db.collection("Users").document(userPath);

        userRef
                .update("Infected", infected)
                .addOnSuccessListener(documentReference ->
                        callback.onCallback(getString(R.string.user_status_update)))
                .addOnFailureListener(e ->
                        callback.onCallback(getString(R.string.error_status_update)));
    }

    private void retrieveUserInfectionStatus(Callback<Boolean> callbackBoolean) {
        db.collection("Users").document(userName).get().addOnSuccessListener(documentSnapshot ->
        {
            Log.d(TAG, "Infected status successfully loaded.");
            Object infected = documentSnapshot.get("Infected");
            if (infected == null) {
                callbackBoolean.onCallback(false);
            } else {
                callbackBoolean.onCallback((boolean) infected);
            }
        })
                .addOnFailureListener(e ->
                        Log.w(TAG, "Error retrieving infection status from Firestore.", e));
    }

    private void setInfectionColorAndMessage(boolean infected) {
        int buttonTextID = infected ? R.string.i_am_cured : R.string.i_am_infected;
        int messageID = infected ? R.string.your_user_status_is_set_to_infected :
                R.string.your_user_status_is_set_to_not_infected;
        int colorID = infected ? R.color.colorRedInfected : R.color.colorGreenCured;
        clickAction(infectionStatusButton, infectionStatusView, buttonTextID,
                messageID, colorID);
    }

    private void clickAction(Button button, TextView textView, int buttonText, int textViewText, int textColor) {
        button.setText(buttonText);
        textView.setTextColor(getResources().getColorStateList(textColor,
                requireActivity().getTheme()));
        textView.setText(textViewText);
    }

    public LocationService getLocationService() {
        return service;
    }

    @VisibleForTesting
    public boolean isImmediatelyNowIll() {
        CharSequence buttonText = infectionStatusButton.getText();
        boolean healthy = buttonText.equals(getResources().getString(R.string.i_am_infected));
        return !healthy;
    }

}