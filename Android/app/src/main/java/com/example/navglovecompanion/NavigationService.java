package com.example.navglovecompanion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;

public class NavigationService extends Service {

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

    private static final String TAG = "NavigationService";
    private static final String NOTIFICATION_CHANNEL_ID = "NAVI";
    private static final int ONGOING_NOTIFICATION_ID = 23;

    private static final int MSG_SERVICE_CREATED = 0;
    private static final int MSG_BLUETOOTH_CONNECTED = 1;
    private static final int MSG_BLUETOOTH_ERROR = 2;
    private static final int MSG_NAVIGATION_STARTED = 3;
    private static final int MSG_NAVIGATION_LOCATED = 3;
    private static final int MSG_NAVIGATION_FINISHED = 4;
    private static final int MSG_NAVIGATION_DONE = 5;

    private final IBinder binder = new NavigationServiceBinder();

    ArrayList<NavigationServiceListener> listeners = new ArrayList<NavigationServiceListener>();
    private int state = STATE_STARTED;

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

    // Service control

    public void addStateChangeListener(NavigationServiceListener listener) {
        listeners.add(listener);
    }

    public void removeStateChangeListener(NavigationServiceListener listener) {
        listeners.remove(listener);
    }

    public int getState() {
        return state;
    }

    public void startNavigation() {
        startForeground(ONGOING_NOTIFICATION_ID, buildNotification());
        startService(new Intent(getApplicationContext(), NavigationService.class));
        sendMessage(MSG_NAVIGATION_STARTED);
        Log.d(TAG, "Navigation started");
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

    // State management

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
            Toast.makeText(this, "Error in navigation service", Toast.LENGTH_SHORT).show();
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
        Log.d(TAG, "onStateStart " + state);
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
                break;
            case STATE_LOCATING:
                Log.d(TAG, "STATE_LOCATING");
                startLocation();
                break;
            case STATE_RUNNING:
                Log.d(TAG, "STATE_RUNNING");
                break;
            case STATE_FINISHED:
                Log.d(TAG, "STATE_FINISHED");
                break;
        }
    }

    private void stopState() {
        Log.d(TAG, "onStateEnd " + state);
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
        sendMessage(MSG_BLUETOOTH_CONNECTED);
    }

    // Location service

    private void startLocation() {
        sendMessage(MSG_NAVIGATION_LOCATED);
        sendMessage(MSG_NAVIGATION_FINISHED);
    }
}
