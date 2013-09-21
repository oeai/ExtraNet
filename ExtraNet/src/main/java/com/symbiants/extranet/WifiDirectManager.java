package com.symbiants.extranet;

/**
 * Created by ra on 15/09/13.
 */

        import android.content.BroadcastReceiver;
        import android.content.Context;
        import android.content.Intent;
        import android.content.IntentFilter;
        import android.net.NetworkInfo;
        import android.net.wifi.WpsInfo;
        import android.net.wifi.p2p.WifiP2pConfig;
        import android.net.wifi.p2p.WifiP2pDevice;
        import android.net.wifi.p2p.WifiP2pDeviceList;
        import android.net.wifi.p2p.WifiP2pInfo;
        import android.net.wifi.p2p.WifiP2pManager;
        import android.net.wifi.p2p.WifiP2pManager.ActionListener;
        import android.net.wifi.p2p.WifiP2pManager.Channel;
        import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
        import android.net.wifi.p2p.WifiP2pManager.PeerListListener;

        import com.symbiants.extranet.NFCMediaShare.WifiDirectCallbacks;

public class WifiDirectManager extends BroadcastReceiver {
    private final Context mContext;
    private final WifiP2pManager mWifiManager;
    private final WifiDirectCallbacks mCallbacks;
    private final Channel mChannel;
    private String mMyAddress;
    private String mAddressToConnect;
    private boolean mMyAddressIsKnown = false;
    private boolean mDiscovered = false;
    private boolean mConnected = false;

    public WifiDirectManager(final Context context, final WifiDirectCallbacks callbacks) {
        mContext = context;
        mCallbacks = callbacks;
        mWifiManager = (WifiP2pManager) mContext.getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mWifiManager.initialize(context, context.getMainLooper(), null);
    }

    /**
     * Registers for receiving broadcasts sent by WifiP2pManager.
     */
    public void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        mContext.registerReceiver(this, filter);
    }

    /**
     * Establishes connection.
     */
    public void connect() {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = mAddressToConnect;
        config.wps.setup = WpsInfo.PBC;
        mWifiManager.connect(mChannel, config, new ActionListener() {
            @Override
            public void onFailure(final int reason) {
                mCallbacks.connectFailed();
            }

            @Override
            public void onSuccess() {
                // We will handle this event in onReceive() method (WIFI_P2P_CONNECTION_CHANGED_ACTION)
            }
        });
    }

    /**
     * Initiates peer discovery.
     *
     * @param address
     *            MAC address of peer to connect
     */
    public void discover(final String address) {
        mAddressToConnect = address;
        mWifiManager.discoverPeers(mChannel, new ActionListener() {
            @Override
            public void onFailure(final int reason) {
            }

            @Override
            public void onSuccess() {
            }
        });
    }

    /**
     * Checks if discovery found a peer with desired MAC address.
     */
    private final PeerListListener mPeerListListener = new PeerListListener() {
        @Override
        public void onPeersAvailable(final WifiP2pDeviceList peers) {
            if (!mDiscovered) {
                for (WifiP2pDevice device : peers.getDeviceList()) {
                    if (device.deviceAddress.equals(mAddressToConnect)) {
                        mDiscovered = true;
                        mCallbacks.discoverFinished();
                    }
                }
            }
        }
    };

    public String getMyAddress() {
        return mMyAddress;
    }

    /**
     * Closes connection.
     */
    public void disconnect() {
        mWifiManager.removeGroup(mChannel, new ActionListener() {
            @Override
            public void onFailure(final int reason) {
            }

            @Override
            public void onSuccess() {
            }
        });
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(intent.getAction())) {
            if (((NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)).isConnected()) {
                mWifiManager.requestConnectionInfo(mChannel, new ConnectionInfoListener() {
                    @Override
                    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
                        if (!mConnected && info.groupFormed) {
                            mConnected = true;
                            mCallbacks.connected(info.groupOwnerAddress, info.isGroupOwner);
                        }
                    }
                });
            }
        } else if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(intent.getAction())) {
            if (intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1) == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
                mCallbacks.unavailable();
            }
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(intent.getAction())) {
            mWifiManager.requestPeers(mChannel, mPeerListListener);
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(intent.getAction())) {
            mMyAddress = ((WifiP2pDevice) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)).deviceAddress;
            if (!mMyAddressIsKnown) {
                mMyAddressIsKnown = true;
                mCallbacks.gotMyMac();
            }
        }
    }
}
