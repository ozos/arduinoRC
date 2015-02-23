package ozos.arduinorc;

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;


public class MainActivity extends ActionBarActivity {
    private final BluetoothAdapter myBluetooth = BluetoothAdapter.getDefaultAdapter();
    ;
    private ArrayAdapter<String> mArrayAdapter;
    private ListView listViewPairedDevices;
    private boolean bt_connected = false;
    private ConnectThread btConnect = null;
    private AdapterView.OnItemClickListener onItemClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> arg0, View view, int position, long arg3) {
            final String info = ((TextView) view).getText().toString();
            final String address = info.substring(info.length() - 17);
            BluetoothDevice device = myBluetooth.getRemoteDevice(address);
            //position is the item position in ListView
            listViewPairedDevices.setItemChecked(position, true);

            btConnect = new ConnectThread(device);
            btConnect.run();
//            btConnect.start();


            //mArrayAdapter.clear();
        }
    };
    private Dialog dialog;
    private String connectedDeviceName;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Add the name and address to an array adapter to show in a ListView
                mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                //Device is now connected
                dialog.cancel();
                bt_connected = true;
                String connectedmsg = getString(R.string.action_btstatus_connected).concat(connectedDeviceName);
                Toast.makeText(getApplicationContext(), connectedmsg, Toast.LENGTH_SHORT).show();

            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                //Done searching
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action)) {
                //Device is about to disconnect
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                //Device has disconnected
                Toast.makeText(getApplicationContext(), R.string.action_btstatus_disconnected, Toast.LENGTH_SHORT).show();

                bt_connected = false;
            }
            supportInvalidateOptionsMenu();
        }

    };
    private ConnectedThread manageConnectedSocket = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);

        IntentFilter filter1 = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        IntentFilter filter2 = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        IntentFilter filter3 = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        this.registerReceiver(mReceiver, filter1);
        this.registerReceiver(mReceiver, filter2);
        this.registerReceiver(mReceiver, filter3);
        // Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
    }

    @Override
    protected void onDestroy() {

        // Unregister broadcast listeners
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        if (!bt_connected) {
            //Device is now disconnected
            menu.findItem(R.id.action_bluetooth).setIcon(R.drawable.ic_action_bluetooth);
            menu.findItem(R.id.action_btstatus).setTitle(R.string.action_btstatus_disconnected);
        } else {
            //Device is now connected
            menu.findItem(R.id.action_bluetooth).setIcon(R.drawable.ic_action_bluetooth_connected);
            String connectedmsg = getString(R.string.action_btstatus_connected).concat(connectedDeviceName);
            menu.findItem(R.id.action_btstatus).setTitle(connectedmsg);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_bluetooth) {
            beginBluetooth(getWindow().getDecorView().findViewById(android.R.id.content));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void beginBluetooth(View view) {
        if (bt_connected) {
            btConnect.cancel();
            return;
        }
        mArrayAdapter.clear();
        dialog = new Dialog(this);
        dialog.setContentView(R.layout.list);
        listViewPairedDevices = (ListView) dialog.findViewById(R.id.listViewPairedDevices);
        dialog.setCancelable(true);
        dialog.setTitle(R.string.devices);
        dialog.show();

        if (myBluetooth == null) {
            // Device does not support Bluetooth
            notFoundBT(getWindow().getDecorView().findViewById(android.R.id.content));
        } else {
            listPairedDevices(getWindow().getDecorView().findViewById(android.R.id.content));

            myBluetooth.startDiscovery();
            // Make sure we're not doing discovery anymore

            listViewPairedDevices.setAdapter(mArrayAdapter);
            listViewPairedDevices.setOnItemClickListener(onItemClickListener);

        }

    }

    public void listPairedDevices(View view) {
        int REQUEST_ENABLE_BT = 1;

        if (!myBluetooth.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        Set<BluetoothDevice> pairedDevices = myBluetooth.getBondedDevices();

        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                // Add the name and address to an array adapter to show in a ListView
                mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        }

    }

    public void notFoundBT(View view) {
        /** Called if the device does not support Bluetooth */

        Intent intent = new Intent(this, notBT.class);
        startActivity(intent);
    }

    public void ultrasonic(View view) {
        if (manageConnectedSocket != null) {
            manageConnectedSocket.write("C".getBytes());
            manageConnectedSocket.write("3".getBytes());
        }
    }

    public void tracking(View view) {
        if (manageConnectedSocket != null) {
            manageConnectedSocket.write("B".getBytes());
            manageConnectedSocket.write("3".getBytes());
        }
    }

    public void joystick(View view) {
        if (manageConnectedSocket != null) {
            manageConnectedSocket.write("A".getBytes());
            manageConnectedSocket.write("0".getBytes());
        }
    }

    public void stop(View view) {
        if (manageConnectedSocket != null) {
            manageConnectedSocket.write("A".getBytes());
            manageConnectedSocket.write("0".getBytes());
        }
    }

    public void forward(View view) {
        if (manageConnectedSocket != null) {

            manageConnectedSocket.write("U".getBytes());
            manageConnectedSocket.write("5".getBytes());
        }
    }


    public void back(View view) {
        if (manageConnectedSocket != null) {

            manageConnectedSocket.write("D".getBytes());
            manageConnectedSocket.write("5".getBytes());
        }
    }

    public void left(View view) {
        if (manageConnectedSocket != null) {

            manageConnectedSocket.write("L".getBytes());
            manageConnectedSocket.write("5".getBytes());
        }
    }

    public void right(View view) {
        if (manageConnectedSocket != null) {

            manageConnectedSocket.write("R".getBytes());
            manageConnectedSocket.write("5".getBytes());
        }
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            Method m;
            connectedDeviceName = device.getName();

            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                m = device.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
                tmp = (BluetoothSocket) m.invoke(device, 1);
                //  MY_UUID is the app's UUID string, also used by the server code
                //    tmp = mmDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));

            } //catch (IOException e) {}
            catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            myBluetooth.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                }
                return;
            }

            // Do work to manage the connection (in a separate thread)
            manageConnectedSocket = new ConnectedThread(mmSocket);
            manageConnectedSocket.start();

        }

        /**
         * Will cancel an in-progress connection, and close the socket
         */
        public void cancel() {
            try {
                mmSocket.close();
                bt_connected = false;
            } catch (IOException e) {
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            OutputStream tmpOut = null;
            InputStream tmpIn = null;
            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            mmOutStream = tmpOut;
            mmInStream = tmpIn;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                } catch (IOException e) {
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }
}


