package hfad.com.btlednevtest;

import android.Manifest;
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
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
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
    private android.support.v7.app.ActionBar mActionBar;

    private final String TAG = "DevicesFragment";
    private ArrayList<String> mScannedDevices = new ArrayList<>();
    private BluetoothAdapter mBtAdapter = null;
    private BluetoothSocket btSocket = null;

    private SeekBar freqBar;
    private SeekBar dutyBar;
    private ProgressDialog btScanProgressDialog;
    private boolean btDiscoverable = false;
    private boolean phoneDevice;
    private String mArduinoAddress = "20:16:11:23:92:96";
    private boolean usePaired = true;
    private AlertDialog deviceListDialog;
    private Button btSend;
    RecyclerView freqRecycler;
    RecyclerView dutyRecycler;
    SeekBarsAdapter mAdapter;
    AppCompatActivity mActivity;
    Button send;
    // UUID service - This is the type of Bluetooth device that the BT module is
    // It is very likely yours will be the same, if not google UUID for your manufacturer
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private int minFreq = 100, maxFreq = 900, minDuty = 20, maxDuty = 95;
    private TextView freqTextLabel;
    private TextView dutyTextLabel;
    // Frequency 4 - 90 Hz default 42
    // Duty 5 - 100 microseconds default 20
    private final int MIN_FREQ = 1;
    private final int MIN_IMPULES_TIME = 1;
    private final int MAX_FREQ = 130;
    private final int MAX_IMPULSE_TIME = 130;
    private SharedPreferences mSettings;

    private final int REQUEST_ENABLE_BT = 1001;
    public static final int WRITE_PERMISSIONS_REQUEST = 1002;
    public static final int INSTALL_PACKAGES_REQUEST = 1003;
    public static final int REQUEST_COARSE_LOCATION = 1004;
    private final int REQUEST_BLUETOOTH_PRIVILEGED = 1005;
    LinearLayoutManager freqLmanager;
    LinearLayoutManager dutyLmanager;
    Parcelable freqState;
    Parcelable dutyState;
    private String FREQ_STATE = "freqState";
    private String DUTY_STATE = "dutyState";

    private boolean hasWritePermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = this;
        setContentView(R.layout.activity_btlednev_test);
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        freqTextLabel = (TextView) findViewById(R.id.freq_value);
        dutyTextLabel = (TextView) findViewById(R.id.duty_value);
        mActionBar = getSupportActionBar();

        checkPermissions();

        mSettings = getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE);

        freqTextLabel.setText("" + mSettings.getInt(FREQ_SHARED_PREF, minFreq));
        dutyTextLabel.setText("" + mSettings.getInt(DUTY_SHARED_PREF, minDuty));


        freqLmanager = new LinearLayoutManager(mActivity, LinearLayoutManager.HORIZONTAL, false);
        dutyLmanager = new LinearLayoutManager(mActivity, LinearLayoutManager.HORIZONTAL, false);

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(mActivity,
            dutyLmanager.getOrientation());

        send = (Button) findViewById(R.id.bt_send);
        if (send != null) {
            send.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    sendPwm();
                }
            });
        }
        freqRecycler = (RecyclerView) findViewById(R.id.frequency_recycler);
        freqRecycler.setLayoutManager(freqLmanager);
        freqRecycler.addItemDecoration(dividerItemDecoration);
        final String[] frequencys = new String[MAX_FREQ];
        for (int i = 0; i < MAX_FREQ; i++) {
            int freq = i + MIN_FREQ;
            frequencys[i] = "" + freq;
        }
        final String[] duties = new String[MAX_IMPULSE_TIME];
        // Fillers to recycler View
        final int FILLERS_COUNT = 2;
        for (int i = 0; i < FILLERS_COUNT; i++) {
            duties[i] = "" + 0;
        }
        for (int i = FILLERS_COUNT; i < MAX_IMPULSE_TIME; i++) {
            int duty = i - FILLERS_COUNT + MIN_IMPULES_TIME;
            duties[i] = "" + duty;
        }
        mAdapter = new SeekBarsAdapter(frequencys);
        freqRecycler.setAdapter(mAdapter);
        freqRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                int first, last, middle;
                first = freqLmanager.findFirstVisibleItemPosition();
                last = freqLmanager.findLastVisibleItemPosition();
                middle = first + (last - first) / 2;
                Log.d(TAG, "new satate = " + newState + " adapterPosition = " + frequencys[middle]);
                RecyclerView.ViewHolder holder = recyclerView.findViewHolderForLayoutPosition(middle);
                TextView text = (TextView) holder.itemView;
                freqTextLabel.setText(frequencys[middle]);
            }
        });
        SeekBarsAdapter dutyAdapter = new SeekBarsAdapter(duties);

        dutyRecycler = (RecyclerView) this.findViewById(R.id.duty_recycler);

        dutyRecycler.addItemDecoration(dividerItemDecoration);
        dutyRecycler.setLayoutManager(dutyLmanager);
        dutyRecycler.setAdapter(dutyAdapter);
        dutyRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                int first = dutyLmanager.findFirstVisibleItemPosition();
                int last = dutyLmanager.findLastVisibleItemPosition();
                int middle = first + (last - first) / 2;
                Log.d(TAG, "new satate = " + newState + " adapterPosition = " + duties[middle]);
                RecyclerView.ViewHolder holder = recyclerView.findViewHolderForLayoutPosition(middle);
                TextView text = (TextView) holder.itemView;
                dutyTextLabel.setText(duties[middle]);
            }
        });

        mBtAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBtAdapter == null) {
            Toast.makeText(mActivity, "BT is absent on this device", Toast.LENGTH_SHORT).show();
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

        // Registering broadcast receivers for all actions

        IntentFilter filterDeviceFound = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        mActivity.registerReceiver(mReceiver, filterDeviceFound);

        int screenSize = getResources().getConfiguration().screenLayout &
            Configuration.SCREENLAYOUT_SIZE_MASK;
        phoneDevice = !(screenSize == Configuration.SCREENLAYOUT_SIZE_LARGE || screenSize == Configuration.SCREENLAYOUT_SIZE_XLARGE);

        if (phoneDevice) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        freqState = savedInstanceState.getParcelable(FREQ_STATE);
        dutyState = savedInstanceState.getParcelable(DUTY_STATE);
        freqLmanager.onRestoreInstanceState(freqState);
        dutyLmanager.onRestoreInstanceState(dutyState);
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(mActivity,
            Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(mActivity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(mActivity,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    WRITE_PERMISSIONS_REQUEST);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }

        if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {

            // Request missing permissions
            ActivityCompat.requestPermissions(mActivity,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_COARSE_LOCATION);

        }

        if  (Build.VERSION.SDK_INT >= 19) {

            if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.BLUETOOTH_PRIVILEGED)
                != PackageManager.PERMISSION_GRANTED) {

                // Request missing permissions
                ActivityCompat.requestPermissions(mActivity,
                    new String[]{Manifest.permission.BLUETOOTH_PRIVILEGED}, REQUEST_BLUETOOTH_PRIVILEGED);

            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case WRITE_PERMISSIONS_REQUEST:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    hasWritePermission = true;
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
                mActivity.unregisterReceiver(this);

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
                        mActivity.getResources().getString(R.string.scanned_devices),
                        new AlertDialog.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mScannedDevices.clear();
                                mBtAdapter.startDiscovery();
                                btScanProgressDialog = new ProgressDialog(mActivity);
                                btScanProgressDialog.setTitle(mActivity.getResources().getString(R.string.scanning));
                                btScanProgressDialog.show();
                                IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
                                mActivity.registerReceiver(mBtScanEndReceiver, filter);
                                usePaired = false;
                            }
                        });
                } else {
                    deviceListDialog.setButton(AlertDialog.BUTTON_NEUTRAL,
                        mActivity.getResources().getString(R.string.paired_devices),
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
                new SendBlueToothData().execute("0");
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
        editor.putInt(FREQ_SHARED_PREF, Integer.valueOf(freqTextLabel.getText().toString()));
        editor.putInt(DUTY_SHARED_PREF, Integer.valueOf(dutyTextLabel.getText().toString()));
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
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setItems(listDevices, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mArduinoAddress = listDevices[which].substring(listDevices[which].length() - 17);

                btConnect(mBtAdapter.getRemoteDevice(mArduinoAddress));
                Toast.makeText(mActivity, listDevices[which].substring(listDevices[which].length() - 17), Toast.LENGTH_SHORT).show();
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

    /*
        =================================================
                        Seek Bar Adapter
        =================================================
     */
    class SeekBarsAdapter extends RecyclerView.Adapter<SeekBarsAdapter.ViewHolder> {
        String[] mValues;

        SeekBarsAdapter(String[] values) {
            mValues = values;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.seekbar_diskrete, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.mTextView.setText(mValues[position]);
        }

        @Override
        public int getItemCount() {
            return mValues.length;
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            TextView mTextView;

            public ViewHolder(View itemView) {
                super(itemView);
                mTextView = (TextView) itemView;
            }
        }
    }

    private void sendPwm() {
        int freq = (Integer.decode(freqTextLabel.getText().toString()) * 2);
        int fill = (Integer.decode(dutyTextLabel.getText().toString())) << 8;
        int dataToSend = freq + fill;
        Log.d(TAG, "fill = " + (fill >> 8) + " freq = " + freq);
        String message = String.valueOf(dataToSend);
        new SendBlueToothData().execute(message);
    }

    private void btConnect(BluetoothDevice device) {
        new AsyncBtConnect().execute(device);
    }


    class AsyncBtConnect extends AsyncTask<BluetoothDevice, Void, Boolean> {
        ProgressDialog progress = new ProgressDialog(mActivity);
        BluetoothDevice btDevice;


        @Override
        protected void onPreExecute() {
            progress.setTitle(mActivity.getResources().getString(R.string.scanning));
            progress.show();
        }

        @Override
        protected void onPostExecute(Boolean connected) {
            progress.dismiss();
            if (!connected) {
                Toast.makeText(mActivity, "Error connecting to device " + btDevice.getName()
                    + " " + btDevice.getAddress(), Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(mActivity, "Connection to device " + btDevice.getName()
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
                Toast.makeText(mActivity, "Device disconnected", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Log.e(TAG, "Can`t close bluetooth socket " + e.getMessage());
            }
        }
    }



    private void deviceDisconnectedShow() {
        LinearLayout noDevicesContainer = (LinearLayout) mActivity.findViewById(R.id.no_device_container);
        noDevicesContainer.setVisibility(View.VISIBLE);

        LinearLayout deviceContainer = (LinearLayout) mActivity.findViewById(R.id.device_container);
        deviceContainer.setVisibility(View.GONE);
    }

    private void deviceConnectedShow() {
        LinearLayout noDevicesContainer = (LinearLayout) mActivity.findViewById(R.id.no_device_container);
        noDevicesContainer.setVisibility(View.GONE);

        LinearLayout deviceContainer = (LinearLayout) mActivity.findViewById(R.id.device_container);
        deviceContainer.setVisibility(View.VISIBLE);
    }

    class SendBlueToothData extends AsyncTask<String, Void, Boolean> {
        String message;
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
        protected Boolean doInBackground(String... params) {
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
                    outStream.write(message.getBytes());
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
                Toast.makeText(mActivity, "Error sending data via bluetooth", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(mActivity, "Data transfer successfull", Toast.LENGTH_SHORT).show();
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
