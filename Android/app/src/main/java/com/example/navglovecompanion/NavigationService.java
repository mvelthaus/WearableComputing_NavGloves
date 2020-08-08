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

    public static final int STATE_ERROR = -2;
    public static final int STATE_PAUSED = -1;
    public static final int STATE_CREATED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    public static final int STATE_STARTING = 3;
    public static final int STATE_LOCATING = 4;
    public static final int STATE_FINISHED = 5;

    public static final int MSG_START_BLUETOOTH = 1;
    public static final int MSG_START_NAVIGATION = 2;
    public static final int MSG_STOP_NAVIGATION = 3;

    private static final int MSG_BLUETOOTH_CONNECTED = 10;
    private static final int MSG_BLUETOOTH_FAILED = 11;
    private static final int MSG_NAVIGATION_STARTED = 20;
    private static final int MSG_NAVIGATION_LOCATED = 21;
    private static final int MSG_NAVIGATION_FINISHED = 22;
    private static final int MSG_NAVIGATION_FAILED = 23;

    private static final String TAG = "NavigationService";
    private static final String NOTIFICATION_CHANNEL_ID = "NAVI";
    private static final int ONGOING_NOTIFICATION_ID = 23;
    // Mac-Adressen und UUIDs der Bluetooth Module am Lilypad
    private static final String[] MAC_ADDRESSES = {"00:13:01:04:18:76", "98:D3:A1:F5:CA:E7"};
    private static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int LOCATIONS_SIZE = 10;

    private final IBinder binder = new NavigationServiceBinder();

    private ArrayList<NavigationServiceListener> listeners = new ArrayList<>();
    private Location[] locations;
    private int locationsIndex;
    private float navigationCourseBearing;
    private float navigationCourseLocation;
    private float navigationDeltaBearing;
    private float navigationDeltaLocation;
    private float navigationDistance;
    private String navigationInput;
    private Location navigationTarget;
    private ArrayList<BluetoothSocket> sockets = new ArrayList<>();
    private int state = STATE_CREATED;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();
        sendMessage(MSG_START_BLUETOOTH);
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

    // Getter & setter

    public float getNavigationCourseBearing() {
        return navigationCourseBearing;
    }

    public float getNavigationCourseLocation() {
        return navigationCourseLocation;
    }

    public float getNavigationDeltaBearing() {
        return navigationDeltaBearing;
    }

    public float getNavigationDeltaLocation() {
        return navigationDeltaLocation;
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

    public void setNavigationInput(String input) {
        navigationInput = input;
    }

    // Navigation control

    private void startNavigation() {
        Location target = parseInput(navigationInput);
        if (target != null) {
            startForeground(ONGOING_NOTIFICATION_ID, buildNotification());
            startService(new Intent(getApplicationContext(), NavigationService.class));
            // TODO allow passing of data along with message
            navigationTarget = target;
            Log.d(TAG, "Navigation started");
        }
        else {
            // TODO Move toast to activity
            Toast.makeText(this, "The entered text does not describe a valid position", Toast.LENGTH_LONG).show();
            sendMessage(MSG_STOP_NAVIGATION);
        }
    }

    private void stopNavigation() {
        stopForeground(true);
        stopSelf();
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

    public void sendMessage(int msg) {
        Log.d(TAG, "sendMessage: " + msg);
        try {
            int oldState = state;
            int newState = processMessage(msg);
            state = newState;
            startState(oldState, newState);
            for (NavigationServiceListener listener : listeners)
                listener.onStateChange(oldState, newState);
        }
        catch (Exception e) {
            Log.e(TAG, "Error while sending message", e);
        }
    }

    private int processMessage(int msg) throws IllegalStateException {
        switch (state) {
            case STATE_CREATED:
                switch (msg) {
                    case MSG_START_BLUETOOTH:
                        return STATE_CONNECTING;
                    default:
                        throw new IllegalStateException("State " + state + " does not know about message: " + msg);
                }
            case STATE_CONNECTING:
                switch (msg) {
                    case MSG_BLUETOOTH_CONNECTED:
                        return STATE_CONNECTED;
                    case MSG_BLUETOOTH_FAILED:
                        return STATE_ERROR;
                    default:
                        throw new IllegalStateException("State " + state + " does not know about message: " + msg);
                }
            case STATE_CONNECTED:
                switch (msg) {
                    case MSG_START_NAVIGATION:
                        return STATE_STARTING;
                    default:
                        throw new IllegalStateException("State " + state + " does not know about message: " + msg);
                }
            case STATE_STARTING:
                switch (msg) {
                    case MSG_NAVIGATION_STARTED: // trigger updates during navigation
                        return STATE_STARTING;
                    case MSG_NAVIGATION_LOCATED:
                        return STATE_LOCATING;
                    case MSG_NAVIGATION_FINISHED:
                        return STATE_FINISHED;
                    case MSG_STOP_NAVIGATION:
                        return STATE_CONNECTED;
                    case MSG_NAVIGATION_FAILED:
                        return STATE_PAUSED;
                    default:
                        throw new IllegalStateException("State " + state + " does not know about message: " + msg);
                }
            case STATE_LOCATING:
                switch (msg) {
                    case MSG_NAVIGATION_LOCATED: // trigger updates during navigation
                        return STATE_LOCATING;
                    case MSG_NAVIGATION_FINISHED:
                        return STATE_FINISHED;
                    case MSG_STOP_NAVIGATION:
                        return STATE_CONNECTED;
                    case MSG_NAVIGATION_FAILED:
                        return STATE_PAUSED;
                    case MSG_BLUETOOTH_FAILED:
                        return STATE_ERROR;
                    default:
                        throw new IllegalStateException("State " + state + " does not know about message: " + msg);
                }
            case STATE_FINISHED:
                switch (msg) {
                    case MSG_NAVIGATION_FINISHED: // trigger updates during navigation
                        return STATE_FINISHED;
                    case MSG_NAVIGATION_STARTED: // jump back, if user moves beyond target
                        return STATE_STARTING;
                    case MSG_NAVIGATION_LOCATED: // jump back, if user moves beyond target
                        return STATE_LOCATING;
                    case MSG_STOP_NAVIGATION:
                        return STATE_CONNECTED;
                    case MSG_NAVIGATION_FAILED:
                        return STATE_PAUSED;
                    case MSG_BLUETOOTH_FAILED:
                        return STATE_ERROR;
                    default:
                        throw new IllegalStateException("State " + state + " does not know about message: " + msg);
                }
            case STATE_PAUSED:
                switch (msg) {
                    case MSG_START_NAVIGATION:
                        return STATE_STARTING;
                    case MSG_STOP_NAVIGATION:
                        return STATE_CONNECTED;
                    default:
                        throw new IllegalStateException("State " + state + " does not know about message: " + msg);
                }
            case STATE_ERROR:
                switch (msg) {
                    case MSG_START_BLUETOOTH:
                        return STATE_CONNECTING;
                    default:
                        throw new IllegalStateException("State " + state + " does not know about message: " + msg);
                }
            default:
                throw new IllegalStateException("Unknown state: " + state);
        }
    }

    private void startState(int oldState, int newState) {
        Log.d(TAG, "onStateStart: " + newState);
        switch (newState) {
            case STATE_CREATED:
                Log.d(TAG, "STATE_CREATED");
                break;
            case STATE_CONNECTING:
                Log.d(TAG, "STATE_CONNECTING");
                startBluetooth();
                break;
            case STATE_CONNECTED:
                Log.d(TAG, "STATE_CONNECTED");
                stopNavigation();
                stopLocation();
                break;
            case STATE_STARTING:
                Log.d(TAG, "STATE_STARTING");
                if (oldState != STATE_STARTING) {
                    startNavigation();
                    startLocation();
                }
                break;
            case STATE_LOCATING:
                Log.d(TAG, "STATE_LOCATING");
                notifyDelta();
                break;
            case STATE_FINISHED:
                Log.d(TAG, "STATE_FINISHED");
                notifyTargetReached();
                break;
            case STATE_PAUSED:
                Log.d(TAG, "STATE_PAUSED");
                break;
            case STATE_ERROR:
                Log.d(TAG, "STATE_ERROR");
                stopBluetooth();
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
                /*/
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
                    Log.e(TAG, "Unable to connect to glove", e);
                    sendMessage(MSG_BLUETOOTH_FAILED);
                }
                //*/
            }
        }).start();
    }

    private void stopBluetooth() {
        Log.d(TAG, "stopBluetooth");
        // TODO Move into thread and sent MSG_BLUETOOTH_STOPPED when done
        for (BluetoothSocket socket : sockets) {
            try {
                socket.close();
            }
            catch (IOException e) {
                Log.d(TAG, "Unable to close connection", e);
            }
        }
        sockets.clear();
    }

    // Public to allow debugging
    public void activateMotor(final int hand, final int motor) {
        Log.d(TAG, "activateMotor: " + motor + ", " + hand);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] buffer = Integer.toString(motor).getBytes();
                    sockets.get(hand).getOutputStream().write(buffer);
                } catch (Exception e) {
                    Log.e(TAG, "Unable to activate motor", e);
                    sendMessage(MSG_BLUETOOTH_FAILED);
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
        int hand = navigationDeltaLocation >= 0 ? 0 : 1;
        float absoluteDelta = Math.abs(navigationDeltaLocation);
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
        Log.v(TAG, location.toString());
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
            Log.v(TAG, "Bearing: " + bearing);

            float courseLocation = calculateCourseLocation();
            float deltaLocation = normalizeDelta(bearing - courseLocation);
            Log.v(TAG, "CourseLocation: " + courseLocation);
            Log.v(TAG, "DeltaLocation: " + deltaLocation);
            navigationCourseLocation = courseLocation;
            navigationDeltaLocation = deltaLocation;

            float courseBearing = calculateCourseBearing();
            float deltaBearing = normalizeDelta(bearing - courseBearing);
            Log.v(TAG, "CourseBearing: " + courseBearing);
            Log.v(TAG, "DeltaBearing: " + deltaBearing);
            navigationCourseBearing = courseBearing;
            navigationDeltaBearing = deltaBearing;
        }
        navigationDistance = location.distanceTo(navigationTarget);
        if (state > STATE_CONNECTED) {
            if (navigationDistance < 5.0) {
                sendMessage(MSG_NAVIGATION_FINISHED);
            }
            else if (locationsIndex < LOCATIONS_SIZE) {
                sendMessage(MSG_NAVIGATION_STARTED);
            }
            else {
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
        // Nothing more to do here
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, "Provider disabled: " + provider);
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            sendMessage(MSG_NAVIGATION_FAILED);
        }
    }

    private float normalizeDelta(float delta) {
        if (delta < -180)
            return delta + 360;
        else if (delta > 180)
            return delta - 360;
        else
            return delta;
    }

    private float calculateCourseLocation() {
        float distSum = 0;
        float courseSum = 0;
        float courseSum_ = 0;
        float coursePrev = 0;
        float coursePrev_ = 0;
        float diffSum = 0;
        float diffSum_ = 0;
        for (int i = 0; i < LOCATIONS_SIZE; i++) {
            for (int j = i + 1; j < LOCATIONS_SIZE; j++) {
                float a = (locations[i].getAccuracy() + locations[j].getAccuracy()) / 2;
                //float a = 5;
                float d = locations[i].distanceTo(locations[j]);
                if (d > a) {
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
        }
        float course = courseSum/distSum;
        float course_ = courseSum_/distSum;
        if (course_ > 180)
            course_ -= 360;
        Log.v(TAG, "DiffLocation:   " + diffSum + " \\ " + diffSum_);
        Log.v(TAG, "CourseLocation: " + course + " \\ " + course_);
        return diffSum < diffSum_? course : course_;
    }

    private float calculateCourseBearing() {
        float distSum = 0;
        float courseSum = 0;
        float courseSum_ = 0;
        float coursePrev = 0;
        float coursePrev_ = 0;
        float diffSum = 0;
        float diffSum_ = 0;
        for (int i = 0; i < LOCATIONS_SIZE; i++) {
            if (locations[i].hasBearing()) { // Invert for debugging
                // Use the bearing accuracy as weight and the horizontal accuracy as (lesser weighted) a backup
                float d = 10 - locations[i].getBearingAccuracyDegrees();
                if (d == 10)
                    d = (10 - locations[i].getAccuracy()) / 2;
                    //d = 2.5f; // For debugging
                if (d > 0) {
                    //float c = locations[i-1].bearingTo(locations[i]); // For debugging
                    float c = locations[i].getBearing();
                    c = c > 180 ? c - 360 : c;
                    float c_ = c < 0 ? c + 360 : c;
                    distSum += d;
                    courseSum += c * d;
                    courseSum_ += c_ * d;
                    if (i > 1) {
                        diffSum += Math.pow(coursePrev - c, 2);
                        diffSum_ += Math.pow(coursePrev_ - c_, 2);
                    }
                    coursePrev = c;
                    coursePrev_ = c_;
                }
            }
        }
        float course = courseSum/distSum;
        float course_ = courseSum_/distSum;
        if (course_ > 180)
            course_ -= 360;
        Log.v(TAG, "DiffLocation:   " + diffSum + " \\ " + diffSum_);
        Log.v(TAG, "CourseLocation: " + course + " \\ " + course_);
        return diffSum < diffSum_? course : course_;
    }

    private void startLocation() {
        try {
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager != null) {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, this);
                    sendMessage(MSG_NAVIGATION_STARTED);
                } else {
                    Log.w(TAG, "GPS provider is not enabled");
                    sendMessage(MSG_NAVIGATION_FAILED);
                }
            }
            else {
                Log.w(TAG, "No location manager");
                sendMessage(MSG_NAVIGATION_FAILED);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "No permission to use GPS");
            sendMessage(MSG_NAVIGATION_FAILED);
        }
    }

    private void stopLocation() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager != null)
            locationManager.removeUpdates(this);
        else
            Log.d(TAG, "No location manager");
        navigationTarget = null;
        navigationDeltaLocation = 0;
        locationsIndex = 0;
        locations = new Location[LOCATIONS_SIZE];
    }
}
