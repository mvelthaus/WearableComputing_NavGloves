package com.example.navglovecompanion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NavigationService extends Service implements LocationListener {

    class NavigationServiceBinder extends Binder {
        NavigationService getService() {
            return NavigationService.this;
        }
    }

    public interface NavigationServiceListener {
        void onStateChange(int oldState, int newState);
    }

    public static final int STATE_STARTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    public static final int STATE_LOCATING = 3;
    public static final int STATE_RUNNING = 4;
    public static final int STATE_FINISHED = 5;

    private static final int MSG_SERVICE_CREATED = 0;
    private static final int MSG_BLUETOOTH_CONNECTED = 1;
    private static final int MSG_BLUETOOTH_ERROR = 2;
    private static final int MSG_NAVIGATION_STARTED = 3;
    private static final int MSG_NAVIGATION_LOCATED = 3;
    private static final int MSG_NAVIGATION_FINISHED = 4;
    private static final int MSG_NAVIGATION_DONE = 5;

    private static final String TAG = "NavigationService";
    private static final String NOTIFICATION_CHANNEL_ID = "NAVI";
    private static final int ONGOING_NOTIFICATION_ID = 23;
    // Mac-Adressen und UUIDs der Bluetooth Module am Lilypad
    private static final String[] MAC_ADDRESSES = {"00:13:01:04:18:76", "98:d3:a1:f5:ca:e7"};
    private static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int LOCATIONS_SIZE = 10;

    private final IBinder binder = new NavigationServiceBinder();

    private ArrayList<NavigationServiceListener> listeners = new ArrayList<>();
    private Location[] locations;
    private int locationsIndex;
    private float navigationDelta;
    private float navigationDistance;
    private String navigationInput;
    private Location navigationTarget;
    private ArrayList<BluetoothSocket> sockets = new ArrayList<>();
    private int state = STATE_STARTED;;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();
        sendMessage(MSG_SERVICE_CREATED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        super.onStartCommand(intent, flags, startId);
        return START_NOT_STICKY;
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
    }

    // Navigation control

    public float getNavigationDelta() {
        return navigationDelta;
    }

    public float getNavigationDistance() {
        return navigationDistance;
    }

    public String getNavigationInput() {
        return navigationInput;
    }

    public Location getNavigationTarget() {
        return navigationTarget;
    }

    public void startNavigation(String input) {
        Location target = parseInput(input);
        if (target != null) {
            startForeground(ONGOING_NOTIFICATION_ID, buildNotification());
            startService(new Intent(getApplicationContext(), NavigationService.class));
            // TODO allow passing of data along with message
            navigationInput = input;
            navigationTarget = target;
            sendMessage(MSG_NAVIGATION_STARTED);
            Log.d(TAG, "Navigation started");
        }
        else {
            Toast.makeText(this, "The entered text does not describe a valid position", Toast.LENGTH_LONG).show();
        }
    }

    public void stopNavigation() {
        stopForeground(true);
        stopSelf();
        sendMessage(MSG_NAVIGATION_DONE);
        Log.d(TAG, "Navigation stopped");
    }

    private Notification buildNotification() {
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "NavigationChannel", NotificationManager.IMPORTANCE_HIGH);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent =
            PendingIntent.getActivity(this, 0, notificationIntent, 0);

        return new Notification.Builder(this,NOTIFICATION_CHANNEL_ID)
            .setContentTitle("NavGlove")
            .setContentText("Navigation is running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build();
    }


    private Location parseInput(String input) {
        Pattern p = Pattern.compile("([-+]?\\d*\\.?\\d+)\\D+([-+]?\\d*\\.?\\d+)");
        Matcher m = p.matcher(input);
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

    // State management

    public void addStateChangeListener(NavigationServiceListener listener) {
        listeners.add(listener);
    }

    public void removeStateChangeListener(NavigationServiceListener listener) {
        listeners.remove(listener);
    }

    public int getState() {
        return state;
    }

    private void sendMessage(int msg) {
        try {
            int oldState = state;
            stopState();
            state = processMessage(msg);
            startState();
            for (NavigationServiceListener listener : listeners)
                listener.onStateChange(oldState, state);
        }
        catch (Exception e) {
            Log.e(TAG, "Error while sending message", e);
        }
    }

    private int processMessage(int msg) throws IllegalStateException {
        switch (state) {
            case STATE_STARTED:
                switch (msg) {
                    case MSG_SERVICE_CREATED:
                        return STATE_CONNECTING;
                    default:
                        throw new IllegalStateException("State " + state + " does not know about message: " + msg);
                }
            case STATE_CONNECTING:
                switch (msg) {
                    case MSG_BLUETOOTH_CONNECTED:
                        return STATE_CONNECTED;
                    case MSG_BLUETOOTH_ERROR:
                        return STATE_CONNECTING;
                    default:
                        throw new IllegalStateException("State " + state + " does not know about message: " + msg);
                }
            case STATE_CONNECTED:
                switch (msg) {
                    case MSG_NAVIGATION_STARTED:
                        return STATE_LOCATING;
                    case MSG_BLUETOOTH_ERROR:
                        return STATE_CONNECTING;
                    default:
                        throw new IllegalStateException("State " + state + " does not know about message: " + msg);
                }
            case STATE_LOCATING:
                switch (msg) {
                    case MSG_NAVIGATION_DONE:
                        return STATE_CONNECTED;
                    case MSG_NAVIGATION_LOCATED:
                        return STATE_RUNNING;
                    default:
                        throw new IllegalStateException("State " + state + " does not know about message: " + msg);
                }
            case STATE_RUNNING:
                switch (msg) {
                    case MSG_NAVIGATION_LOCATED: // used for updates during navigation
                        return STATE_RUNNING;
                    case MSG_NAVIGATION_DONE:
                        return STATE_CONNECTED;
                    case MSG_NAVIGATION_FINISHED:
                        return STATE_FINISHED;
                    case MSG_BLUETOOTH_ERROR:
                        return STATE_CONNECTING;
                    default:
                        throw new IllegalStateException("State " + state + " does not know about message: " + msg);
                }
            case STATE_FINISHED:
                switch (msg) {
                    case MSG_NAVIGATION_FINISHED: // used for updates during navigation
                        return STATE_FINISHED;
                    case MSG_NAVIGATION_LOCATED: // used for updates during navigation
                        return STATE_RUNNING;
                    case MSG_NAVIGATION_DONE:
                        return STATE_CONNECTED;
                    case MSG_BLUETOOTH_ERROR:
                        return STATE_CONNECTING;
                    default:
                        throw new IllegalStateException("State " + state + " does not know about message: " + msg);
                }
            default:
                throw new IllegalStateException("Unknown state: " + state);
        }
    }

    private void startState() {
        Log.d(TAG, "onStateStart: " + state);
        switch (state) {
            case STATE_STARTED:
                Log.d(TAG, "STATE_STARTED");
                break;
            case STATE_CONNECTING:
                Log.d(TAG, "STATE_CONNECTING");
                startBluetooth();
                break;
            case STATE_CONNECTED:
                Log.d(TAG, "STATE_CONNECTED");
                stopLocation();
                break;
            case STATE_LOCATING:
                Log.d(TAG, "STATE_LOCATING");
                startLocation();
                break;
            case STATE_RUNNING:
                Log.d(TAG, "STATE_RUNNING");
                notifyDelta();
                break;
            case STATE_FINISHED:
                Log.d(TAG, "STATE_FINISHED");
                notifyTargetReached();
                break;
        }
    }

    private void stopState() {
        Log.d(TAG, "onStateEnd: " + state);
        switch (state) {
            case STATE_STARTED:
                Log.d(TAG, "STATE_STARTED");
                break;
            case STATE_CONNECTING:
                Log.d(TAG, "STATE_CONNECTING");
                break;
            case STATE_CONNECTED:
                Log.d(TAG, "STATE_CONNECTED");
                break;
            case STATE_LOCATING:
                Log.d(TAG, "STATE_LOCATING");
                break;
            case STATE_RUNNING:
                Log.d(TAG, "STATE_RUNNING");
                break;
            case STATE_FINISHED:
                Log.d(TAG, "STATE_FINISHED");
                break;
        }
    }

    // Bluetooth service

    private void startBluetooth() {
        Log.d(TAG, "startBluetooth");
        new Thread(new Runnable() {
            @Override
            public void run() {
                /* Uncomment to skip Bluetooth
                sendMessage(MSG_BLUETOOTH_CONNECTED);
                //*/
                //*
                try {
                    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                    for (String macAddress : MAC_ADDRESSES) {
                        BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(macAddress);
                        BluetoothSocket bluetoothSocket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(uuid);
                        bluetoothSocket.connect();
                        sockets.add(bluetoothSocket);
                        Log.d(TAG, "Connected to glove: " + macAddress);
                    }
                    sendMessage(MSG_BLUETOOTH_CONNECTED);
                } catch (Exception e) {
                    // TODO Introduce bluetooth-error state
                    stopBluetooth();
                    Log.e(TAG, "Unable to establish a Bluetooth connection", e);
                }
                //*/
            }
        }).start();
    }

    private void stopBluetooth() {
        Log.d(TAG, "stopBluetooth");
        for (BluetoothSocket socket : sockets) {
            try {
                socket.close();
            }
            catch (IOException e) {
                Log.e(TAG, "Unable to close Bluetooth socket to " + socket.getRemoteDevice().getAddress(), e);
            }
        }
        sockets.clear();
        sendMessage(MSG_BLUETOOTH_ERROR);
    }

    private void activateMotor(final int hand, final int motor) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "activateMotor: " + motor + ", " + hand);
                    byte[] buffer = Integer.toString(motor).getBytes();
                    sockets.get(hand).getOutputStream().write(buffer);
                } catch (Exception e) {
                    // TODO Introduce bluetooth-error state
                    stopBluetooth();
                    Log.e(TAG, "Unable to activate motor", e);
                }
            }
        }).start();
    }

    private void notifyTargetReached() {
        for (int motor = 0; motor < 4; motor++) {
            activateMotor(0, motor);
            activateMotor(1, motor);
        }
    }

    private void notifyDelta() {
        int hand = navigationDelta >= 0 ? 0 : 1;
        float absoluteDelta = Math.abs(navigationDelta);
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

    // Location service

    @Override
    public void onLocationChanged(Location location) {
        if (state > STATE_CONNECTED) {
            //Log.v(TAG, location.toString());
            if (locationsIndex < LOCATIONS_SIZE) {
                locations[locationsIndex] = location;
                Log.v(TAG, "Previous location " + locationsIndex + " initialized");
                locationsIndex++;
                Log.v(TAG, "Set previous locations index: " + locationsIndex);
            }
            else {
                System.arraycopy(locations, 1, locations, 0, LOCATIONS_SIZE - 1);
                locations[LOCATIONS_SIZE - 1] = location;
                float bearing = location.bearingTo(navigationTarget);
                float course = calculateCourse();
                float delta = bearing - course;
                if (delta < -180)
                    delta += 360;
                else if (delta > 180)
                    delta -= 360;
                Log.v(TAG, "Bearing: " + bearing);
                Log.v(TAG, "Course: " + course);
                Log.v(TAG, "Delta: " + delta);
                navigationDelta = delta;
            }
            navigationDistance = location.distanceTo(navigationTarget);
            if (navigationDistance < 5.0) {
                sendMessage(MSG_NAVIGATION_FINISHED);
            }
            else if (locationsIndex >= LOCATIONS_SIZE) {
                sendMessage(MSG_NAVIGATION_LOCATED);
            }
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

    private float calculateCourse() {
        float distSum = 0;
        float courseSum = 0;
        float courseSum_ = 0;
        float coursePrev = 0;
        float coursePrev_ = 0;
        float diffSum = 0;
        float diffSum_ = 0;
        for (int i = 0; i < LOCATIONS_SIZE; i++) {
            for (int j = i + 1; j < LOCATIONS_SIZE; j++) {
                float d = (float) Math.pow(locations[i].distanceTo(locations[j]), 2);
                float c = locations[i].bearingTo(locations[j]);
                float c_ = c < 0 ? c + 360 : c;
                distSum += d;
                courseSum += c * d;
                courseSum_ += c_ * d;
                if (i + j > 0) {
                    diffSum += Math.pow(coursePrev - c, 2);
                    diffSum_ += Math.pow(coursePrev_ - c_, 2);
                }
                coursePrev = c;
                coursePrev_ = c_;
            }
        }
        float course = courseSum/distSum;
        float course_ = courseSum_/distSum;
        if (course_ > 180)
            course_ -= 360;
        Log.v(TAG, "Diff:   " + diffSum + " \\ " + diffSum_);
        Log.v(TAG, "Course: " + course + " \\ " + course_);
        return diffSum < diffSum_? course : course_;
    }

    private void startLocation() {
        try {
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager != null) {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (lastLocation != null) {
                        onLocationChanged(lastLocation);
                    } else {
                        Log.d(TAG, "No last known position");
                    }
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 1, this);
                } else {
                    // TODO Go back to connected state
                    // TODO Introduce bluetooth-error state
                    Toast.makeText(this, "GPS has to be enabled to track your position.", Toast.LENGTH_LONG).show();
                }
            }
            else {
                Log.d(TAG, "No location manager");
            }
        } catch (SecurityException e) {
            // TODO Go back to connected state
            // TODO Introduce location-error message & state
            Toast.makeText(this, "Location permissions are required to track your position.", Toast.LENGTH_LONG).show();
            sendMessage(MSG_BLUETOOTH_ERROR);
        }
    }

    private void stopLocation() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager != null)
            locationManager.removeUpdates(this);
        else
            Log.d(TAG, "No location manager");
        navigationTarget = null;
        navigationDelta = 0;
        locationsIndex = 0;
        locations = new Location[LOCATIONS_SIZE];
    }
}
