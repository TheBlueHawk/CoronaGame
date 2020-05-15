package ch.epfl.sdp.contamination.fragment;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;

import java.util.Date;

import ch.epfl.sdp.R;
import ch.epfl.sdp.contamination.InfectionAnalyst;
import ch.epfl.sdp.identity.fragment.AccountFragment;
import ch.epfl.sdp.location.LocationService;

import static android.content.Context.BIND_AUTO_CREATE;

/**
 * Say what this class is doing
 */
public class InfectionFragment extends Fragment implements View.OnClickListener {

    private TextView infectionStatus;
    private ProgressBar infectionProbability;
    private long lastUpdateTime;

    private LocationService service;

    private Handler uiHandler;

    public InfectionFragment(Handler uiHandler) {
        this.uiHandler = uiHandler;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        View view = inflater.inflate(R.layout.fragment_infection, container, false);

        infectionStatus = view.findViewById(R.id.my_infection_status);
        infectionProbability = view.findViewById(R.id.my_infection_probability);
        view.findViewById(R.id.my_infection_refresh).setOnClickListener(this);

        lastUpdateTime = System.currentTimeMillis();

        infectionStatus.setText("Refresh to see your status");

        ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                InfectionFragment.this.service = ((LocationService.LocationBinder) service).getService();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                service = null;
            }
        };

        getActivity().bindService(new Intent(getActivity(), LocationService.class), conn, BIND_AUTO_CREATE);

        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.my_infection_refresh: {
                onModelRefresh(view);
            }
            break;
        }
    }

    public void onModelRefresh(View v) {

        Date refreshTime = new Date(lastUpdateTime);
        lastUpdateTime = System.currentTimeMillis();

        // TODO: Which location?
        service.getReceiver().getMyLastLocation(AccountFragment.getAccount(getActivity()))
                .thenApply(location -> service.getAnalyst().updateInfectionPredictions(location, refreshTime, new Date())
                        .thenAccept(todayInfectionMeetings -> {
                            //TODO: should run on UI thread?
                            getActivity().runOnUiThread(() -> {
                                infectionStatus.setText(R.string.infection_status_posted);
                                uiHandler.post(() -> {
                                    InfectionAnalyst analyst = service.getAnalyst();
                                    infectionStatus.setText(analyst.getCarrier().getInfectionStatus().toString());
                                    infectionProbability.setProgress(Math.round(analyst.getCarrier().getIllnessProbability() * 100));
                                    Log.e("PROB:", analyst.getCarrier().getIllnessProbability() + "");
                                    displayAlert(todayInfectionMeetings);
                                });
                            });
                        })
                        .join());
    }

    private void displayAlert(int todayInfectionMeetings) {
        if (todayInfectionMeetings < 0) {
            throw new IllegalArgumentException();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        CharSequence first;
        CharSequence second;
        CharSequence title = getText(R.string.infection_dialog_title);
        switch (todayInfectionMeetings) {
            case 0:
                first = getText(R.string.infection_dialog_cool_message);
                second = "";
                break;
            case 1:
                first = getText(R.string.infection_dialog_message1);
                second = getText(R.string.one_infection_dialog_message2);
                break;
            default:
                first = getText(R.string.infection_dialog_message1);
                second = getText(R.string.several_infection_dialog_message2);

        }
        builder.setMessage((String) first + (todayInfectionMeetings == 0 ? "" : todayInfectionMeetings) + (String) second)
                .setTitle(title);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @VisibleForTesting
    public LocationService getLocationService() {
        return service;
    }

}
