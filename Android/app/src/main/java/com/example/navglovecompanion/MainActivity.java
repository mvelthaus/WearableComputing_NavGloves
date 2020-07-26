package com.example.navglovecompanion;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements NavigationService.NavigationServiceListener {
    private static final int LOCATION_PERMISSIONS_CHECK_CODE = 23;
    private static final int BLUETOOTH_PERMISSION_CHECK_CODE = 24;
    private static final String TAG = "MainActivity";

    private NavigationService navigationService = null;

    private Button naviBtn;
    private EditText inputField;
    private TextView deltaText;
    private TextView distanceText;

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
            navigationService.stopNavigation();
        } else {
            Location target = parseLocationString(inputField.getText().toString());
            if (target != null) {
                navigationService.startNavigation(target);
            }
            else {
                Toast.makeText(this, "The entered text does not describe a valid position", Toast.LENGTH_LONG).show();
            }
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

    private Location parseLocationString(String locationStr) {
        Pattern p = Pattern.compile("([-+]?\\d*\\.?\\d+)\\D+([-+]?\\d*\\.?\\d+)");
        Matcher m = p.matcher(locationStr);
        if (m.find()) {
            double latitude = Double.parseDouble(m.group(1));
            double longitude = Double.parseDouble(m.group(2));
            Log.d(TAG, "Location: " + latitude + ", " + longitude);
            Location location = new Location(LocationManager.GPS_PROVIDER);
            location.setLatitude(latitude);
            location.setLongitude(longitude);
            return location;
        }
        else {
            Log.d(TAG, "Location string does not match pattern");
            return null;
        }
    }

    private void syncUiWithState() {
        if (navigationService == null) {
            Log.w(TAG, "Not connected to navigation service");
            return;
        }
        switch (navigationService.getState()) {
            case NavigationService.STATE_STARTED:
                deltaText.setText("Service has been started.");
                distanceText.setText("");
                inputField.setEnabled(true);
                naviBtn.setEnabled(false);
                naviBtn.setText("Start Navigation");
                break;
            case NavigationService.STATE_CONNECTING:
                deltaText.setText("Connecting to gloves...");
                distanceText.setText("");
                inputField.setEnabled(true);
                naviBtn.setEnabled(false);
                naviBtn.setText("Start Navigation");
                break;
            case NavigationService.STATE_CONNECTED:
                deltaText.setText("Ready to navigate.");
                distanceText.setText("");
                inputField.setEnabled(true);
                naviBtn.setEnabled(true);
                naviBtn.setText("Start Navigation");
                break;
            case NavigationService.STATE_LOCATING:
                deltaText.setText("Move 10m into one direction.");
                distanceText.setText("Distance: " + navigationService.getNavigationDistance());
                inputField.setEnabled(false);
                naviBtn.setEnabled(true);
                naviBtn.setText("Stop Navigation");
                break;
            case NavigationService.STATE_RUNNING:
                deltaText.setText("Delta: " + navigationService.getNavigationDelta());
                distanceText.setText("Distance: " + navigationService.getNavigationDistance());
                inputField.setEnabled(false);
                naviBtn.setEnabled(true);
                naviBtn.setText("Stop Navigation");
                break;
            case NavigationService.STATE_FINISHED:
                deltaText.setText("Target reached.");
                distanceText.setText("Distance: " + navigationService.getNavigationDistance());
                inputField.setEnabled(false);
                naviBtn.setEnabled(true);
                naviBtn.setText("Stop Navigation");
                break;
        }
    }

    // Bluetooth Methoden
    // TODO Move to service
    /*
    private void notifyTargetReached() {
        for (int motor = 0; motor < 4; motor++) {
            bluetoothService.activateMotor(0, motor);
            bluetoothService.activateMotor(1, motor);
        }
    }

    private void notifyDelta(float delta) {
        int hand = delta >= 0 ? 0 : 1;
        float absoluteDelta = Math.abs(delta);
        if (absoluteDelta > 80.0) {
            bluetoothService.activateMotor(hand, 0);
            bluetoothService.activateMotor(hand, 1);
            bluetoothService.activateMotor(hand, 2);
            bluetoothService.activateMotor(hand, 3);
        }
        else if (absoluteDelta > 60.0) {
            bluetoothService.activateMotor(hand, 2);
            bluetoothService.activateMotor(hand, 3);
        }
        else if (absoluteDelta > 40.0) {
            bluetoothService.activateMotor(hand, 3);
        }
        else if (absoluteDelta > 20.0) {
            bluetoothService.activateMotor(hand, 2);
        }
        else if (absoluteDelta > 10.0) {
            bluetoothService.activateMotor(hand, 1);
        }
        else if (absoluteDelta > 5.0) {
            bluetoothService.activateMotor(hand, 0);
        }
    }
    */
}
