/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.cellbroadcastreceiver.tests;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Intent;
import android.os.UserHandle;
import android.provider.Telephony;
import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;

import com.android.internal.telephony.gsm.SmsCbConstants;

/**
 * Send some test GSM CMAS warning notifications.
 */
public class SendGsmCmasMessages {

    private static final String CB_RECEIVER_PKG = "com.android.cellbroadcastreceiver";

    private static final String PRES_ALERT =
            "THE PRESIDENT HAS ISSUED AN EMERGENCY ALERT. CHECK LOCAL MEDIA FOR MORE DETAILS";

    private static final String EXTREME_ALERT = "FLASH FLOOD WARNING FOR SOUTH COCONINO COUNTY"
            + " - NORTH CENTRAL ARIZONA UNTIL 415 PM MST";

    private static final String SEVERE_ALERT = "SEVERE WEATHER WARNING FOR SOMERSET COUNTY"
            + " - NEW JERSEY UNTIL 415 PM MST";

    private static final String AMBER_ALERT =
            "AMBER ALERT:Mountain View,CA VEH'07 Blue Honda Civic CA LIC 5ABC123. "
                    + "Check https://www.amberalert.gov/active.htm for more information.";

    private static final String MONTHLY_TEST_ALERT = "This is a test of the emergency alert system."
            + " This is only a test. Call (123)456-7890.";

    private static final String PUBLIC_SAFETY_MESSAGE = "This is a public safety message.";

    private static final String STATE_LOCAL_ALERT = "This is a state/local test message.";

    private static void sendBroadcast(Activity activity, SmsCbMessage cbMessage) {
        Intent intent = new Intent(Telephony.Sms.Intents.SMS_EMERGENCY_CB_RECEIVED_ACTION);
        intent.putExtra("message", cbMessage);
        intent.setPackage(CB_RECEIVER_PKG);
        activity.sendOrderedBroadcastAsUser(intent, UserHandle.ALL,
                Manifest.permission.RECEIVE_EMERGENCY_BROADCAST,
                AppOpsManager.OP_RECEIVE_EMERGECY_SMS, null, null, Activity.RESULT_OK, null, null);
    }

    public static void testSendCmasPresAlert(Activity activity, int serialNumber) {
        SmsCbMessage cbMessage = createCmasSmsMessage(
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL, serialNumber, "en",
                PRES_ALERT, SmsCbCmasInfo.CMAS_SEVERITY_EXTREME,
                SmsCbCmasInfo.CMAS_URGENCY_EXPECTED, SmsCbCmasInfo.CMAS_CERTAINTY_LIKELY,
                SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY);

        sendBroadcast(activity, cbMessage);
    }

    public static void testSendCmasExtremeAlert(Activity activity, int serialNumber) {
        SmsCbMessage cbMessage = createCmasSmsMessage(
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED, serialNumber, "en",
                EXTREME_ALERT, SmsCbCmasInfo.CMAS_SEVERITY_EXTREME,
                SmsCbCmasInfo.CMAS_URGENCY_EXPECTED, SmsCbCmasInfo.CMAS_CERTAINTY_OBSERVED,
                SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY);

        sendBroadcast(activity, cbMessage);
    }

    public static void testSendCmasSevereAlert(Activity activity, int serialNumber) {
        SmsCbMessage cbMessage = createCmasSmsMessage(
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED, serialNumber, "en",
                SEVERE_ALERT, SmsCbCmasInfo.CMAS_SEVERITY_SEVERE,
                SmsCbCmasInfo.CMAS_URGENCY_IMMEDIATE, SmsCbCmasInfo.CMAS_CERTAINTY_LIKELY,
                SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY);

        sendBroadcast(activity, cbMessage);
    }

    public static void testSendCmasAmberAlert(Activity activity, int serialNumber) {
        SmsCbMessage cbMessage = createCmasSmsMessage(
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY, serialNumber, "en",
                AMBER_ALERT, SmsCbCmasInfo.CMAS_SEVERITY_UNKNOWN,
                SmsCbCmasInfo.CMAS_URGENCY_UNKNOWN, SmsCbCmasInfo.CMAS_CERTAINTY_UNKNOWN,
                SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY);

        sendBroadcast(activity, cbMessage);
    }

    public static void testSendCmasMonthlyTest(Activity activity, int serialNumber) {
        SmsCbMessage cbMessage = createCmasSmsMessage(
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST, serialNumber, "en",
                MONTHLY_TEST_ALERT, SmsCbCmasInfo.CMAS_SEVERITY_UNKNOWN,
                SmsCbCmasInfo.CMAS_URGENCY_UNKNOWN, SmsCbCmasInfo.CMAS_CERTAINTY_UNKNOWN,
                SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY);

        sendBroadcast(activity, cbMessage);
    }

    public static void testSendPublicSafetyMessagesAlert(Activity activity, int serialNumber) {
        SmsCbMessage cbMessage = createCmasSmsMessage(
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PUBLIC_SAFETY, serialNumber, "en",
                PUBLIC_SAFETY_MESSAGE, SmsCbCmasInfo.CMAS_SEVERITY_UNKNOWN,
                SmsCbCmasInfo.CMAS_URGENCY_UNKNOWN, SmsCbCmasInfo.CMAS_CERTAINTY_UNKNOWN,
                SmsCbMessage.MESSAGE_PRIORITY_NORMAL);

        sendBroadcast(activity, cbMessage);
    }

    public static void testSendStateLocalTestAlert(Activity activity, int serialNumber) {
        SmsCbMessage cbMessage = createCmasSmsMessage(
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_STATE_LOCAL_TEST, serialNumber, "en",
                STATE_LOCAL_ALERT, SmsCbCmasInfo.CMAS_SEVERITY_UNKNOWN,
                SmsCbCmasInfo.CMAS_URGENCY_UNKNOWN, SmsCbCmasInfo.CMAS_CERTAINTY_UNKNOWN,
                SmsCbMessage.MESSAGE_PRIORITY_NORMAL);

        sendBroadcast(activity, cbMessage);
    }

    /**
     * Create a new SmsCbMessage for testing GSM CMAS support.
     * @param serviceCategory the GSM service category
     * @param serialNumber the 16-bit message identifier
     * @param language message language code
     * @param body message body
     * @param severity CMAS severity
     * @param urgency CMAS urgency
     * @param certainty CMAS certainty
     * @return the newly created SmsMessage object
     */
    private static SmsCbMessage createCmasSmsMessage(int serviceCategory, int serialNumber,
            String language, String body, int severity, int urgency, int certainty, int priority) {
        SmsCbCmasInfo cmasInfo = new SmsCbCmasInfo(serviceCategory,
                SmsCbCmasInfo.CMAS_CATEGORY_UNKNOWN, SmsCbCmasInfo.CMAS_RESPONSE_TYPE_UNKNOWN,
                severity, urgency, certainty);
        return new SmsCbMessage(SmsCbMessage.MESSAGE_FORMAT_3GPP, 0, serialNumber,
                new SmsCbLocation("123456"), serviceCategory, language, body,
                priority, null, cmasInfo);
    }
}
