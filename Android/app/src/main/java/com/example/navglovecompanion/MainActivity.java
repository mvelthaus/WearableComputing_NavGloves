package com.example.navglovecompanion;

import android.Manifest;
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

public class MainActivity extends AppCompatActivity implements LocationListener, NavigationService.NavigationServiceListener {
    private static final int LOCATION_PERMISSIONS_CHECK_CODE = 23;
    private static final int BLUETOOTH_PERMISSION_CHECK_CODE = 24;
    private static final String TAG = "MainActivity";
    private static final int LOCATIONS_SIZE = 10;

    private BluetoothService bluetoothService = null;
    private NavigationService navigationService = null;
    private LocationManager locationManager;
    private Location[] locations;
    private int locationsIndex = -1;
    private Location target;

    private boolean doNavigation = false;

    private Button naviBtn;
    private EditText inputField;
    private TextView deltaText;
    private TextView distanceText;

    // Service connection handler

    private final ServiceConnection bluetoothConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            bluetoothService = ((BluetoothService.BluetoothServiceBinder) binder).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            bluetoothService = null;
        }
    };

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

    // Lifecycle Methoden

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_main);

        inputField = findViewById(R.id.inputField);
        deltaText = (TextView) findViewById(R.id.deltaText);
        distanceText = (TextView) findViewById(R.id.distanceText);
        naviBtn = findViewById(R.id.naviBtn);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            String sharedData = intent.getStringExtra(Intent.EXTRA_TEXT);
            inputField.setText(sharedData);
        }
        // Activate for debugging
        inputField.setText("54.7748757451, 9.45588403779");
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH}, BLUETOOTH_PERMISSION_CHECK_CODE);
        } else {
            //bindBluetoothService();
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSIONS_CHECK_CODE);
        } else {
            bindNavigationService();
            //startLocation();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (bluetoothService != null) {
            unbindService(bluetoothConnection);
            bluetoothService = null;
        }
        if (navigationService != null) {
            navigationService.removeStateChangeListener(this);
            unbindService(navigationConnection);
            navigationService = null;
        }
    }

    // Event Methoden

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSIONS_CHECK_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                bindNavigationService();
                //startLocation();
            } else {
                Toast.makeText(this, "Location permissions are required to track your position.", Toast.LENGTH_LONG).show();
            }
        }

        if (requestCode == BLUETOOTH_PERMISSION_CHECK_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //bindBluetoothService();
            } else {
                Toast.makeText(this, "Bluetooth permissions are required.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onStateChange(int oldState, int newState) {
        syncUiWithState();
    }

    @Override
    public void onLocationChanged(Location location) {
        if (doNavigation) {
            //Log.v(TAG, location.toString());
            setInfo(location);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d(TAG, "Status changed: " + provider);
        // Nothing more to do here
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, "Provider enabled: " + provider);
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "GPS has been enabled, tracking resumed.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, "Provider disabled: " + provider);
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "GPS has been disabled, tracking paused.", Toast.LENGTH_LONG).show();
        }
    }

    public void onNaviClick(View v) {
        if (navigationService.getState() > NavigationService.STATE_CONNECTED) {
            navigationService.stopNavigation();
            target = null;
            locations = null;
            locationsIndex = -1;
            doNavigation = false;
        } else {
            target = parseLocationString(inputField.getText().toString());
            if (target != null) {
                doNavigation = true;
                locations = new Location[LOCATIONS_SIZE];
                locationsIndex = 0;
                navigationService.startNavigation();
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
                deltaText.setText("Waiting for GPS...");
                distanceText.setText("");
                inputField.setEnabled(false);
                naviBtn.setEnabled(true);
                naviBtn.setText("Stop Navigation");
                break;
            case NavigationService.STATE_RUNNING:
                deltaText.setText("Move 10m into one direction.");
                distanceText.setText("");
                inputField.setEnabled(false);
                naviBtn.setEnabled(true);
                naviBtn.setText("Stop Navigation");
                break;
            case NavigationService.STATE_FINISHED:
                deltaText.setText("Target reached.");
                distanceText.setText("");
                inputField.setEnabled(false);
                naviBtn.setEnabled(true);
                naviBtn.setText("Stop Navigation");
                break;
        }
    }

    // Location Methoden

    private void bindNavigationService() {
        if (navigationService == null) {
            Intent intent = new Intent(this, NavigationService.class);
            if (!bindService(intent, navigationConnection, Context.BIND_AUTO_CREATE)) {
                Toast.makeText(this, "Binding to navigation service failed", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startLocation() {
        try {
            if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastLocation != null) {
                    onLocationChanged(lastLocation);
                } else {
                    Log.d(TAG, "No last known position");
                }
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 1, this);
                Log.d(TAG, "Location init successful");
            } else {
                Toast.makeText(this, "GPS has to be enabled to track your position.", Toast.LENGTH_LONG).show();
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "Location permissions are required to track your position.", Toast.LENGTH_LONG).show();
        }
    }

    private void setInfo(Location location) {
        float distance = location.distanceTo(target);
        if (distance < 5.0) {
            distanceText.setText("Target reached.");
            deltaText.setText("---");
            notifyTargetReached();
        }
        else {
            distanceText.setText(String.format("Distance: %.2f", distance));
            if (locationsIndex < LOCATIONS_SIZE) {
                locations[locationsIndex] = location;
                Log.v(TAG, "Previous location " + locationsIndex + " initialized");
                locationsIndex++;
                Log.v(TAG, "Set previous locations index: " + locationsIndex);
            }
            else {
                System.arraycopy(locations, 1, locations, 0, LOCATIONS_SIZE - 1);
                locations[LOCATIONS_SIZE - 1] = location;
                float bearing = location.bearingTo(target);
                float course = calculateCourse();
                float delta = bearing - course;
                if (delta < -180)
                    delta += 360;
                else if (delta > 180)
                    delta -= 360;
                Log.v(TAG, "Bearing: " + bearing);
                Log.v(TAG, "Course: " + course);
                Log.v(TAG, "Delta: " + delta);
                deltaText.setText(String.format("Delta: %.2f", delta));
                notifyDelta(delta);
            }
        }
    }

    private float calculateCourse() {
        float lengthSum = 0;
        float courseSum = 0;
        for (int i = 0; i < LOCATIONS_SIZE; i++) {
            for (int j = i + 1; j < LOCATIONS_SIZE; j++) {
                float d = (float) Math.pow(locations[i].distanceTo(locations[j]), 2);
                float c = locations[i].bearingTo(locations[j]);
                lengthSum += d;
                courseSum += c * d;
            }
        }
        return courseSum / lengthSum;
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

    // Bluetooth Methoden

    private void bindBluetoothService() {
        if (bluetoothService == null) {
            Intent intent = new Intent(this, BluetoothService.class);
            if (!bindService(intent, bluetoothConnection, Context.BIND_AUTO_CREATE)) {
                Toast.makeText(this, "Binding to Bluetooth service failed", Toast.LENGTH_LONG).show();
            }
        }
    }

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
}
