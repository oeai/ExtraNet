package com.symbiants.extranet;


import android.app.Activity;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateNdefMessageCallback;
import android.nfc.NfcAdapter.OnNdefPushCompleteCallback;
import android.nfc.NfcEvent;

import com.symbiants.extranet.NFCMediaShare.NfcCallbacks;

/**
 * Created by ra on 15/09/13.
 */

public class NFCManager implements CreateNdefMessageCallback, OnNdefPushCompleteCallback {
  /*  private static final String MESSAGE_MIME_TYPE = "application/com.samsung.sprc.nfcmediashare"; */
    private static final String MESSAGE_MIME_TYPE = "application/com.symbiants.extrasense";
    private final Activity mActivity;
    private final NfcCallbacks mCallbacks;
    private String mMacAddress;

    public NFCManager(final Activity activity, final NfcCallbacks callbacks, final boolean isSender) {
        mActivity = activity;
        mCallbacks = callbacks;
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(activity);
        if (adapter == null || !adapter.isEnabled()) {
            mCallbacks.unavailable();
        } else if (isSender) {
            adapter.setNdefPushMessageCallback(this, mActivity);
            adapter.setOnNdefPushCompleteCallback(this, mActivity);
        }
    }

    @Override
    public void onNdefPushComplete(final NfcEvent event) {
        event.nfcAdapter.setNdefPushMessageCallback(null, mActivity);
        event.nfcAdapter.setOnNdefPushCompleteCallback(null, mActivity);
        mCallbacks.messageSent();
    }

    @Override
    public NdefMessage createNdefMessage(final NfcEvent event) {
        String mimeType = mCallbacks.getFileMimeType();
        return new NdefMessage(new NdefRecord[] {
                new NdefRecord(NdefRecord.TNF_MIME_MEDIA, MESSAGE_MIME_TYPE.getBytes(), new byte[0],
                        mMacAddress.getBytes()),
                new NdefRecord(NdefRecord.TNF_MIME_MEDIA, MESSAGE_MIME_TYPE.getBytes(), new byte[0],
                        mimeType.getBytes()) });
    }

    /**
     * Parses NDEF Message from given Intent
     */
    public void processIntent(final Intent intent) {
        NdefMessage msg = (NdefMessage) intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)[0];
        mCallbacks.messageReceived(new String(msg.getRecords()[0].getPayload()),
                new String(msg.getRecords()[1].getPayload()));
    }

    public void setMacAddress(final String mac) {
        mMacAddress = mac;
    }
}