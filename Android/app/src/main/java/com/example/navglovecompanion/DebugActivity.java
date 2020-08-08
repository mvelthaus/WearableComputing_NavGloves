package com.example.navglovecompanion;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Switch;
import android.widget.Toast;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DebugActivity extends AppCompatActivity {
    private static final int LOCATION_PERMISSIONS_CHECK_CODE = 23;
    private static final int BLUETOOTH_PERMISSION_CHECK_CODE = 24;
    private static final String TAG = "DebugActivity";

    private NavigationService navigationService = null;
    private Switch switch0;
    private Switch switch1;
    private Switch switch2;
    private Switch switch3;

    // Service connection handler

    private final ServiceConnection navigationConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            navigationService = ((NavigationService.NavigationServiceBinder) binder).getService();
            Toast.makeText(DebugActivity.this, "Conneted to service", Toast.LENGTH_SHORT);
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            navigationService = null;
        }
    };

    // Lifecycle hooks

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug);
        switch0 = findViewById(R.id.switch0);
        switch1 = findViewById(R.id.switch1);
        switch2 = findViewById(R.id.switch2);
        switch3 = findViewById(R.id.switch3);
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
            unbindService(navigationConnection);
            navigationService = null;
        }
    }

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

    // Event Methoden

    public void onSendClick(View view) {
        if (switch0.isChecked()) {
            navigationService.activateMotor(0, 0);
            navigationService.activateMotor(1, 0);
        }
        if (switch1.isChecked()) {
            navigationService.activateMotor(0, 1);
            navigationService.activateMotor(1, 1);
        }
        if (switch2.isChecked()) {
            navigationService.activateMotor(0, 2);
            navigationService.activateMotor(1, 2);
        }
        if (switch3.isChecked()) {
            navigationService.activateMotor(0, 3);
            navigationService.activateMotor(1, 3);
        }
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
}
