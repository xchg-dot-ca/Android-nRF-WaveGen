package com.xchg.wavegen;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.nordicsemi.nrfUARTBescor.MainActivity;
import com.nordicsemi.nrfUARTBescor.R;
import com.nordicsemi.nrfUARTBescor.UartService;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class WaveGenActivity extends Activity {

    private UartService mService = null;
    public static final String TAG = "nRFUART-WAVEGEN-WAVEGEN-ACTIVITY";

    private Button btnOnOff;
    private Button btnSet;
    private EditText editFrequency, editAmplitude;
    Spinner dropdown;

    // Commands
    public static final char SET_FREQ_AMPL = 0x0;
    public static final char ON = 0x01;
    public static final char OFF = 0x02;

    // Wave types
    public static final char WAVE_OFF = 0x0;
    public static final char WAVE_SINUS = 0x01;
    public static final char WAVE_SQAURE1 = 0x02;
    public static final char WAVE_SQAURE2 = 0x03;
    public static final char WAVE_TRIANGLE = 0x04;

    public static final int MIN_APLITUDE = 0;
    public static final int MAX_APLITUDE = 4500;

    public static final String STRING_OFF = "OFF";
    public static final String STRING_SINE = "Sine";
    public static final String STRING_SQUARE1 = "Square1";
    public static final String STRING_SQUARE2 = "Square2";
    public static final String STRING_TRIANGLE = "Triangle";

    static HashMap<String, Integer> WaveTypesMapping = new HashMap<>();
    static {
        WaveTypesMapping.put(STRING_OFF,(int)WAVE_OFF);
        WaveTypesMapping.put(STRING_SINE,(int)WAVE_SINUS);
        //WaveTypesMapping.put(STRING_SQUARE1,(int)WAVE_SQAURE1);
        //WaveTypesMapping.put(STRING_SQUARE2,(int)WAVE_SQAURE2);
        WaveTypesMapping.put(STRING_TRIANGLE,(int)WAVE_TRIANGLE);
    }

    public static byte[] integersToBytes(int[] values)
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        for(int i=0; i < values.length; ++i)
        {
            try {
                dos.writeInt(values[i]);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return baos.toByteArray();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wave_gen);

        btnOnOff=(Button) findViewById(R.id.bt_onOff);
        btnSet=(Button) findViewById(R.id.bt_set);
        editFrequency=(EditText) findViewById(R.id.editFrequency);
        editAmplitude=(EditText) findViewById(R.id.editAplitude);

        dropdown = findViewById(R.id.selectWaveType);
        // String[] items = new String[]{STRING_SINE, STRING_TRIANGLE, STRING_SQUARE1, STRING_SQUARE2}; TODO Add proper support for SQARE vaweform
        String[] items = new String[]{STRING_SINE, STRING_TRIANGLE};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        dropdown.setAdapter(adapter);

        service_init();

        // Handle ON-OFF button click
        btnOnOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                byte[] value = {ON};
                //send data to service
                mService.writeRXCharacteristic(value);
            }
        });

        // Handle Set button click
        btnSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // 1000 Hz default
                int freq = editFrequency.getText().toString().equals("")?1000:Integer.parseInt(String.valueOf(editFrequency.getText()));
                // 100 mV default
                int ampl = editAmplitude.getText().toString().equals("")?100:Integer.parseInt(String.valueOf(editAmplitude.getText()));

                if(ampl > MAX_APLITUDE && ampl < MIN_APLITUDE) {
                    //TODO: Show error !
                    return;
                }

                String text = dropdown.getSelectedItem().toString();

                int waveType = WaveTypesMapping.get(text);

                int[] intValues = {freq, ampl, waveType};
                byte[] intValuesInByte = integersToBytes(intValues);
                byte[] payloadCommand = {SET_FREQ_AMPL};

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
                try {
                    outputStream.write( payloadCommand );
                    outputStream.write( intValuesInByte );
                } catch (IOException e) {
                    e.printStackTrace();
                }

                byte payload[] = outputStream.toByteArray( );
                //send data to service
                mService.writeRXCharacteristic(payload);
            }
        });
    }

    //UART service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((UartService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= " + mService);
            if (!mService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        public void onServiceDisconnected(ComponentName classname) {
            ////     mService.disconnect(mDevice);
            mService = null;
        }
    };

    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }

    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            final Intent mIntent = intent;
            //*********************//
            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        // Nothing todo really
                    }
                });
            }

            //*********************//
            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        //TODO: CALL main activity
                        Intent intentMain = new Intent(WaveGenActivity.this, MainActivity.class);
                        startActivity(intentMain);
                    }
                });
            }

            //*********************//
            if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
                mService.enableTXNotification();
            }
            //*********************//
            if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {

                final byte[] txValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            String text = new String(txValue, "UTF-8");
                            String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                            // TODO: Get some data from the device

                        } catch (Exception e) {
                            Log.e(TAG, e.toString());
                        }
                    }
                });
            }
        }
    };
}