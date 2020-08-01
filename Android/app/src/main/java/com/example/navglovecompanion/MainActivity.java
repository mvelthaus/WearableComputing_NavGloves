package com.example.navglovecompanion;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity implements NavigationService.NavigationServiceListener {
    private static final int LOCATION_PERMISSIONS_CHECK_CODE = 23;
    private static final int BLUETOOTH_PERMISSION_CHECK_CODE = 24;
    private static final String TAG = "MainActivity";

    private NavigationService navigationService = null;

    private Button naviBtn;
    private EditText inputField;
    private TextView deltaText;
    private TextView distanceText;
    private AlertDialog dialog;

    // Service connection handler

    private final ServiceConnection navigationConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            navigationService = ((NavigationService.NavigationServiceBinder) binder).getService();
            navigationService.addStateChangeListener(MainActivity.this);
            syncUiWithState();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            navigationService = null;
        }
    };

    // Lifecycle hooks

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_main);

        inputField = findViewById(R.id.inputField);
        deltaText = (TextView) findViewById(R.id.deltaText);
        distanceText = (TextView) findViewById(R.id.distanceText);
        naviBtn = findViewById(R.id.naviBtn);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            String sharedData = intent.getStringExtra(Intent.EXTRA_TEXT);
            inputField.setText(sharedData);
        }
        // Activate for debugging
        //inputField.setText("54.7748757451, 9.45588403779");
        //inputField.setText("52.9421480488, 12.3939376012");
    }

    @Override
    protected void onStart() {
        super.onStart();

        boolean isBluetoothAllowed = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED;
        boolean isLocationAllowed = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (isBluetoothAllowed && isLocationAllowed) {
            bindNavigationService();
        }
        else {
            if (!isBluetoothAllowed)
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH}, BLUETOOTH_PERMISSION_CHECK_CODE);
            if (!isLocationAllowed)
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSIONS_CHECK_CODE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (navigationService != null) {
            navigationService.removeStateChangeListener(this);
            unbindService(navigationConnection);
            navigationService = null;
        }
    }

    // Event handling

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSIONS_CHECK_CODE && grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED)
            Toast.makeText(this, "Error: Location permissions are required.", Toast.LENGTH_LONG).show();
        if (requestCode == BLUETOOTH_PERMISSION_CHECK_CODE && grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED)
            Toast.makeText(this, "Error: Bluetooth permissions are required.", Toast.LENGTH_LONG).show();
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED)
            && (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED))
            bindNavigationService();
    }

    @Override
    public void onStateChange(int oldState, int newState) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                syncUiWithState();
            }
        });
    }

    public void onNaviClick(View v) {
        if (navigationService.getState() > NavigationService.STATE_CONNECTED) {
            navigationService.sendMessage(NavigationService.MSG_STOP_NAVIGATION);
        } else {
            // TODO allow passing of data along with message
            navigationService.setNavigationInput(inputField.getText().toString());
            navigationService.sendMessage(NavigationService.MSG_START_NAVIGATION);
        }
    }

    public void onDebugClick(View v) {
        Intent intent = new Intent(this, DebugActivity.class);
        startActivity(intent);
    }

    // Helper methods

    private void bindNavigationService() {
        if (navigationService == null) {
            Intent intent = new Intent(this, NavigationService.class);
            if (!bindService(intent, navigationConnection, Context.BIND_AUTO_CREATE)) {
                Toast.makeText(this, "Binding to navigation service failed", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void syncUiWithState() {
        if (navigationService == null) {
            Log.w(TAG, "Not connected to navigation service");
            return;
        }
        if (dialog != null) {
            dialog.cancel();
            dialog = null;
        }
        switch (navigationService.getState()) {
            case NavigationService.STATE_CREATED:
                deltaText.setText("Service has been started.");
                distanceText.setText("");
                inputField.setEnabled(true);
                //inputField.setText(navigationService.getNavigationInput());
                naviBtn.setEnabled(false);
                naviBtn.setText("Start Navigation");
                break;
            case NavigationService.STATE_CONNECTING:
                deltaText.setText("Connecting to gloves...");
                distanceText.setText("");
                inputField.setEnabled(true);
                //inputField.setText(navigationService.getNavigationInput());
                naviBtn.setEnabled(false);
                naviBtn.setText("Start Navigation");
                break;
            case NavigationService.STATE_CONNECTED:
                deltaText.setText("Ready to navigate.");
                distanceText.setText("");
                inputField.setEnabled(true);
                //inputField.setText(navigationService.getNavigationInput());
                naviBtn.setEnabled(true);
                naviBtn.setText("Start Navigation");
                break;
            case NavigationService.STATE_STARTING:
                deltaText.setText("Start walking into one direction...");
                distanceText.setText("Distance: " + navigationService.getNavigationDistance());
                inputField.setEnabled(false);
                inputField.setText(navigationService.getNavigationInput());
                naviBtn.setEnabled(true);
                naviBtn.setText("Stop Navigation");
                break;
            case NavigationService.STATE_LOCATING:
                deltaText.setText("Delta: " + navigationService.getNavigationDelta());
                distanceText.setText("Distance: " + navigationService.getNavigationDistance());
                inputField.setEnabled(false);
                inputField.setText(navigationService.getNavigationInput());
                naviBtn.setEnabled(true);
                naviBtn.setText("Stop Navigation");
                break;
            case NavigationService.STATE_FINISHED:
                deltaText.setText("Target reached.");
                distanceText.setText("Distance: " + navigationService.getNavigationDistance());
                inputField.setEnabled(false);
                inputField.setText(navigationService.getNavigationInput());
                naviBtn.setEnabled(true);
                naviBtn.setText("Stop Navigation");
                break;
            case NavigationService.STATE_PAUSED:
                dialog = new AlertDialog.Builder(this)
                    .setTitle("Navigation Paused")
                    .setMessage("Please enable GPS and grant permission to access location.")
                    .setPositiveButton("Resume", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            navigationService.sendMessage(NavigationService.MSG_START_NAVIGATION);
                        }
                    })
                    .setNegativeButton("Stop", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            navigationService.sendMessage(NavigationService.MSG_STOP_NAVIGATION);
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
                break;
            case NavigationService.STATE_ERROR:
                dialog = new AlertDialog.Builder(this)
                    .setTitle("Bluetooth Error")
                    .setMessage("Unable to connect to NavGloves.")
                    .setPositiveButton("Retry", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            navigationService.sendMessage(NavigationService.MSG_START_BLUETOOTH);
                        }
                    })
                    .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            finishAndRemoveTask();
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
                break;
        }
    }
}
