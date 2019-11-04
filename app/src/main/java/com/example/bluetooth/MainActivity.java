package com.example.bluetooth;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BLE: ";

    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    BluetoothDevice bluetoothDevice;

    // Climate service UUID
    UUID CLIMATE_UUID = UUID.fromString("19B10010-E8F2-537E-4F6C-D104768A1214");

    // Temperature characteristic UUID
    UUID TEMP_CHAR_UUID = UUID.fromString("19B10012-E8F2-537E-4F6C-D104768A1214");

    // Humidity characteristic UUID
    UUID HUMIDITY_CHAR_UUID = UUID.fromString("19B10012-E8F2-537E-4F6C-D104768A1215");

    // Pressure characteristic UUID
    UUID PRESSURE_CHAR_UUID = UUID.fromString("19B10012-E8F2-537E-4F6C-D104768A1216");

    UUID[] serviceUUIDS = new UUID[]{CLIMATE_UUID};

    List<ScanFilter> filters = null;

    byte[] curTemp;
    byte[] curHumidity;
    byte[] curPressure;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check that Bluetooth is enabled -  If not, request to turn on
        if (bluetoothAdapter == null || (!bluetoothAdapter.isEnabled())) {
            Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetoothIntent, 1);
        }

        // Initialize settings for device scanning
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                .setReportDelay(0L)
                .build();

        // Initialize filters for device scanning
        if (serviceUUIDS != null) {
            filters = new ArrayList<>();
            for (UUID serviceUUID : serviceUUIDS) {
                ScanFilter filter = new ScanFilter.Builder()
                        .setServiceUuid(new ParcelUuid(serviceUUID))
                        .build();
                filters.add(filter);
            }
        }

        // If scanner is initialized, start scanning for devices and call callback if found
        if (bluetoothLeScanner != null) {
            bluetoothLeScanner.startScan(filters, scanSettings, scanCallback);
            Log.d(TAG, "Scan started.");
        } else {
            Log.e(TAG, "Could not get scanner object.");
        }
    }


    // Called after startScan runs and finds a device matching UUID from filter
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            Log.d(TAG, "onScanResult called.");

            // Create BluetoothDevice object
            bluetoothDevice = result.getDevice();
            bluetoothDevice.connectGatt(getApplicationContext(), true, bluetoothGattCallback, TRANSPORT_LE);

        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            Log.d(TAG, "onBatchScanResults called.");
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.d(TAG, "onScanFailed called.");
        }
    };


    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {

        public List<BluetoothGattCharacteristic> chars = new ArrayList<>();

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d(TAG, "onConnectionStateChange called.");

            if(status == GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // We successfully connected, proceed with service discovery
                    gatt.discoverServices();

                    final List<BluetoothGattService> services = gatt.getServices();
                    Log.i(TAG, String.format(Locale.ENGLISH,"discovered %d services for '%s'", services.size(), bluetoothAdapter.getName()));

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // We successfully disconnected on our own request
                    gatt.close();
                } else {
                    // We're CONNECTING or DISCONNECTING, ignore for now
                }
            } else {
                // An error happened...figure out what happened!

                gatt.close();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.d(TAG, "onServicesDiscovered called.");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattCharacteristic temp = gatt.getService(CLIMATE_UUID).getCharacteristic(TEMP_CHAR_UUID);
                BluetoothGattCharacteristic humidity = gatt.getService(CLIMATE_UUID).getCharacteristic(HUMIDITY_CHAR_UUID);
                BluetoothGattCharacteristic pressure = gatt.getService(CLIMATE_UUID).getCharacteristic(PRESSURE_CHAR_UUID);

                chars.add(temp);
                chars.add(humidity);
                chars.add(pressure);

                requestCharacteristics(gatt);
            }
        }

        public void requestCharacteristics(BluetoothGatt gatt) {
            gatt.readCharacteristic(chars.get(chars.size()-1));
        }


        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.d(TAG, "onCharacteristicRead called.");
            if (status == 0) {

                if (characteristic.getUuid().equals(TEMP_CHAR_UUID)) {

                    curTemp = characteristic.getValue();

                    for (byte b : curTemp) {
                        System.out.println("TEMP VALUE: " + b);
                        TextView temp = findViewById(R.id.temp_reading);
                        temp.setText(b);

                    }


                } else if (characteristic.getUuid().equals(HUMIDITY_CHAR_UUID)) {
                    curHumidity = characteristic.getValue();

                    for (byte b : curHumidity) {
                        System.out.println("HUMIDITY VALUE: " + b);
                    }
                } else if (characteristic.getUuid().equals(PRESSURE_CHAR_UUID)) {
                    curPressure = characteristic.getValue();

                    for (byte b : curPressure) {
                        System.out.println("PRESSURE VALUE: " + b);
                    }
                }


                chars.remove(chars.get(chars.size() - 1));

                if (chars.size() > 0) {
                    requestCharacteristics(gatt);
                } else {

                    gatt.disconnect();
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d(TAG, "onCharacteristicWrite called.");
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.d(TAG, "onCharacteristicChanged called.");
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            Log.d(TAG, "onDescriptorRead called.");
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            Log.d(TAG, "onDescriptorWrite called.");
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
            Log.d(TAG, "onReliableWriteCompleted called.");
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            Log.d(TAG, "onReadRemoteRssi called.");
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            Log.d(TAG, "onMtuChanged called.");
        }

        @Override
        public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
            super.onPhyUpdate(gatt, txPhy, rxPhy, status);
            Log.d(TAG, "onPhyUpdate called.");
        }

        @Override
        public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
            super.onPhyRead(gatt, txPhy, rxPhy, status);
            Log.d(TAG, "onPhyRead called.");
        }


    };

}
