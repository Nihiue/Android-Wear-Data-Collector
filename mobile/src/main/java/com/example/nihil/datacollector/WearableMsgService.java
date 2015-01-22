package com.example.nihil.datacollector;

/**
 * Created by Nihil on 1/10/2015.
 */

        import android.app.IntentService;
        import android.content.Intent;
        import android.util.Log;
        import android.content.Intent;
        import com.google.android.gms.common.api.GoogleApiClient;
        import com.google.android.gms.common.api.ResultCallback;
        import com.google.android.gms.wearable.MessageApi;
        import com.google.android.gms.wearable.MessageEvent;
        import com.google.android.gms.wearable.Node;
        import com.google.android.gms.wearable.NodeApi;
        import com.google.android.gms.wearable.Wearable;
        import android.os.Bundle;
        import java.util.Collection;
        import java.util.HashSet;


public class WearableMsgService extends IntentService {
    public WearableMsgService() {
        super("WearableMsgService");
    }

    private static final String TAG = "WearableMsgService";

    private GoogleApiClient client;

    @Override
    public void onCreate() {
        super.onCreate();
        client = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
        client.connect();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle bundle = intent.getExtras();
        String myMsg = bundle.getString("MYMSG");
        Log.d(TAG, "onHandleIntent: " + myMsg );
        for (String node : getNodes()) {
            sendMessage(node, myMsg );
        }
    }

    private Collection<String> getNodes() {
        HashSet<String> results = new HashSet<String>();
        NodeApi.GetConnectedNodesResult nodes =
                Wearable.NodeApi.getConnectedNodes(client).await();

        for (Node node : nodes.getNodes()) {
            results.add(node.getId());
        }

        return results;
    }

    private void sendMessage(String node, final String message) {
        Log.d(TAG, "Sending Message: " + message + " to Node: " + node);
        Wearable.MessageApi.sendMessage(
                client, node, message, new byte[0]).setResultCallback(
                new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                        if (!sendMessageResult.getStatus().isSuccess()) {
                            Log.e(TAG, "Failed to send message with status code: "
                                    + sendMessageResult.getStatus().getStatusCode());
                        }
                    }
                }
        );
    }
}
