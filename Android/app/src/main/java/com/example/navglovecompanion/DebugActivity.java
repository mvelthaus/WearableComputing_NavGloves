package com.example.navglovecompanion;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Bundle;
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

    private Switch switch0;
    private Switch switch1;
    private Switch switch2;
    private Switch switch3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug);
        switch0 = findViewById(R.id.switch0);
        switch1 = findViewById(R.id.switch1);
        switch2 = findViewById(R.id.switch2);
        switch3 = findViewById(R.id.switch3);
    }

    // Event Methoden

    public void onSendClick(View view) {
        if (switch0.isChecked())
            activateMotor(0, 0);
        if (switch1.isChecked())
            activateMotor(0, 1);
        if (switch2.isChecked())
            activateMotor(0, 2);
        if (switch3.isChecked())
            activateMotor(0, 3);
    }

    // Bluetooth Methoden

    private void activateMotor(final int hand, final int motor) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Activating motor " + motor + " on hand " + hand);
                    byte[] buffer = Integer.toString(motor).getBytes();
                    MainActivity.outputStreams.get(hand).write(buffer);
                } catch (Exception e) {
                    Log.e(TAG, "Error while activating motor: " + e);
                }
            }
        }).start();
    }
}
