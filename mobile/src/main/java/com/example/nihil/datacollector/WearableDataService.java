package com.example.nihil.datacollector;

import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import java.util.concurrent.TimeUnit;

public class WearableDataService extends WearableListenerService{
    private static final String TAG = "WearableDataService";
    private static final String SENSOR_DATA_PATH = "/current-sensor-data";
    private GoogleApiClient mGoogleApiClient;
   // private SharedPreferences preferences;

    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();

    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d(TAG, "onDataChanged: " + dataEvents);
        final List<DataEvent> events = FreezableUtils.freezeIterable(dataEvents);
        dataEvents.close();
        if(!mGoogleApiClient.isConnected()) {
            ConnectionResult connectionResult = mGoogleApiClient
                    .blockingConnect(3, TimeUnit.SECONDS);
            if (!connectionResult.isSuccess()) {
                Log.e(TAG, "DataLayerListenerService failed to connect to GoogleApiClient.");
                return;
            }
        }

        // Loop through the events and send a message back to the node that created the data item.
        for (DataEvent event : events) {
            Uri uri = event.getDataItem().getUri();
            String path = uri.getPath();
            if (path.indexOf(SENSOR_DATA_PATH)!=-1 && event.getType() == DataEvent.TYPE_CHANGED) {
                byte[] rawData = event.getDataItem().getData();
                DataMap sensorData = DataMap.fromByteArray(rawData);
                Log.d(TAG, "Recording new data item: " + path);
                saveData(sensorData);
            }
        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        String path = messageEvent.getPath();
        Log.d(TAG, "onMessageReceived: " + path);
        Intent mIntent=new Intent();
        mIntent.setAction(MainActivity.BC_INFO);
        mIntent.putExtra("data", "Wear: "+path);
        sendBroadcast(mIntent);

    }
    private JSONObject dataMapAsJSONObject(DataMap data) {
        String dispStr="";
        DateFormat dfmt = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        JSONObject json=new JSONObject();
        try {
        Timestamp ts=new Timestamp(data.getLong("packTime"));
        dispStr+=("packTime : "+dfmt.format(ts)+"\n");
        dispStr+=("packSize : "+String.valueOf(data.getDataMapArrayList("dataPackage").size())+"\n");
        dispStr+=("deviceID : "+data.getString("deviceID")+"\n");

        json.put("packTime", data.getLong("packTime"));
        json.put("deviceID", data.getString("deviceID"));
        ArrayList<DataMap> pack= data.getDataMapArrayList("dataPackage");
        JSONArray itemArray=new JSONArray();
        for(int i=0;i<pack.size();i++){
            JSONObject item=new JSONObject();
            item.put("timestamp",pack.get(i).getLong("timestamp"));
            ts=new Timestamp(pack.get(i).getLong("timestamp"));
            dispStr+=("==============================\n"+"Timestamp"+"\n "+dfmt.format(ts)+"\n\n");
            Set<String> keys=pack.get(i).keySet();
            for (String key : keys) {
                if(key.equals("timestamp")){
                   continue;
                }
                JSONArray valueArray=new JSONArray();
                float[] values=pack.get(i).getFloatArray(key);
                dispStr+=(key+":\n");
                for(int k=0;k<values.length;k++){
                    dispStr+=(String.valueOf(values[k])+"\n");
                    valueArray.put(values[k]);
                }
                    dispStr+="\n";
                    item.put(key,valueArray);
            }
            itemArray.put(item);
        }
        json.put("dataPackage",itemArray);

        } catch(JSONException e) {
            Log.d(TAG,e.toString());
        }
        Intent mIntent=new Intent();
        mIntent.setAction(MainActivity.BC_DATA);
        mIntent.putExtra("data", dispStr );
        sendBroadcast(mIntent);
        return json;
    }


    private void saveData(DataMap data) {
        String dataJSON = dataMapAsJSONObject(data).toString() + "\n";
        Log.d("MyJSON",dataJSON);

    }

    @Override
    public void onPeerConnected(Node peer) {
        Log.d(TAG, "onPeerConnected: " + peer);
    }

    @Override
    public void onPeerDisconnected(Node peer) {
        Log.d(TAG, "onPeerDisconnected: " + peer);
    }
}
