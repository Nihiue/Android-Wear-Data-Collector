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
    private static GoogleApiClient mGoogleApiClient;
    private static SensorManager sensorManager;
    private static PowerManager powerManager;
    private static PowerManager.WakeLock wakeLock;
    private static SyncBuffer mSyncBuffer;
    private static CycleManager mCycleManager;
    private static boolean isCollecting;
    static {
       isCollecting=false;
    }
    private class SyncBuffer{
        private static final String SENSOR_DATA_PATH = "/current-sensor-data";
        private static final int BUFFER_SIZE=10;
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
        public void flush(){
            this.doSync();
            this.clear();
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

    private class CycleManager{
        private static final int CYCLE_WINDOW =20000;//20 secs
        private DataMap cycleData;
        public void startNewCycle(){
            cycleData=new DataMap();
            cycleData.putLong("timestamp",System.currentTimeMillis());
        }
        public void onData(SensorEvent event){
            String key = event.sensor.getStringType();
            float[] values = event.values;
            Log.d(TAG, "newReading: " + key);
            cycleData.putFloatArray(key, values);
            long gap=System.currentTimeMillis()-cycleData.getLong("timestamp");
            if(gap>CYCLE_WINDOW){
                cycleData.putLong("cycleGap",gap);
                mSyncBuffer.insert(cycleData);
                this.startNewCycle();
            }
        }
    }
    @Override
    public void onCreate() {
        super.onCreate();
        if(mGoogleApiClient==null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .build();
            mGoogleApiClient.connect();
        }
        if(sensorManager==null){
            sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        }
        if(powerManager==null){
            powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        }
        if(wakeLock==null){
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    TAG);
        }
        if(mSyncBuffer==null){
            mSyncBuffer=new SyncBuffer();
        }
        if(mCycleManager==null){
            mCycleManager=new CycleManager();
        }
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
        if(event.accuracy==sensorManager.SENSOR_STATUS_NO_CONTACT||event.accuracy==sensorManager.SENSOR_STATUS_UNRELIABLE){
            Log.d(TAG,"valueDiscarded:"+event.sensor.getStringType());
            return;
        }
        mCycleManager.onData(event);
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
        Log.d(TAG, "startSensorListeners");
        List<Sensor> availableSensorsList= sensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor sensor : availableSensorsList ) {
            int type=sensor.getType();
          /*  if(type==Sensor.TYPE_AMBIENT_TEMPERATURE||type==Sensor.TYPE_GYROSCOPE||type==Sensor.TYPE_MAGNETIC_FIELD||type==Sensor.TYPE_ROTATION_VECTOR||type==Sensor.TYPE_ACCELEROMETER||type==Sensor.TYPE_HEART_RATE||type==Sensor.TYPE_STEP_COUNTER){
                sensorManager.registerListener(this, sensor, 2000000);
            }*/
            if(type==Sensor.TYPE_HEART_RATE||type==Sensor.TYPE_STEP_COUNTER||type==Sensor.TYPE_STEP_DETECTOR){
                sensorManager.registerListener(this, sensor, 10000000);
            }
        }
        mCycleManager.startNewCycle();
        mSyncBuffer.clear();
        isCollecting=true;
    }

    private void stopSensorListeners() {
        Log.d(TAG, "stopSensorListeners");
        sensorManager.unregisterListener(this);
        mSyncBuffer.flush();
        isCollecting=false;
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
            wakeLock.acquire();
        }
    }

    private void releaseWakeLock() {
        if(wakeLock.isHeld()) {
            Log.d(TAG, "releaseWakeLock");
            wakeLock.release();
        }
    }

}