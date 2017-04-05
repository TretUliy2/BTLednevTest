package hfad.com.btlednevtest;

import android.app.ActionBar;
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
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
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

public class BTLednevTest extends Activity {
    private static final String BT_SOCKET = "BlueToothSocket";
    private ActionBar mActionBar;
    private final int REQUEST_ENABLE_BT = 1001;
    private final String TAG = "DevicesFragment";
    private static final int REQUEST_COARSE_LOCATION = 1121;
    private ArrayList<String> mScannedDevices = new ArrayList<>();
    private BluetoothAdapter mBtAdapter = null;
    private BluetoothSocket btSocket = null;

    private SeekBar freqBar;
    private SeekBar dutyBar;
    private ProgressDialog btScanProgressDialog;
    private boolean btDiscoverable = false;
    private String mArduinoAddress = "20:16:11:23:92:96";
    private boolean usePaired = true;
    private AlertDialog deviceListDialog;
    private Button btSend;
    RecyclerView freqRecycler;
    RecyclerView dutyRecycler;
    SeekBarsAdapter mAdapter;
    Activity mActivity;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = this;
        setContentView(R.layout.activity_btlednev_test);
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        freqTextLabel = (TextView) findViewById(R.id.freq_value);
        dutyTextLabel = (TextView) findViewById(R.id.duty_value);

        final String freqBaseText = freqTextLabel.getText().toString();
        final String dutyBaseText = dutyTextLabel.getText().toString();

        freqTextLabel.setText("" + minFreq);
        dutyTextLabel.setText("" + minDuty);


        final LinearLayoutManager lManager = new LinearLayoutManager(mActivity, LinearLayoutManager.HORIZONTAL, false);
        final LinearLayoutManager dutylManager = new LinearLayoutManager(mActivity, LinearLayoutManager.HORIZONTAL, false);

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(mActivity,
            dutylManager.getOrientation());

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
        freqRecycler.setLayoutManager(lManager);
        freqRecycler.addItemDecoration(dividerItemDecoration);
        final String[] frequencys = new String[MAX_FREQ];
        for (int i = 0; i < MAX_FREQ; i++) {
            int freq = i + MIN_FREQ;
            frequencys[i] = "" + freq;
        }
        final String[] duties = new String[MAX_IMPULSE_TIME];
        // Fillers to recycler View
        for (int i = 0; i < 3; i++) {
            duties[i] = "" + 0;
        }
        for (int i = 3; i < MAX_IMPULSE_TIME; i++) {
            int duty = i-3 + MIN_IMPULES_TIME;
            duties[i] = "" + duty;
        }
        mAdapter = new SeekBarsAdapter(frequencys);
        freqRecycler.setAdapter(mAdapter);
        freqRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                int first, last, middle;
                first = lManager.findFirstVisibleItemPosition();
                last = lManager.findLastVisibleItemPosition();
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
        dutyRecycler.setLayoutManager(dutylManager);
        dutyRecycler.setAdapter(dutyAdapter);
        dutyRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                int first = dutylManager.findFirstVisibleItemPosition();
                int last = dutylManager.findLastVisibleItemPosition();
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