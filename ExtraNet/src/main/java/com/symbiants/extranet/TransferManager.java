package com.symbiants.extranet;

/**
 * Created by ra on 15/09/13.
 */

        import java.io.BufferedInputStream;
        import java.io.BufferedOutputStream;
        import java.io.File;
        import java.io.FileOutputStream;
        import java.io.IOException;
        import java.net.InetAddress;
        import java.net.InetSocketAddress;
        import java.net.ServerSocket;
        import java.net.Socket;

        import android.app.IntentService;
        import android.content.Intent;
        import android.net.Uri;
        import android.os.Bundle;
        import android.text.format.Time;

public class TransferManager extends IntentService {
   /*
    public static final String BROADCAST_ACTION_SENT = "com.samsung.sprc.nfcmediashare.transfermanager.SENT";
    public static final String BROADCAST_ACTION_RECEIVED = "com.samsung.sprc.nfcmediashare.transfermanager.RECEIVED";
    public static final String BROADCAST_ACTION_RECEIVED_EXTRAS_FILE = "received_file";
    public static final String BROADCAST_ACTION_EXCEPTION = "com.samsung.sprc.nfcmediashare.transfermanager.EXCEPTION";
    public static final String BROADCAST_ACTION_EXCEPTION_EXTRAS_MESSAGE = "exception_message";
    public static final String INTENT_ACTION_SEND = "com.samsung.sprc.nfcmediashare.SEND";
    public static final String INTENT_ACTION_RECEIVE = "com.samsung.sprc.nfcmediashare.RECEIVE";
    */
    public static final String BROADCAST_ACTION_SENT = "com.symbiants.extrasense.transfermanager.SENT";
    public static final String BROADCAST_ACTION_RECEIVED = "com.symbiants.extrasense.transfermanager.RECEIVED";
    public static final String BROADCAST_ACTION_RECEIVED_EXTRAS_FILE = "received_file";
    public static final String BROADCAST_ACTION_EXCEPTION = "com.symbiants.extrasense.transfermanager.EXCEPTION";
    public static final String BROADCAST_ACTION_EXCEPTION_EXTRAS_MESSAGE = "exception_message";
    public static final String INTENT_ACTION_SEND = "com.symbiants.extrasense.SEND";
    public static final String INTENT_ACTION_RECEIVE = "com.symbiants.extrasense.RECEIVE";


    public static final String INTENT_EXTRAS_ADDRESS = "extras_ip";
    public static final String INTENT_EXTRAS_FILE_URI = "extras_uri";
    private static final int SOCKET_PORT = 6666;

    public TransferManager() {
        super("TransferManager");
    }

    public TransferManager(final String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        if (INTENT_ACTION_SEND.equals(intent.getAction())) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                try {
                    sendFile((Uri) extras.get(INTENT_EXTRAS_FILE_URI), (InetAddress) extras.get(INTENT_EXTRAS_ADDRESS));
                } catch (IOException e) {
                    Intent exceptionIntent = new Intent(BROADCAST_ACTION_EXCEPTION);
                    exceptionIntent.putExtra(BROADCAST_ACTION_EXCEPTION_EXTRAS_MESSAGE, e.getMessage());
                    sendBroadcast(exceptionIntent);
                }
            }
        } else if (INTENT_ACTION_RECEIVE.equals(intent.getAction())) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                try {
                    receiveFile((InetAddress) extras.get(INTENT_EXTRAS_ADDRESS));
                } catch (IOException e) {
                    Intent exceptionIntent = new Intent(BROADCAST_ACTION_EXCEPTION);
                    exceptionIntent.putExtra(BROADCAST_ACTION_EXCEPTION_EXTRAS_MESSAGE, e.getMessage());
                    sendBroadcast(exceptionIntent);
                }
            }
        }
    }

    /**
     * Initiates sending file through socket.
     *
     * @param fileUri
     *            Uri of file to be sent
     * @param address
     *            Peer address or null if sending through local socket
     * @throws IOException
     */
    private void sendFile(final Uri fileUri, final InetAddress address) throws IOException {
        BufferedInputStream input = null;
        BufferedOutputStream output = null;
        ServerSocket server = null;
        Socket client = null;
        try {
            if (address == null) {
                server = new ServerSocket(SOCKET_PORT);
                client = server.accept();
            } else {
                client = new Socket();
                client.connect(new InetSocketAddress(address, SOCKET_PORT));
            }
            input = new BufferedInputStream(getContentResolver().openInputStream(fileUri));
            output = new BufferedOutputStream(client.getOutputStream());
            int oneByte;
            while ((oneByte = input.read()) != -1) {
                output.write(oneByte);
            }
            Intent intent = new Intent(BROADCAST_ACTION_SENT);
            sendBroadcast(intent);
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } finally {
                try {
                    if (output != null) {
                        output.close();
                    }
                } finally {
                    if (server != null) {
                        server.close();
                    } else if (client != null) {
                        client.close();
                    }
                }
            }
        }
    }

    /**
     * Initiates receiving file through socket.
     *
     * @param address
     *            Peer address or null if receiving through local socket
     * @throws IOException
     */
    private void receiveFile(final InetAddress address) throws IOException {
        BufferedInputStream input = null;
        BufferedOutputStream output = null;
        ServerSocket server = null;
        Socket client = null;
        File file = getFile();
        try {
            if (address == null) {
                server = new ServerSocket(SOCKET_PORT);
                client = server.accept();
            } else {
                client = new Socket();
                client.connect(new InetSocketAddress(address, SOCKET_PORT));
            }
            input = new BufferedInputStream(client.getInputStream());
            output = new BufferedOutputStream(new FileOutputStream(file));
            int oneByte;
            while ((oneByte = input.read()) != -1) {
                output.write(oneByte);
            }
            Intent intent = new Intent(BROADCAST_ACTION_RECEIVED);
            intent.putExtra(BROADCAST_ACTION_RECEIVED_EXTRAS_FILE, file);
            sendBroadcast(intent);
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } finally {
                try {
                    if (output != null) {
                        output.close();
                    }
                } finally {
                    if (server != null) {
                        server.close();
                    } else if (client != null) {
                        client.close();
                    }
                }
            }
        }
    }

    /**
     * Generates file with name based on current time.
     *
     * @return
     *         File handler
     */
    private File getFile() {
        Time time = new Time();
        time.setToNow();
        return new File(getExternalFilesDir(null) + File.separator + time.format("%Y%m%d%H%M%S"));
    }
}
