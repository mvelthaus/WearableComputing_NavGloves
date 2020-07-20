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
    private static final String TAG = "DebugActivity";
    private static final int BLUETOOTH_PERMISSION_CHECK_CODE = 24;

    private BluetoothService bluetoothService = null;
    private Switch switch0;
    private Switch switch1;
    private Switch switch2;
    private Switch switch3;

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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH}, BLUETOOTH_PERMISSION_CHECK_CODE);
        } else {
            bindBluetoothService();
        }
    }

    @Override
    protected void onStop() {
        if (bluetoothService != null) {
            unbindService(connection);
            bluetoothService = null;
        }
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == BLUETOOTH_PERMISSION_CHECK_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                bindBluetoothService();
            } else {
                Toast.makeText(this, "Bluetooth permissions are required.", Toast.LENGTH_LONG).show();
            }
        }
    }

    // Event Methoden

    public void onSendClick(View view) {
        if (switch0.isChecked()) {
            bluetoothService.activateMotor(0, 0);
            bluetoothService.activateMotor(1, 0);
        }
        if (switch1.isChecked()) {
            bluetoothService.activateMotor(0, 1);
            bluetoothService.activateMotor(1, 1);
        }
        if (switch2.isChecked()) {
            bluetoothService.activateMotor(0, 2);
            bluetoothService.activateMotor(1, 2);
        }
        if (switch3.isChecked()) {
            bluetoothService.activateMotor(0, 3);
            bluetoothService.activateMotor(1, 3);
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
}
