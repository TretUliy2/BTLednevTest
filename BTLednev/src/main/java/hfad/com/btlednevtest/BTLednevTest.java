package hfad.com.btlednevtest;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputEditText;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

public class BTLednevTest extends AppCompatActivity {
    private static final String BT_SOCKET = "BlueToothSocket";
    private static final String FREQ_SHARED_PREF = "freqSharedPref";
    private static final String DUTY_SHARED_PREF = "dutySharedPref";
    private static final String APP_PREFS_NAME = "AppPreferences";

    private Activity mActivity;
    private final String TAG = "DevicesFragment";
    private ArrayList<String> mScannedDevices = new ArrayList<>();
    private BluetoothAdapter mBtAdapter = null;
    private BluetoothSocket btSocket = null;

    private ProgressDialog btScanProgressDialog;
    private boolean btDiscoverable = false;
    private String mArduinoAddress = "20:16:11:23:92:96";
    private boolean usePaired = true;
    private AlertDialog deviceListDialog;
    private Button btSend;
    private  Button send;
    // UUID service - This is the type of Bluetooth device that the BT module is
    // It is very likely yours will be the same, if not google UUID for your manufacturer
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private int minFreq = 100, maxFreq = 900, minDuty = 20, maxDuty = 95;
    // Frequency 4 - 90 Hz default 42
    // Duty 5 - 100 microseconds default 20
    private SharedPreferences mSettings;
    private TextInputEditText mFreqPicker, mDutyPicker, mVoltagePicker;

    private Button freqPlusBtn, dutyPlusBtn, voltagePlusBtn;
    private Button freqMinusBtn, dutyMinusBtn, voltageMinusBtn;

