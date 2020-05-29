package com.example.navglovecompanion;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.icu.util.ULocale;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.net.URI;
import java.net.URL;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements LocationListener {
    private static final int LOCATION_PERMISSIONS_CHECK_CODE = 23;
    private static final String TAG = "MainActivity";

    LocationManager locationManager;
    Location goal;
    TextView outputField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        outputField = (TextView) findViewById(R.id.outputField);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        /* spezifisch für den Empfang von Intents aus HERE Maps
        * Die Location befindet sich im Link, daher beim https splitten und den Link per Uri parsen lassen (handleIntent())
        * Aus dem String path muss dann noch latitude und logitude extrahiert werden (bisher nicht implementiert)
        */
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            String sharedData = intent.getStringExtra(Intent.EXTRA_TEXT);
            String[] url = sharedData.split("https://");

            outputField.setText(handleIntent("https://"+url[1]));
            //Log.i(TAG, sharedData);
            //Log.i(TAG, url[1]);
        }
    }

    @Override
    protected void onResume() {
        super.onStart();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSIONS_CHECK_CODE);
        }
        else {
            startLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSIONS_CHECK_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocation();
            } else {
                Toast.makeText(this, "Location permissions are required to track your position.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, location.toString());
        checkBearing(location);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.i(TAG, "Status changed: " + provider);
        // Nothing more to do here
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.i(TAG, "Provider enabled: " + provider);
        // Nothing more to do here
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.i(TAG, "Provider disabled: " + provider);
        // Nothing more to do here
    }

    private String handleIntent(String data) {
        // TODO Save `goal`
        Uri uri = Uri.parse(data);
        String protocol = uri.getScheme();
        String server = uri.getAuthority();
        String path = uri.getPath();
        Set<String> args = uri.getQueryParameterNames();

        Log.i("Protocol", protocol);
        Log.i("Server", server);
        Log.i("Path", path);

        for(String arg: args) {
            Log.i("Args", arg);
        }
        return path;
    }

    private void startLocation() {
        try {
            locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastLocation != null) {
                    onLocationChanged(lastLocation);
                }
                else {
                    Log.d(TAG, "No last known position");
                }
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, 1, this);
                Log.d(TAG, "Location init successful");
            }
            else {
                Toast.makeText(this, "Location provider is not enabled.", Toast.LENGTH_LONG).show();
            }
        }
        catch (SecurityException e) {
            Toast.makeText(this, "Location permissions are required to track your position.", Toast.LENGTH_LONG).show();
        }
    }

    private void checkBearing(Location location) {
        // TODO Calculate bearing from `location` and `goal`
        //      Compare with `location.bearing`
        //      Start vibration if necessary
    }
}
