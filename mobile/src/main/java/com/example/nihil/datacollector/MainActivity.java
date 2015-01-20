package com.example.nihil.datacollector;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.MenuItem;
import android.content.Context;
import android.content.Intent;

public class MainActivity extends ActionBarActivity {

    private static final String START_COLLECT_PATH = "/start-collect-data";
    private static final String STOP_COLLECT_PATH = "/stop-collect-data";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    public void onSendStopMsg(View v){
        Intent service = new Intent(this, WearableMsgService.class);
        Bundle bundle = new Bundle();
        bundle.putString("MYMSG", STOP_COLLECT_PATH);
        service.putExtras(bundle);
        startService(service);
    }
    public void onSendStartMsg(View v){
        Intent service = new Intent(this, WearableMsgService.class);
        Bundle bundle = new Bundle();
        bundle.putString("MYMSG", START_COLLECT_PATH);
        service.putExtras(bundle);
        startService(service);
    }


}
