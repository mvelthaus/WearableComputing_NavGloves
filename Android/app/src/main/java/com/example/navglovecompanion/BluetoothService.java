package com.example.navglovecompanion;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothService extends Service {

    class BluetoothServiceBinder extends Binder {
        BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    private static final String TAG = "BluetoothService";
    // Mac-Adressen und UUIDs der Bluetooth Module am Lilypad
    private static final String[] MAC_ADDRESSES = {"00:13:01:04:18:76", "98:d3:a1:f5:ca:e7"};
    private static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final IBinder binder = new BluetoothServiceBinder();

    private List<BluetoothSocket> sockets;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();
        startBluetoothConnection();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return binder;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "onRebind");
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        stopBluetoothConnection();
    }

    // A simple public method that can be called by a client
    public void activateMotor(final int hand, final int motor) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Activating motor " + motor + " on hand " + hand);
                    byte[] buffer = Integer.toString(motor).getBytes();
                    sockets.get(hand).getOutputStream().write(buffer);
                } catch (Exception e) {
                    Log.e(TAG, "Error while activating motor " + motor + " on hand " + hand, e);
                    Toast.makeText(BluetoothService.this, "Unable to activate motor", Toast.LENGTH_SHORT).show();
                }
            }
        }).start();
    }

    private void startBluetoothConnection() {
        Log.d(TAG, "Starting Bluetooth");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter != null) {
                sockets = new ArrayList<>();
                for (String macAddress : MAC_ADDRESSES) {
                    try {
                        BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(macAddress);
                        BluetoothSocket bluetoothSocket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(uuid);
                        bluetoothSocket.connect();
                        sockets.add(bluetoothSocket);
                        Log.i(TAG, "Connected to: " + macAddress);
                    } catch (Exception e) {
                        Log.e(TAG, "Connection error", e);
                        Toast.makeText(this, "Connection error with device: " + macAddress, Toast.LENGTH_LONG).show();
                    }
                }
            } else {
                Log.e(TAG, "Default adapter is null");
                Toast.makeText(this, "Bluetooth is not available on this device", Toast.LENGTH_LONG).show();
            }
        }
        else {
            Log.e(TAG, "Permission has not been granted");
            Toast.makeText(this, "Missing permission to use Bluetooth", Toast.LENGTH_LONG).show();
        }
    }

    private void stopBluetoothConnection() {
        Log.d(TAG, "StoppingBluetooth");
        if (sockets == null)
            return;
        for (BluetoothSocket socket : sockets) {
            try {
                socket.close();
            }
            catch (IOException e) {
                Log.e(TAG, "Error while closing socket", e);
            }
        }
    }
}
