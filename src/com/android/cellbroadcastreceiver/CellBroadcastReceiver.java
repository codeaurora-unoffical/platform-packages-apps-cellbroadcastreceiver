/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (c) 2012-2013, The Linux Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.cellbroadcastreceiver;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.telephony.CellBroadcastMessage;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaSmsCbProgramData;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.MSimConstants;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.telephony.msim.ITelephonyMSim;
import com.android.internal.telephony.MSimConstants;

public class CellBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "CellBroadcastReceiver";
    static final boolean DBG = true;    // STOPSHIP: change to false before ship

    private static final String GET_LATEST_CB_AREA_INFO_ACTION =
            "android.cellbroadcastreceiver.GET_LATEST_CB_AREA_INFO";
    private int mSubscription = MSimConstants.DEFAULT_SUBSCRIPTION;

    @Override
    public void onReceive(Context context, Intent intent) {
        onReceiveWithPrivilege(context, intent, false);
    }

    protected void onReceiveWithPrivilege(Context context, Intent intent, boolean privileged) {
        if (DBG) log("onReceive " + intent);

        String action = intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            if (DBG) log("Registering for ServiceState updates");
            TelephonyManager tm = (TelephonyManager) context.getSystemService(
                    Context.TELEPHONY_SERVICE);
            tm.listen(new ServiceStateListener(context.getApplicationContext()),
                    PhoneStateListener.LISTEN_SERVICE_STATE);
        } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
            boolean airplaneModeOn = intent.getBooleanExtra("state", false);
            if (DBG) log("airplaneModeOn: " + airplaneModeOn);
            if (!airplaneModeOn) {
                for (int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++){
                    startConfigService(context, i);
                }
            }
        } else if (Telephony.Sms.Intents.SMS_EMERGENCY_CB_RECEIVED_ACTION.equals(action) ||
                Telephony.Sms.Intents.SMS_CB_RECEIVED_ACTION.equals(action)) {
            // If 'privileged' is false, it means that the intent was delivered to the base
            // no-permissions receiver class.  If we get an SMS_CB_RECEIVED message that way, it
            // means someone has tried to spoof the message by delivering it outside the normal
            // permission-checked route, so we just ignore it.
            if (privileged) {
                intent.setClass(context, CellBroadcastAlertService.class);
                context.startService(intent);
            } else {
                Log.e(TAG, "ignoring unprivileged action received " + action);
            }
        } else if (Telephony.Sms.Intents.SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED_ACTION
                .equals(action)) {
            if (privileged) {
                mSubscription = intent.getIntExtra(MSimConstants.SUBSCRIPTION_KEY,
                    MSimConstants.SUB1);
                Log.d(TAG, "onReceive SMS_CATEGORY_PROGRAM_DATA mSubscription :" + mSubscription);
                CdmaSmsCbProgramData[] programDataList = (CdmaSmsCbProgramData[])
                        intent.getParcelableArrayExtra("program_data_list");
                if (programDataList != null) {
                    handleCdmaSmsCbProgramData(context, programDataList);
                } else {
                    Log.e(TAG, "SCPD intent received with no program_data_list");
                }
            } else {
                Log.e(TAG, "ignoring unprivileged action received " + action);
            }
        } else if (GET_LATEST_CB_AREA_INFO_ACTION.equals(action)) {
            if (privileged) {
                CellBroadcastMessage message = CellBroadcastReceiverApp.getLatestAreaInfo();
                if (message != null) {
                    Intent areaInfoIntent = new Intent(
                            CellBroadcastAlertService.CB_AREA_INFO_RECEIVED_ACTION);
                    areaInfoIntent.putExtra("message", message);
                    context.sendBroadcastAsUser(areaInfoIntent, UserHandle.ALL,
                            android.Manifest.permission.READ_PHONE_STATE);
                }
            } else {
                Log.e(TAG, "caller missing READ_PHONE_STATE permission, returning");
            }
        } else {
            Log.w(TAG, "onReceive() unexpected action " + action);
        }
    }

    /**
     * Handle Service Category Program Data message.
     * TODO: Send Service Category Program Results response message to sender
     *
     * @param context
     * @param programDataList
     */
    private void handleCdmaSmsCbProgramData(Context context,
            CdmaSmsCbProgramData[] programDataList) {
        for (CdmaSmsCbProgramData programData : programDataList) {
            switch (programData.getOperation()) {
                case CdmaSmsCbProgramData.OPERATION_ADD_CATEGORY:
                    tryCdmaSetCategory(context, programData.getCategory(), true);
                    break;

                case CdmaSmsCbProgramData.OPERATION_DELETE_CATEGORY:
                    tryCdmaSetCategory(context, programData.getCategory(), false);
                    break;

                case CdmaSmsCbProgramData.OPERATION_CLEAR_CATEGORIES:
                    tryCdmaSetCategory(context,
                            SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT, false);
                    tryCdmaSetCategory(context,
                            SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT, false);
                    tryCdmaSetCategory(context,
                            SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY, false);
                    tryCdmaSetCategory(context,
                            SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE, false);
                    break;

                default:
                    Log.e(TAG, "Ignoring unknown SCPD operation " + programData.getOperation());
            }
        }
    }

    private void tryCdmaSetCategory(Context context, int category, boolean enable) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        String key = null;

        switch (category) {
            case SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT:
                key = CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS + mSubscription;
                break;

            case SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT:
                key = CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS + mSubscription;
                break;

            case SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY:
                key = CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS + mSubscription;
                break;

            case SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE:
                key = CellBroadcastSettings.KEY_ENABLE_CMAS_TEST_ALERTS + mSubscription;
                break;

            default:
                Log.w(TAG, "Ignoring SCPD command to " + (enable ? "enable" : "disable")
                        + " alerts in category " + category);
        }
        if (null != key) sharedPrefs.edit().putBoolean(key, enable).apply();
    }

    private static boolean isNormalStatus(int sub){
        //we take this sim status as normal for cell broadcast
        int subStatus  = MSimTelephonyManager.getDefault().getSimState(sub);
        if (subStatus == TelephonyManager.SIM_STATE_PIN_REQUIRED ||
            subStatus == TelephonyManager.SIM_STATE_PUK_REQUIRED ||
            subStatus == TelephonyManager.SIM_STATE_NETWORK_LOCKED ||
            subStatus == TelephonyManager.SIM_STATE_READY) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Tell {@link CellBroadcastConfigService} to enable the CB channels.
     * @param context the broadcast receiver context
     */
    static void startConfigService(Context context,int subscription) {
        if (!isNormalStatus(subscription))
            return;
        String action = CellBroadcastConfigService.ACTION_ENABLE_CHANNELS_GSM;
        Log.d(TAG, "startConfigService subscription = " + subscription);
        if (phoneIsCdma(subscription)) {
            action = CellBroadcastConfigService.ACTION_ENABLE_CHANNELS_CDMA;
        }
        Intent serviceIntent = new Intent(action, null,
                context, CellBroadcastConfigService.class);
        serviceIntent.putExtra(MSimConstants.SUBSCRIPTION_KEY, subscription);
        context.startService(serviceIntent);
    }

    /**
     * @return true if the phone is a CDMA phone type
     */

    static boolean phoneIsCdma() {
        boolean isCdma = false;
        try {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
            if (phone != null) {
                isCdma = (phone.getActivePhoneType() == TelephonyManager.PHONE_TYPE_CDMA);
            }
        } catch (RemoteException e) {
            Log.w(TAG, "phone.getActivePhoneType() failed", e);
        }
        return isCdma;
    }


    /**
     * @return true if the phone is a CDMA phone type in DSDS
     */
    static boolean phoneIsCdma(int subscription) {
        boolean isCdma = false;
        try {
            ITelephonyMSim phoneMsim = ITelephonyMSim.Stub.asInterface(
                    ServiceManager.checkService("phone_msim"));
            if (phoneMsim != null) {
                isCdma = (phoneMsim.getActivePhoneType(subscription) ==
                        TelephonyManager.PHONE_TYPE_CDMA);
            }
        } catch (RemoteException e) {
            Log.w(TAG, "phoneMsim.getActivePhoneType() failed", e);
        }
        return isCdma;
    }

    private class ServiceStateListener extends PhoneStateListener {
        private final Context mContext;
        private int mServiceState = -1;

        ServiceStateListener(Context context) {
            mContext = context;
        }

        @Override
        public void onServiceStateChanged(ServiceState ss) {
            int newState = ss.getState();
            if (newState != mServiceState) {
                Log.d(TAG, "Service state changed! " + newState + " Full: " + ss);
                mServiceState = newState;
                if (newState == ServiceState.STATE_IN_SERVICE ||
                        newState == ServiceState.STATE_EMERGENCY_ONLY) {
                    for(int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++){
                        startConfigService(mContext, i);
                    }
                }
            }
        }
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