    private boolean autoIncrement = false, autoDecrement = false;
    private final int REQUEST_ENABLE_BT = 1001;
    public static final int WRITE_PERMISSIONS_REQUEST = 1002;
    public static final int INSTALL_PACKAGES_REQUEST = 1003;
    public static final int REQUEST_COARSE_LOCATION = 1004;
    private final int REQUEST_BLUETOOTH_PRIVILEGED = 1005;
    private String FREQ_STATE = "freqState";
    private String DUTY_STATE = "dutyState";
    private String VOLTAGE_STATE = "voltageState";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_btlednev_test);
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        //freqTextLabel = (TextView) findViewById(R.id.freq_value);
        //dutyTextLabel = (TextView) findViewById(R.id.duty_value);
        mActivity = this;

        mVoltagePicker = (TextInputEditText) findViewById(R.id.voltage_edit_text);
        mFreqPicker = (TextInputEditText) findViewById(R.id.freq_edit_text);
        mDutyPicker = (TextInputEditText) findViewById(R.id.duty_edit_text);

        mVoltagePicker.setText("0");
        mFreqPicker.setText("0");
        mDutyPicker.setText("0");

        mVoltagePicker.clearFocus();
        mFreqPicker.clearFocus();
        mDutyPicker.clearFocus();

        freqPlusBtn = (Button) findViewById(R.id.freq_plus);
        dutyPlusBtn = (Button) findViewById(R.id.duty_plus);
        voltagePlusBtn = (Button) findViewById(R.id.voltage_plus);

        freqMinusBtn = (Button) findViewById(R.id.freq_minus);
        dutyMinusBtn = (Button) findViewById(R.id.duty_minus);
        voltageMinusBtn = (Button) findViewById(R.id.voltage_minus);

        buttonsHandling();

        checkPermissions();

        mSettings = getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE);


        send = (Button) findViewById(R.id.bt_send);
        if (send != null) {
            send.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    sendPwm();
                }
            });
        }
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(this, "BT is absent on this device", Toast.LENGTH_SHORT).show();
        } else {
            if (!mBtAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
        if (Build.VERSION.SDK_INT >= 19) {
            IntentFilter filter_new = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
            registerReceiver(mBtPairingRequestReceiver, filter_new);
        }

        IntentFilter filterDeviceFound = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filterDeviceFound);

        int screenSize = getResources().getConfiguration().screenLayout &
            Configuration.SCREENLAYOUT_SIZE_MASK;

        boolean phoneDevice = !(screenSize == Configuration.SCREENLAYOUT_SIZE_LARGE || screenSize == Configuration.SCREENLAYOUT_SIZE_XLARGE);

        if (phoneDevice) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private Handler repetitiveUpdateHandler = new Handler();



    private class RepetitiveRun implements Runnable {
        private String what = "freq"; // Default increment frequency
        RepetitiveRun(String what) {
            this.what = what;
        }

        @Override
        public void run() {
            if (autoIncrement) {
                increment(what);
                repetitiveUpdateHandler.postDelayed(new RepetitiveRun(what), 50);
            } else if (autoDecrement) {
                decrement(what);
                repetitiveUpdateHandler.postDelayed(new RepetitiveRun(what), 50);
            }
        }
    }

    private void increment(String what) {
        int result;

        switch ( what ) {
            case "freq":
                result = Integer.valueOf(mFreqPicker.getText().toString()) + 1;
                if (result > 255) { result = 0;}
                mFreqPicker.setText(String.valueOf(result));
                break;
            case "duty":
                result = Integer.valueOf(mDutyPicker.getText().toString()) + 1;
                if (result > 255) { result = 0;}
                mDutyPicker.setText(String.valueOf(result));
                break;
            case "voltage":
                result = Integer.valueOf(mVoltagePicker.getText().toString()) + 1;
                if (result > 255) { result = 0;}
                mVoltagePicker.setText(String.valueOf(result));
                break;
        }
    }

    private void decrement(String what) {
        int result;

        switch ( what ) {
            case "freq":
                result = Integer.valueOf(mFreqPicker.getText().toString()) - 1;
                if (result < 0 )  { result = 255; }
                mFreqPicker.setText(String.valueOf(result));
                break;
            case "duty":
                result = Integer.valueOf(mDutyPicker.getText().toString()) - 1;
                if (result < 0 )  { result = 255; }
                mDutyPicker.setText(String.valueOf(result));
                break;
            case "voltage":
                result = Integer.valueOf(mVoltagePicker.getText().toString()) - 1;
                if (result < 0 )  { result = 255; }
                mVoltagePicker.setText(String.valueOf(result));
                break;
        }

    }

    private void buttonsHandling () {
        final String VOLTAGE = "voltage";
        final String FREQ = "freq";
        final String DUTY = "duty";

        freqPlusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                increment(FREQ);
            }
        });

        freqPlusBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                autoIncrement = true;
                repetitiveUpdateHandler.post(new RepetitiveRun(FREQ));
                return false;
            }
        });

        freqPlusBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP && autoIncrement) {
                    autoIncrement = false;
                }
                return false;
            }
        });


        dutyPlusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               mDutyPicker.setText(String.valueOf(Integer.valueOf(mDutyPicker.getText().toString()) + 1));
            }
        });
        voltagePlusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               mVoltagePicker.setText(String.valueOf(Integer.valueOf(mVoltagePicker.getText().toString()) + 1));
            }
        });

        freqMinusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                decrement("freq");
            }
        });

        freqMinusBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                autoDecrement = true;
                repetitiveUpdateHandler.post(new RepetitiveRun("freq"));
                return false;
            }
        });
        freqMinusBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP && autoDecrement) {
                    autoDecrement = false;
                }
                return false;
            }
        });

        dutyMinusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               decrement("duty");
            }
        });

        voltageMinusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            decrement("voltage");
            }
        });



    }
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    WRITE_PERMISSIONS_REQUEST);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {

            // Request missing permissions
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_COARSE_LOCATION);

        }

        if  (Build.VERSION.SDK_INT >= 19) {

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_PRIVILEGED)
                != PackageManager.PERMISSION_GRANTED) {

                // Request missing permissions
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_PRIVILEGED}, REQUEST_BLUETOOTH_PRIVILEGED);

            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case WRITE_PERMISSIONS_REQUEST:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                }

            case INSTALL_PACKAGES_REQUEST:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "INSTALL PERMISSION GRANTED");
                }

            case REQUEST_COARSE_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Received prmission for bluetooth");
                }

            case REQUEST_BLUETOOTH_PRIVILEGED:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Received permission for REQUEST_BLUETOOTH_PRIVILEGED");
                }
        }
    }



    private final BroadcastReceiver mBtPairingRequestReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Received Broadcast request for pairing");
            String action = intent.getAction();
            if (action.equals(BluetoothDevice.ACTION_PAIRING_REQUEST)) {
                if (Build.VERSION.SDK_INT >= 19) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    int pin = intent.getIntExtra("android.bluetooth.device.extra.PAIRING_KEY", 1234);
                    byte[] pinBytes;
                    pinBytes = ("" + pin).getBytes();

                    device.setPin(pinBytes);
                    device.setPairingConfirmation(true);
                }
            } else {
                Log.d(TAG, "Current device not support autopairing upgrade youre firmware");
            }
        }
    };

    private final BroadcastReceiver mBtScanEndReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                //
                btScanProgressDialog.dismiss();
                Log.d(TAG, "Received end of bluetooth scan procedure");
                getApplicationContext().unregisterReceiver(this);

                deviceListDialog = getDeviceDialog();
                deviceListDialog.show();
            }
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.devices_menu, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.bt_menu_paired_devices:
                Log.i(TAG, "Get Paired devices show dialog");
