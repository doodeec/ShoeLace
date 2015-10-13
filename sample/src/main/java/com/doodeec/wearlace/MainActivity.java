package com.doodeec.wearlace;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.doodeec.shoelace.Lace;
import com.doodeec.shoelace.MessageEventType;
import com.doodeec.shoelace.ShoeLace;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    View mSendMessageBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSendMessageBtn = findViewById(R.id.btn_send_message);

        mSendMessageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ShoeLace.sendMessage("Event");
            }
        });

        ShoeLace.init(getApplicationContext());
    }

    @Override
    protected void onResume() {
        super.onResume();
        ShoeLace.tie(this);
    }

    @Override
    protected void onPause() {
        ShoeLace.untie(this);
        super.onPause();
    }

    @Override
    protected void onStop() {
        ShoeLace.dismount();
        super.onStop();
    }

    @Lace("Data")
    void onDataChangedReceived(Asset receivedAsset) {
        Log.d(TAG, "onDataChangedReceived");
        Toast.makeText(this, "onDataChangedReceived", Toast.LENGTH_LONG).show();
    }

    @Lace(value = "Data", type = DataEvent.TYPE_DELETED)
    void onDataDeletedReceived(Asset receivedAsset) {
        Log.d(TAG, "onDataDeletedReceived");
        Toast.makeText(this, "onDataDeletedReceived", Toast.LENGTH_LONG).show();
    }

    @Lace(value = "Event", type = MessageEventType.TYPE_MESSAGE)
    void onMessageEventReceived() {
        Log.d(TAG, "onMessageEventReceived");
        Toast.makeText(this, "onMessageEventReceived", Toast.LENGTH_LONG).show();
    }
}
