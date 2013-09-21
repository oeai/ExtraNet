package com.symbiants.extranet;

/**
 * Created by ra on 15/09/13.

 */
        import java.io.File;
        import java.net.InetAddress;
        import java.text.DateFormat;
        import java.util.Date;

        import android.app.Activity;
        import android.app.AlertDialog;
        import android.content.BroadcastReceiver;
        import android.content.Context;
        import android.content.DialogInterface;
        import android.content.DialogInterface.OnCancelListener;
        import android.content.Intent;
        import android.content.IntentFilter;
        import android.database.Cursor;
        import android.net.Uri;
        import android.nfc.NfcAdapter;
        import android.os.Bundle;
        import android.provider.MediaStore;
        import android.provider.Settings;
        import android.view.View;
        import android.view.View.OnClickListener;
        import android.webkit.MimeTypeMap;
        import android.widget.Button;
        import android.widget.TextView;

public class NFCMediaShare extends Activity {
    private NFCManager mNFCManager;
    private WifiDirectManager mWifiDirectManager;
    private Uri mFileUri;
    private String mMimeType;
    private TextView mLogConsole;
    private Button mExitButton;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog);
        mLogConsole = (TextView) findViewById(R.id.log_console);
        mExitButton = (Button) findViewById(R.id.exit_button);
        mExitButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View view) {
                finish();
            }
        });

        boolean isSender = Intent.ACTION_SEND.equals(getIntent().getAction());
        boolean isReceiver = NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction());

        mNFCManager = new NFCManager(NFCMediaShare.this, mNfcCallbacks, isSender);
        mWifiDirectManager = new WifiDirectManager(this, mWifiDirectCallbacks);

        if (isSender) {
            appendLog("Received SEND intent.");
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                mFileUri = (Uri) extras.get(Intent.EXTRA_STREAM);
            }
        } else if (isReceiver) {
            appendLog("Received NDEF DISCOVERED intent. Processing...");
            mNFCManager.processIntent(getIntent());
        } else {
            finish();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mWifiDirectManager.registerReceiver();
        registerTransferBroadcastReceiver();
    }

    @Override
    public void onPause() {
        unregisterReceiver(mWifiDirectManager);
        unregisterReceiver(mTransferBroadcastReceiver);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mWifiDirectManager.disconnect();
        super.onDestroy();
    }

    private final NfcCallbacks mNfcCallbacks = new NfcCallbacks() {
        @Override
        public void messageSent() {
            appendLog("Message sent. Waiting for connection...");
        }

        @Override
        public void messageReceived(final String mac, final String mimeType) {
            appendLog("MAC received: " + mac + ", MIME type: " + mimeType + ". Discovering...");
            mMimeType = mimeType;
            mWifiDirectManager.discover(mac);
        }

        @Override
        public void unavailable() {
            appendLog("NFC is disabled!");
            getWirelessSettingsDialog("NFC disabled", "Enable in settings").show();
        }

        @Override
        public String getFileMimeType() {
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    MimeTypeMap.getFileExtensionFromUrl(getMediaPathFromUri(mFileUri)));
            if (mimeType != null) {
                appendLog("Recognized mime type: " + mimeType);
            } else {
                appendLog("Could not recognize file mime type!");
                mimeType = "*/*"; // receiver will be asked to choose application to open file
            }
            return mimeType;
        }

        /**
         * Gets real file path based on given media file Uri.
         *
         * @param uri
         *            Media content uri
         * @return
         *         Absolute file path
         */
        private String getMediaPathFromUri(final Uri uri) {
            String[] projection = { MediaStore.Images.Media.DATA };
            Cursor cursor = managedQuery(uri, projection, null, null, null);
            cursor.moveToFirst();
            return cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
        }
    };

    private final WifiDirectCallbacks mWifiDirectCallbacks = new WifiDirectCallbacks() {
        @Override
        public void connectFailed() {
            appendLog("Connection failed!");
            mExitButton.setVisibility(View.VISIBLE);
        }

        @Override
        public void connected(final InetAddress address, final boolean isMine) {
            if (Intent.ACTION_SEND.equals(getIntent().getAction())) {
                if (isMine) {
                    appendLog("Connected. Sending file through local socket...");
                    sendFile(null);
                } else {
                    appendLog("Connected. Sending file through socket on " + address.getHostAddress() + "...");
                    sendFile(address);
                }
            } else if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
                if (isMine) {
                    appendLog("Connected. Receiving file through local socket...");
                    receiveFile(null);
                } else {
                    appendLog("Connected. Receiving file through socket on " + address.getHostAddress() + "...");
                    receiveFile(address);
                }
            }
        }

        @Override
        public void unavailable() {
            appendLog("WiFi Direct is disabled!");
            getWirelessSettingsDialog("WiFi Direct disabled", "Enable in settings").show();
        }

        @Override
        public void discoverFinished() {
            appendLog("Discovering successfully finished. Connecting...");
            mWifiDirectManager.connect();
        }

        @Override
        public void gotMyMac() {
            if (Intent.ACTION_SEND.equals(getIntent().getAction())) {
                appendLog("Received Wifi Direct MAC: " + mWifiDirectManager.getMyAddress());
                mNFCManager.setMacAddress(mWifiDirectManager.getMyAddress());
                appendLog("Beam now!");
            }
        }
    };

    /**
     * Handles all broadcasts sent by TransferManager.
     */
    private final BroadcastReceiver mTransferBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (TransferManager.BROADCAST_ACTION_SENT.equals(intent.getAction())) {
                appendLog("File sent!");
                mExitButton.setVisibility(View.VISIBLE);
            } else if (TransferManager.BROADCAST_ACTION_RECEIVED.equals(intent.getAction())) {
                File file = (File) intent.getSerializableExtra(TransferManager.BROADCAST_ACTION_RECEIVED_EXTRAS_FILE);
                appendLog("File received! Opening...");
                Intent openIntent = new Intent();
                openIntent.setAction(Intent.ACTION_VIEW);
                openIntent.setDataAndType(Uri.fromFile(file), mMimeType);
                startActivity(openIntent);
                mExitButton.setVisibility(View.VISIBLE);
            } else if (TransferManager.BROADCAST_ACTION_EXCEPTION.equals(intent.getAction())) {
                String message = intent.getStringExtra(TransferManager.BROADCAST_ACTION_EXCEPTION_EXTRAS_MESSAGE);
                appendLog("Transfer exception: " + message);
                mExitButton.setVisibility(View.VISIBLE);
            }
        }
    };

    /**
     * Registers for receiving broadcasts sent by TransferManager.
     */
    private void registerTransferBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(TransferManager.BROADCAST_ACTION_SENT);
        filter.addAction(TransferManager.BROADCAST_ACTION_RECEIVED);
        filter.addAction(TransferManager.BROADCAST_ACTION_EXCEPTION);
        registerReceiver(mTransferBroadcastReceiver, filter);
    }

    /**
     * Creates a dialog which allows to open wireless settings window (used if any communication service is disabled).
     *
     * @param message
     *            Message text
     * @param buttonText
     *            Button text
     * @return
     *         AlertDialog to be shown
     */
    private AlertDialog getWirelessSettingsDialog(final String message, final String buttonText) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(NFCMediaShare.this);
        builder.setMessage(message).setCancelable(true)
                .setPositiveButton(buttonText, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface arg0, final int arg1) {
                        startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                        finish();
                    }
                })
                .setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(final DialogInterface arg0) {
                        finish();
                    }
                });
        return builder.create();
    }

    /**
     * Initiates receiving file.
     *
     * @param address
     *            Peer address or null if receiving through local socket
     */
    private void receiveFile(final InetAddress address) {
        Intent intent = new Intent(NFCMediaShare.this, TransferManager.class);
        intent.setAction(TransferManager.INTENT_ACTION_RECEIVE);
        intent.putExtra(TransferManager.INTENT_EXTRAS_ADDRESS, address);
        startService(intent);
    }

    /**
     * Initiates sending file.
     *
     * @param address
     *            Peer address or null if sending through local socket
     */
    private void sendFile(final InetAddress address) {
        Intent intent = new Intent(NFCMediaShare.this, TransferManager.class);
        intent.setAction(TransferManager.INTENT_ACTION_SEND);
        intent.putExtra(TransferManager.INTENT_EXTRAS_FILE_URI, mFileUri);
        intent.putExtra(TransferManager.INTENT_EXTRAS_ADDRESS, address);
        startService(intent);
    }

    /**
     * Appends given message to the log console.
     *
     * @param message
     *            Message to log
     */
    private void appendLog(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLogConsole.setText(mLogConsole.getText() + "\n"
                        + DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date()) + "\t\t" + message);
            }
        });
    }

    /**
     * Callbacks for NFCManager.
     */
    public interface NfcCallbacks {

        /**
         * Called when NFC adapter sent a message.
         */
        void messageSent();

        /**
         * Called when NFC adapter received a message.
         *
         * @param mac
         *            Received MAC address
         * @param mimeType
         *            Received MIME type
         */
        void messageReceived(String mac, String mimeType);

        /**
         * Called when NFC is not supported or disabled.
         */
        void unavailable();

        /**
         * Called in order to define the type of file being sent.
         *
         * @return
         *         File MIME type.
         */
        String getFileMimeType();
    }

    /**
     * Callbacks for WifiDirectManager.
     */
    public interface WifiDirectCallbacks {
        /**
         * Called when connection cannot be established.
         */
        void connectFailed();

        /**
         * Called when connection is successfully established.
         *
         * @param address
         *            Group owner address
         * @param isMyAddress
         *            If current device is group owner
         */
        void connected(InetAddress address, boolean isMine);

        /**
         * Called when WiFi Direct is not supported or disabled.
         */
        void unavailable();

        /**
         * Called when discover is successfully finished - peer with given MAC was found.
         */
        void discoverFinished();

        /**
         * Called when current device MAC is known.
         */
        void gotMyMac();
    }
}