/*                android.app.FragmentManager manager = mActivity.getFragmentManager();
                DeviceScanDialog dialog = new DeviceScanDialog();
                dialog.show(manager, "dialog_fragment");*/
                deviceListDialog = getDeviceDialog();
                if (usePaired) {
                    deviceListDialog.setButton(AlertDialog.BUTTON_NEUTRAL,
                        getString(R.string.scanned_devices),
                        new AlertDialog.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mScannedDevices.clear();
                                mBtAdapter.startDiscovery();
                                btScanProgressDialog = new ProgressDialog(getApplicationContext());
                                btScanProgressDialog.setTitle(getString(R.string.scanning));
                                btScanProgressDialog.show();
                                IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
                                getApplicationContext().registerReceiver(mBtScanEndReceiver, filter);
                                usePaired = false;
                            }
                        });
                } else {
                    deviceListDialog.setButton(AlertDialog.BUTTON_NEUTRAL,
                        getString(R.string.paired_devices),
                        new AlertDialog.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mBtAdapter.cancelDiscovery();
                                usePaired = true;
                                showPairedDialog();
                            }
                        });

                }
                deviceListDialog.show();
                break;
            case R.id.bt_menu_send_zero:
                byte[] msg = new byte[1];
                msg[0] = 0;
                new SendBlueToothData().execute(msg);
                break;
            case R.id.bt_menu_disconnect:
                btDisconnect(mBtAdapter.getRemoteDevice(mArduinoAddress));
                deviceDisconnectedShow();
                break;
            default:
                break;
        }
        return false;
    }

    private void showPairedDialog() {
        deviceListDialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SharedPreferences.Editor editor = mSettings.edit();
        mBtAdapter.cancelDiscovery();
        try {
            btSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to close btSocket ", e);
        }
        editor.putInt(FREQ_SHARED_PREF, Integer.valueOf(mFreqPicker.getText().toString()));
        editor.putInt(DUTY_SHARED_PREF, Integer.valueOf(mDutyPicker.getText().toString()));
        editor.apply();
    }

    private AlertDialog getDeviceDialog() {
        final String[] listDevices;
        int title;
        if (usePaired) {
            title = R.string.paired_devices;
            listDevices = new String[mBtAdapter.getBondedDevices().size()];
            int i = 0;
            for (BluetoothDevice device : mBtAdapter.getBondedDevices()) {
                listDevices[i] = (device.getName() + "\n" + device.getAddress());
                i++;
            }
        } else {
            title = R.string.scanned_devices;
            listDevices = new String[mScannedDevices.size()];
            int i = 0;
            for (String buf : mScannedDevices) {
                listDevices[i] = buf;
                i++;
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setItems(listDevices, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mArduinoAddress = listDevices[which].substring(listDevices[which].length() - 17);

                btConnect(mBtAdapter.getRemoteDevice(mArduinoAddress));
                Toast.makeText(getApplicationContext(),
                        listDevices[which].substring(listDevices[which].length() - 17),
                        Toast.LENGTH_SHORT).show();
            }
        });
        builder.setTitle(title);

        if (!usePaired) {
            builder.setNeutralButton("Scan Devices", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            });
        } else {
            builder.setNeutralButton("Paired Devices", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            });
        }
        return builder.create();
    }


    private void sendPwm() {
        byte[] message_byte = new byte[3];
        message_byte[0] = (byte) Byte.valueOf(mFreqPicker.getText().toString());
        message_byte[1] = (byte) Byte.valueOf(mDutyPicker.getText().toString());
        message_byte[2] = (byte) Byte.valueOf(mVoltagePicker.getText().toString());
        for (int i = 0; i < message_byte.length; i++) {
            Log.d(TAG, "Byte["+ i + "] to send = 0x" + String.format("%02x", message_byte[i]));
        }
        new SendBlueToothData().execute(message_byte);
    }

    private void btConnect(BluetoothDevice device) {
        new AsyncBtConnect().execute(device);
    }


    private class AsyncBtConnect extends AsyncTask<BluetoothDevice, Void, Boolean> {
        ProgressDialog progress = new ProgressDialog(mActivity);
        BluetoothDevice btDevice;


        @Override
        protected void onPreExecute() {
            progress.setTitle(getString(R.string.scanning));
            progress.show();
        }

        @Override
        protected void onPostExecute(Boolean connected) {
            progress.dismiss();
            if (!connected) {
                Toast.makeText(getApplicationContext(), "Error connecting to device " + btDevice.getName()
                    + " " + btDevice.getAddress(), Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(getApplicationContext(), "Connection to device " + btDevice.getName()
                + " " + btDevice.getAddress() + " success", Toast.LENGTH_SHORT).show();

            deviceConnectedShow();
        }


        @Override
        protected Boolean doInBackground(BluetoothDevice... params) {
            BluetoothDevice device = params[0];
            btDevice = device;
            if (btSocket == null || !btSocket.isConnected()) {
                try {
                    Log.d(TAG, "createRfconnSocket to " + device.getAddress());
                    btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                } catch (IOException e) {
                    Log.e(TAG, "createRfcommFailed  " + Log.getStackTraceString(e));
                    return false;
                }

                try {
                    mBtAdapter.cancelDiscovery();
                    Log.d(TAG, "Trying to connect to " + device.getAddress());
                    btSocket.connect();
                } catch (IOException e) {
                    Log.e(TAG, "btSocket.connect() failed  " + Log.getStackTraceString(e));
                    return false;
                }
            }
            return true;
        }

    }

    private void btDisconnect(BluetoothDevice device) {
        if (btSocket != null && btSocket.isConnected()) {
            try {
                Log.d(TAG, "Disconnecting from " + device.getAddress());
                btSocket.close();
                Toast.makeText(getApplicationContext(), "Device disconnected", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Log.e(TAG, "Can`t close bluetooth socket " + e.getMessage());
            }
        }
    }



    private void deviceDisconnectedShow() {
        LinearLayout noDevicesContainer = (LinearLayout) findViewById(R.id.no_device_container);
        noDevicesContainer.setVisibility(View.VISIBLE);

        LinearLayout deviceContainer = (LinearLayout) findViewById(R.id.device_container);
        deviceContainer.setVisibility(View.GONE);
    }

    private void deviceConnectedShow() {
        LinearLayout noDevicesContainer = (LinearLayout) findViewById(R.id.no_device_container);
        noDevicesContainer.setVisibility(View.GONE);

        LinearLayout deviceContainer = (LinearLayout) findViewById(R.id.device_container);
        deviceContainer.setVisibility(View.VISIBLE);
    }

    private class SendBlueToothData extends AsyncTask<byte[], Void, Boolean> {
        byte[] message;
        BluetoothDevice device = null;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (mArduinoAddress == null) {
                mArduinoAddress = "20:16:11:23:92:96";
            }
            Log.d(TAG, "SendBlueToothData starting() ");
            device = mBtAdapter.getRemoteDevice(mArduinoAddress);
        }

        @Override
        protected Boolean doInBackground(byte[]... params) {
            message = params[0];
            Log.d(TAG, "Sending " + message + " via bluetooth");
            OutputStream outStream = null;
            try {
                outStream = btSocket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "OutputStream Failed " + Log.getStackTraceString(e));
                return false;
            } catch (NullPointerException e) {
                Log.e(TAG, "btSocket is null object WTF " + e.getMessage());
                return false;
            }
            try {
                if (outStream != null) {
                    outStream.write(message);
                }
            } catch (IOException e) {
                Log.e(TAG, "write failed " + Log.getStackTraceString(e));
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (!success) {
                Toast.makeText(getApplicationContext(), "Error sending data via bluetooth", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "Data transfer successfull", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceMac = device.getAddress();
                mScannedDevices.add(deviceName + "\n" + deviceMac);
                Log.d(TAG, "Received message about bluetooth device " + deviceMac);
            }
        }
    };
}
