package com.example.nihil.datacollector;

/**
 * Created by Nihil on 1/10/2015.
 */

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.List;

public class DataCollectorListenerService extends WearableListenerService implements SensorEventListener {
    private static final String TAG = "DataCollectorListenerService";
    private static final String START_COLLECT_PATH = "/start-collect-data";
    private static final String STOP_COLLECT_PATH = "/stop-collect-data";
    private static final String SENSOR_DATA_PATH = "/current-sensor-data";
    private static final Integer CYCLE_WINDOW = 10000;
    private boolean isCollecting=false;
    private GoogleApiClient mGoogleApiClient;
    private SensorManager sensorManager;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;
    private List<Sensor> availableSensorsList;
    private PutDataMapRequest currentSensorData;
    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient个 = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();
        sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                TAG);

    }

    @Override // SensorEventListener
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        return;
    }

    @Override // SensorEventListener
    public final void onSensorChanged(SensorEvent event) {
        String key = event.sensor.getName();
        float[] values = event.values;
        int currentAccuracy = currentSensorData.getDataMap().getInt(key + " Accuracy");
        if(event.accuracy > currentAccuracy) {
            Log.d(TAG, "New reading for sensor: " + key);
            currentSensorData.getDataMap().putFloatArray(key, values);
            currentSensorData.getDataMap().putInt(key + " Accuracy", event.accuracy);
        }
        if(System.currentTimeMillis()-currentSensorData.getDataMap().getLong("Timestamp")>CYCLE_WINDOW){
            sendCurrentSensorData();
            startNewCycle();
        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        String path = messageEvent.getPath();
        Log.d(TAG, "onMessageReceived: " + path);
        if (path.equals(START_COLLECT_PATH)) {
            if(isCollecting){
                Log.d(TAG, "listenersAlreadyStarted.");
                return;
            }
            try {
                acquireWakeLock();
                startSensorListeners();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if(path.equals(STOP_COLLECT_PATH)){
            if(!isCollecting){
                Log.d(TAG, "ListenersAlreadyStopped.");
                return;
            }
            try {
                stopSensorListeners();
                releaseWakeLock();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onPeerConnected(Node peer) {
        Log.d(TAG, "onPeerConnected: " + peer);
    }

    @Override
    public void onPeerDisconnected(Node peer) {
        Log.d(TAG, "onPeerDisconnected: " + peer);
    }

    private void startSensorListeners() {
        Log.d(TAG, "startSensorListeners");
        availableSensorsList = sensorManager.getSensorList(Sensor.TYPE_ALL);
        startNewCycle();
        for (Sensor sensor : availableSensorsList ) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        isCollecting=true;

    }

    private void stopSensorListeners() {
        Log.d(TAG, "stopSensorListeners");
        sensorManager.unregisterListener(DataCollectorListenerService.this);
        isCollecting=false;
    }

    private void startNewCycle(){
        currentSensorData = PutDataMapRequest.create(SENSOR_DATA_PATH);
        currentSensorData.getDataMap().putLong("Timestamp", System.currentTimeMillis());
        float[] empty = new float[0];
        for (Sensor sensor : availableSensorsList) {
            currentSensorData.getDataMap().putFloatArray(sensor.getName(), empty);
            currentSensorData.getDataMap().putInt(sensor.getName() + " Accuracy", 0);
        }
    }

    private void sendCurrentSensorData() {
        Log.d(TAG, "sendSensorData");
        PutDataRequest request = currentSensorData.asPutDataRequest();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request);
    }

    private void acquireWakeLock() {
        Log.d(TAG, "acquireWakeLock");
       // wakeLock.acquire();
    }

    private void releaseWakeLock() {
        Log.d(TAG, "releaseWakeLock");
        //wakeLock.release();
    }
}