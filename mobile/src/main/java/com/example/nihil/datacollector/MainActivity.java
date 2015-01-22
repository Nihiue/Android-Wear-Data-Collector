package com.example.nihil.datacollector;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.MenuItem;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.widget.TextView;
import android.content.IntentFilter;
import android.widget.Toast;
public class MainActivity extends ActionBarActivity {
    private TextView mDataTextView;
    private static final String START_COLLECT_PATH = "/start-collect-data";
    private static final String STOP_COLLECT_PATH = "/stop-collect-data";
    public static final String BC_DATA="com.example.nihil.datacollector.DATA_UPDATE";
    public static final String BC_INFO="com.example.nihil.datacollector.INFO";
    public MessageReceiver mMessageReceiver;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mDataTextView = (TextView) findViewById(R.id.data_text);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BC_DATA);
        intentFilter.addAction(BC_INFO);
        mMessageReceiver=new MessageReceiver();
        registerReceiver(mMessageReceiver,intentFilter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
    @Override
    protected void onDestroy() {
        unregisterReceiver(mMessageReceiver);
        super.onDestroy();
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_start) {
            Intent service = new Intent(this, WearableMsgService.class);
            Bundle bundle = new Bundle();
            bundle.putString("MYMSG", START_COLLECT_PATH);
            service.putExtras(bundle);
            startService(service);
            return true;
        }
       else if (id == R.id.action_stop) {
            Intent service = new Intent(this, WearableMsgService.class);
            Bundle bundle = new Bundle();
            bundle.putString("MYMSG", STOP_COLLECT_PATH);
            service.putExtras(bundle);
            startService(service);
            return true;
        }
       else if (id == R.id.action_clear) {
            mDataTextView.setText("");
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
     public class MessageReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            if (intent.getAction().equals(BC_DATA)) {
                mDataTextView.setText(mDataTextView.getText()+intent.getStringExtra("data")+"\n\n");
                Toast.makeText(MainActivity.this, "New Data",Toast.LENGTH_SHORT).show();
            }
            if (intent.getAction().equals(BC_INFO)) {
                Toast.makeText(MainActivity.this, intent.getStringExtra("data"),Toast.LENGTH_SHORT).show();
            }
        }

    }

}
