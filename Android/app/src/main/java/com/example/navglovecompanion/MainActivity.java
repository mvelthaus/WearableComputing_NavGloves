package com.example.navglovecompanion;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
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

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements LocationListener {
    private static final int LOCATION_PERMISSIONS_CHECK_CODE = 23;
    private static final int BLUETOOTH_PERMISSION_CHECK_CODE = 24;
    private static final String TAG = "MainActivity";

    // Mac-Adressen und UUIDs der Bluetooth Module am Lilypad
    private static final String[] MAC_ADDRESSES = {"00:13:01:04:18:76"}; // TODO Zweite Adresse hinzufügen
    private static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public static List<OutputStream> outputStreams;

    private LocationManager locationManager;
    private Location previousLocation;
    private Location target;
    private boolean doNavigation = false;

    private Button naviBtn;
    private EditText inputView;
    private TextView bearingView;
    private TextView distanceView;

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
        outputStreams = new ArrayList<>();

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            String sharedData = intent.getStringExtra(Intent.EXTRA_TEXT);
            inputView.setText(sharedData);
        }
        // Activate for debugging
        inputView.setText("54.7748757451, 9.45588403779");
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH}, BLUETOOTH_PERMISSION_CHECK_CODE);
        } else {
            startBluetoothConnection();
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSIONS_CHECK_CODE);
        } else {
            startLocation();
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
                startBluetoothConnection();
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
            locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
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

    private void startBluetoothConnection() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            for (String macAddress : MAC_ADDRESSES) {
                try {
                    BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(macAddress);
                    BluetoothSocket bluetoothSocket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(uuid);
                    bluetoothSocket.connect();
                    outputStreams.add(bluetoothSocket.getOutputStream());
                    Log.i(TAG, "Connected to: " + macAddress);
                } catch (Exception e) {
                    Log.e(TAG, "Bluetooth connection error: " + e);
                    Toast.makeText(this, "Connection error with MAC address: " + macAddress, Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void notifyTargetReached() {
        for (int motor = 0; motor < 4; motor++) {
            activateMotor(0, motor);
            activateMotor(1, motor);
        }
    }

    private void notifyDelta(float delta) {
        int hand = delta >= 0 ? 0 : 1;
        float absoluteDelta = Math.abs(delta);
        if (absoluteDelta > 80.0) {
            activateMotor(hand, 0);
            activateMotor(hand, 1);
            activateMotor(hand, 2);
            activateMotor(hand, 3);
        }
        else if (absoluteDelta > 60.0) {
            activateMotor(hand, 2);
            activateMotor(hand, 3);
        }
        else if (absoluteDelta > 40.0) {
            activateMotor(hand, 3);
        }
        else if (absoluteDelta > 20.0) {
            activateMotor(hand, 2);
        }
        else if (absoluteDelta > 10.0) {
            activateMotor(hand, 1);
        }
        else if (absoluteDelta > 5.0) {
            activateMotor(hand, 0);
        }
    }

    private void activateMotor(final int hand, final int motor) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Activating motor " + motor + " on hand " + hand);
                    byte[] buffer = Integer.toString(motor).getBytes();
                    outputStreams.get(hand).write(buffer);
                } catch (Exception e) {
                    Log.e(TAG, "Error while activating motor: " + e);
                }
            }
        }).start();
    }
}
