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

public class MainActivity extends AppCompatActivity implements LocationListener {
    private static final int LOCATION_PERMISSIONS_CHECK_CODE = 23;
    private static final int BLUETOOTH_PERMISSION_CHECK_CODE = 24;
    private static final String TAG = "MainActivity";

    private BluetoothService bluetoothService = null;
    private LocationManager locationManager;
    private Location previousLocation;
    private Location target;

    private boolean doNavigation = false;

    private Button naviBtn;
    private EditText inputView;
    private TextView bearingView;
    private TextView distanceView;

    // Service connection handler

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            bluetoothService = ((BluetoothService.BluetoothServiceBinder) binder).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            bluetoothService = null;
        }
    };

    // Lifecycle Methoden

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputView = findViewById(R.id.input);
        bearingView = (TextView) findViewById(R.id.displayBearing);
        distanceView = (TextView) findViewById(R.id.displayDis);
        naviBtn = findViewById(R.id.naviBtn);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            String sharedData = intent.getStringExtra(Intent.EXTRA_TEXT);
            inputView.setText(sharedData);
        }
        // Activate for debugging
        //inputView.setText("54.7748757451, 9.45588403779");
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH}, BLUETOOTH_PERMISSION_CHECK_CODE);
        } else {
            bindBluetoothService();
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSIONS_CHECK_CODE);
        } else {
            startLocation();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (bluetoothService != null) {
            unbindService(connection);
            bluetoothService = null;
        }
    }

    // Event Methoden

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSIONS_CHECK_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocation();
            } else {
                Toast.makeText(this, "Location permissions are required to track your position.", Toast.LENGTH_LONG).show();
            }
        }

        if (requestCode == BLUETOOTH_PERMISSION_CHECK_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                bindBluetoothService();
            } else {
                Toast.makeText(this, "Bluetooth permissions are required.", Toast.LENGTH_LONG).show();
            }
        }
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
        if (doNavigation) {
            target = null;
            previousLocation = null;
            doNavigation = false;
            inputView.setEnabled(true);
            naviBtn.setText("Start Navigation");
            distanceView.setText("");
            bearingView.setText("");
        } else {
            target = parseLocationString(inputView.getText().toString());
            previousLocation = null;
            if (target != null) {
                doNavigation = true;
                inputView.setEnabled(false);
                naviBtn.setText("Stop Navigation");
                distanceView.setText("Start moving...");
                bearingView.setText("---");
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

    // Location Methoden

    private void startLocation() {
        try {
            if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastLocation != null) {
                    onLocationChanged(lastLocation);
                } else {
                    Log.d(TAG, "No last known position");
                }
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, 1, this);
                Log.d(TAG, "Location init successful");
            } else {
                Toast.makeText(this, "GPS has to be enabled to track your position.", Toast.LENGTH_LONG).show();
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "Location permissions are required to track your position.", Toast.LENGTH_LONG).show();
        }
    }

    private void setInfo(Location location) {
        if (previousLocation == null) {
            previousLocation = location;
        }
        float distance = location.distanceTo(target);
        if (distance < 5.0) {
            distanceView.setText("Target reached.");
            bearingView.setText("---");
            notifyTargetReached();
        }
        else {
            distanceView.setText(String.format("Distance: %.2f", distance));
            if (previousLocation != null && location.distanceTo(previousLocation) > 5.0) {
                float bearingTarget = location.bearingTo(target);
                float bearingPrevious = previousLocation.bearingTo(location);
                float bearingDelta = bearingTarget - bearingPrevious;
                previousLocation = location;
                Log.v(TAG, "Bearing to target: " + bearingTarget);
                Log.v(TAG, "Bearing from previous: " + bearingPrevious);
                Log.v(TAG, "Bearing delta: " + bearingDelta);
                bearingView.setText(String.format("Bearing: %.2f", bearingDelta));
                notifyDelta(bearingDelta);
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

    // Bluetooth Methoden

    private void bindBluetoothService() {
        if (bluetoothService == null) {
            Intent intent = new Intent(this, BluetoothService.class);
            if (!bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
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
