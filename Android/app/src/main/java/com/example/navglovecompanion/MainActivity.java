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
import android.net.Uri;
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
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements LocationListener, View.OnClickListener {
    private static final int LOCATION_PERMISSIONS_CHECK_CODE = 23;
    private static final int BLUETOOTH_PERMISSION_CHECK_CODE = 24;
    private static final String TAG = "MainActivity";

    // Mac-Adressen der Bluetooth Module am Lilypad
    private final String[] macAdresses = {"00:00:00:00:01", "00:00:00:00:02"};

    LocationManager locationManager;
    Location lastLocation;
    Location target;
    TextView outputField;

    Location location;
    // Location test;
    //private final double LONG =9.442749;
    //private final double LAT= 54.778514;
    private Button naviBtn;
    private EditText input;
    private TextView displayBearing;
    private TextView displayDis;
    private boolean isTarget = false;
    private boolean stateBtn = false;
    private String out;

    private final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothAdapter bluetoothAdapter;
    private List<OutputStream> outputStreams;

    // lifecycle methods

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // outputField = (TextView) findViewById(R.id.outputField);
        input = findViewById(R.id.input);
        displayBearing = (TextView) findViewById(R.id.displayBearing);
        displayDis = (TextView) findViewById(R.id.displayDis);

        naviBtn = findViewById(R.id.naviBtn);
        naviBtn.setOnClickListener(this);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        outputStreams = new ArrayList<>();

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        /* spezifisch für den Empfang von Intents aus HERE Maps und OSMand
         * HERE Maps (work in Progress): Die Location befindet sich im Link, daher beim https splitten und den Link per Uri parsen lassen (handleIntent())
         * Aus dem String path muss dann noch latitude und logitude extrahiert werden (bisher nicht implementiert)
         */

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            String sharedData = intent.getStringExtra(Intent.EXTRA_TEXT);
            Log.i(TAG, sharedData);
            String out = "";
            if (sharedData.contains("geo:")) {
                target = handleOSMLinks(sharedData);
                out = target.toString();
                isTarget = true;

            } else if (sharedData.contains("https://")) {
                out = handleHERELinks(sharedData);
                isTarget = true;

            } else {
                out = "Invalid Data. Use HERE Maps or OSMand for location.";
                isTarget = false;
            }
            // outputField.setText(out);
            input.setText(out);
            // Log.i(TAG, sharedData);
        }
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

    // event methods

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
        Log.d(TAG, location.toString());
        setInfo(location);
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

    @Override
    public void onClick(View v) {
        if (isTarget) {
            startTracking();
        } else {
            Toast.makeText(this, "No target position exists!", Toast.LENGTH_LONG).show();
        }
    }

    // location methods

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

    private void startTracking() {
        if (stateBtn == true) {
            input.setEnabled(true);
            naviBtn.setText("Navigate to Position");
            displayDis.setText("");
            displayBearing.setText("");
            target = null;
        } else {
            stateBtn = true;
        }
    }

    private void setInfo(Location location) {
        if (stateBtn == true) {
            input.setEnabled(false);
            naviBtn.setText("Stop Navigation");
            stateBtn = false;
            float distance = location.distanceTo(target);
            float bearing = location.bearingTo(target);

            displayDis.setText(String.format("%.2f", distance));
            displayBearing.setText(String.format("%.2f", bearing));
        }
    }

    // link handling methods

    private String handleHERELinks(String data) {
        // TODO Save `goal`
        data = data.split("https://")[1];
        Uri uri = Uri.parse(data);
        String protocol = uri.getScheme();
        String server = uri.getAuthority();
        String path = uri.getPath();
        Set<String> args = uri.getQueryParameterNames();
//        Log.i("Protocol", protocol);
//        Log.i("Server", server);
//        Log.i("Path", path);

//        for(String arg: args) {
//            Log.i("Args", arg);
//        }
        return path;
    }

    private Location handleOSMLinks(String data) {
        String[] locationStrings = data.split("geo:")[1].split("\\?z=")[0].split(",");
        Double latitude = Double.parseDouble(locationStrings[0]);
        Double longitude = Double.parseDouble(locationStrings[1]);
        Location location = new Location("TargetLocation");
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        return location;
    }

    // bluetooth methods

    private void startBluetoothConnection() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            for (String macAddress : macAdresses) {
                try {
                    BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(macAddress);
                    BluetoothSocket bluetoothSocket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(uuid);
                    outputStreams.add(bluetoothSocket.getOutputStream());
                    Log.i(TAG, "Connected to: " + macAddress);
                } catch (Exception e) {
                    Log.e(TAG, "Bluetooth connection error: " + e);
                    Toast.makeText(this, "Connection error with MAC address: " + macAddress, Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void activateMotor(int hand, int motor) {
        String msg = Integer.toString(motor);
        byte[] buffer = msg.getBytes();
        try {
            outputStreams.get(hand).write(buffer);
            Log.i(TAG, "Activated motor " + motor + " on hand " + hand);
        } catch (Exception e) {
            Log.e(TAG, "Bluetooth sending error: " + e);
        }
    }
}
