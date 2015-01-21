package com.example.nihil.datacollector;

/**
 * Created by Nihil on 1/10/2015.
 */
import android.os.Build;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;
import android.util.Log;

import java.util.Collection;
import java.util.HashSet;
import  java.util.Iterator;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import com.google.android.gms.wearable.DataMap;
import java.util.ArrayList;
import java.util.List;
public class DataCollectorListenerService extends WearableListenerService implements SensorEventListener {
    private static final String TAG = "DataCollectorListenerService";
    private static final String START_COLLECT_PATH = "/start-collect-data";
    private static final String STOP_COLLECT_PATH = "/stop-collect-data";
    private static final String SENSOR_DATA_PATH = "/current-sensor-data";
    private static final int CYCLE_WINDOW=3000;
    private static final int BUFFER_SIZE=10;
    private  GoogleApiClient mGoogleApiClient;
    private  SensorManager sensorManager;
    private  PowerManager powerManager;
    private  PowerManager.WakeLock wakeLock;
    private  SyncBuffer mySyncBuffer;
    private  DataMap currentSensorData;
    private static boolean isCollecting=false;

    private class SyncBuffer{
        private ArrayList<DataMap> dataBuffer;
        public void clear(){
            dataBuffer=new ArrayList<DataMap>();
        }
        public void insert(DataMap item){
            dataBuffer.add(item);
            Log.d(TAG,"insertToSyncBuffer.");
            if(dataBuffer.size()>=BUFFER_SIZE){
                this.doSync();
                this.clear();
            }
        }
        public void doSync(){
            long currentTime=System.currentTimeMillis();
            PutDataMapRequest mapRequest = PutDataMapRequest.create(SENSOR_DATA_PATH+"/"+String.valueOf(currentTime));
            mapRequest.getDataMap().putDataMapArrayList("dataPackage",dataBuffer);
            mapRequest.getDataMap().putLong("packTime",currentTime);
            mapRequest.getDataMap().putString("deviceID",Build.SERIAL);
            PutDataRequest request =  mapRequest.asPutDataRequest();
            Wearable.DataApi.putDataItem(mGoogleApiClient, request);
            Log.d(TAG, "syncBufferSent: "+SENSOR_DATA_PATH+"/"+String.valueOf(currentTime));
        }
    }
    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
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
       if(!isCollecting){
           sensorManager.unregisterListener(this, event.sensor);
           Log.d(TAG,"unexpectedSensorEvent");
           return ;
        }
        if(event.accuracy<sensorManager.SENSOR_STATUS_ACCURACY_MEDIUM){
            return;
        }
        String key = event.sensor.getName();
        float[] values = event.values;
        Log.d(TAG, "newReading: " + key);
        currentSensorData.putFloatArray(key, values);
        if(System.currentTimeMillis()-currentSensorData.getLong("timestamp")>CYCLE_WINDOW){
            mySyncBuffer.insert(currentSensorData);
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
                sendMsgToPhone("Collect Already Started.");
                return;
            }
            try {
                acquireWakeLock();
                startSensorListeners();
                sendMsgToPhone("Collect Start.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        else if(path.equals(STOP_COLLECT_PATH)){
           if(!isCollecting){
                Log.d(TAG, "listenersAlreadyStopped.");
                sendMsgToPhone("Collect Already Stopped.");
                return;
            }
            try {
                releaseWakeLock();
                stopSensorListeners();
                sendMsgToPhone("Collect Stop.");
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
        mySyncBuffer=new SyncBuffer();
        Log.d(TAG, "startSensorListeners");
        List<Sensor> availableSensorsList= sensorManager.getSensorList(Sensor.TYPE_ALL);
        mySyncBuffer.clear();
        startNewCycle();
        for (Sensor sensor : availableSensorsList ) {
            int type=sensor.getType();
            if(type==Sensor.TYPE_AMBIENT_TEMPERATURE||type==Sensor.TYPE_GYROSCOPE||type==Sensor.TYPE_MAGNETIC_FIELD||type==Sensor.TYPE_ROTATION_VECTOR||type==Sensor.TYPE_ACCELEROMETER){
                sensorManager.registerListener(this, sensor, 2000000);
            }
        }
        isCollecting=true;
    }

    private void stopSensorListeners() {
        Log.d(TAG, "stopSensorListeners");
        sensorManager.unregisterListener(this);
        mySyncBuffer=null;
        currentSensorData=null;
        isCollecting=false;
    }

    private void startNewCycle(){
        currentSensorData = new DataMap();
        currentSensorData.putLong("timestamp",System.currentTimeMillis());
    }
    private void sendMsgToPhone(String msg){
        NodeApi.GetConnectedNodesResult nodes =
                Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
        for (Node node : nodes.getNodes()) {
            Wearable.MessageApi.sendMessage(mGoogleApiClient, node.getId(), msg, new byte[0]);
        }
    }



    private void acquireWakeLock() {

        if(!wakeLock.isHeld()) {
            Log.d(TAG, "acquireWakeLock");
            //wakeLock.acquire();
        }
    }

    private void releaseWakeLock() {
        if(wakeLock.isHeld()) {
            Log.d(TAG, "releaseWakeLock");
           // wakeLock.release();
        }
    }
}