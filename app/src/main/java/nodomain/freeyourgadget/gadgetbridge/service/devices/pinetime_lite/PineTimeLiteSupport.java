/*  Copyright (C) 2016-2021 Andreas Shimokawa, Carsten Pfeiffer, JF, Sebastian
    Kranz, Taavi Eomäe

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.pinetime_lite;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.UUID;

import no.nordicsemi.android.dfu.DfuLogListener;
import no.nordicsemi.android.dfu.DfuProgressListener;
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter;
import no.nordicsemi.android.dfu.DfuServiceController;
import no.nordicsemi.android.dfu.DfuServiceInitiator;
import no.nordicsemi.android.dfu.DfuServiceListenerHelper;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventCallControl;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo;
import nodomain.freeyourgadget.gadgetbridge.devices.pinetime_lite.PineTimeLiteDFUService;
import nodomain.freeyourgadget.gadgetbridge.devices.pinetime_lite.PineTimeLiteConstants;
import nodomain.freeyourgadget.gadgetbridge.devices.pinetime_lite.PineTimeLiteInstallHandler;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.Alarm;
import nodomain.freeyourgadget.gadgetbridge.model.CalendarEventSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CannedMessagesSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicStateSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.model.Weather;
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btle.BLETypeConversions;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattCharacteristic;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.actions.SetDeviceStateAction;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.IntentListener;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.alertnotification.AlertNotificationProfile;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.battery.BatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.battery.BatteryInfoProfile;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfoProfile;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

//
// Pinetime Lite - joaquimorg - 12-2020
//

public class PineTimeLiteSupport extends AbstractBTLEDeviceSupport implements DfuLogListener {
    private static final Logger LOG = LoggerFactory.getLogger(PineTimeLiteSupport.class);
    private final GBDeviceEventVersionInfo versionCmd = new GBDeviceEventVersionInfo();
    private final int MaxNotificationLength = 100;
    private int firmwareVersionMajor = 0;
    private int firmwareVersionMinor = 0;
    private int firmwareVersionPatch = 0;

    private boolean callIncoming = false;

    private BluetoothGattCharacteristic writeCharacteristic = null;

    private final DeviceInfoProfile<PineTimeLiteSupport> deviceInfoProfile;
    private final BatteryInfoProfile<PineTimeLiteSupport> batteryInfoProfile;

    public PineTimeLiteSupport() {
        super(LOG);

        addSupportedService(PineTimeLiteConstants.UUID_SERVICE_MSG_NOTIFICATION);
        addSupportedService(PineTimeLiteConstants.UUID_CHARACTERISTIC_MSG_NOTIFICATION_EVENT);

        addSupportedService(GattService.UUID_SERVICE_CURRENT_TIME);
        addSupportedService(GattService.UUID_SERVICE_DEVICE_INFORMATION);
        addSupportedService(GattService.UUID_SERVICE_BATTERY_SERVICE);
        //addSupportedService(GattService.UUID_SERVICE_IMMEDIATE_ALERT);
        //addSupportedService(GattService.UUID_SERVICE_ALERT_NOTIFICATION);

        IntentListener mListenerInfo = new IntentListener() {
            @Override
            public void notify(Intent intent) {
                String action = intent.getAction();
                if (DeviceInfoProfile.ACTION_DEVICE_INFO.equals(action)) {
                    handleDeviceInfo((nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfo) intent.getParcelableExtra(DeviceInfoProfile.EXTRA_DEVICE_INFO));
                }
            }
        };

        deviceInfoProfile = new DeviceInfoProfile<>(this);
        deviceInfoProfile.addListener(mListenerInfo);
        addSupportedProfile(deviceInfoProfile);

        AlertNotificationProfile<PineTimeLiteSupport> alertNotificationProfile = new AlertNotificationProfile<>(this);
        addSupportedProfile(alertNotificationProfile);

        IntentListener mListenerBatt = new IntentListener() {
            @Override
            public void notify(Intent intent) {
                String action = intent.getAction();
                if (BatteryInfoProfile.ACTION_BATTERY_INFO.equals(action)) {
                    BatteryInfo info = intent.getParcelableExtra(BatteryInfoProfile.EXTRA_BATTERY_INFO);
                    GBDeviceEventBatteryInfo gbInfo = new GBDeviceEventBatteryInfo();
                    gbInfo.level = (short) info.getPercentCharged();
                    handleGBDeviceEvent(gbInfo);
                }
            }
        };

        batteryInfoProfile = new BatteryInfoProfile<>(this);
        batteryInfoProfile.addListener(mListenerBatt);
        addSupportedProfile(batteryInfoProfile);

    }

    @Override
    protected TransactionBuilder initializeDevice(TransactionBuilder builder) {

        writeCharacteristic = getCharacteristic(PineTimeLiteConstants.UUID_CHARACTERISTIC_MSG_NOTIFICATION);

        builder.add(new SetDeviceStateAction(getDevice(), GBDevice.State.INITIALIZING, getContext()));
        requestDeviceInfo(builder);
        onSetTime();

        BluetoothGattCharacteristic alertNotificationEventCharacteristic = getCharacteristic(PineTimeLiteConstants.UUID_CHARACTERISTIC_MSG_NOTIFICATION_EVENT);
        if (alertNotificationEventCharacteristic != null) {
            builder.notify(alertNotificationEventCharacteristic, true);
        }
        setInitialized(builder);

        batteryInfoProfile.requestBatteryInfo(builder);
        return builder;
    }

    /**
     * These are used to keep track when long strings haven't changed,
     * thus avoiding unnecessary transfers that are (potentially) very slow.
     * <p>
     * Makes the device's UI more responsive.
     */
    String lastAlbum;
    String lastTrack;
    String lastArtist;
    PineTimeLiteInstallHandler handler;
    DfuServiceController controller;

    private final DfuProgressListener progressListener = new DfuProgressListenerAdapter() {
        private final LocalBroadcastManager manager = LocalBroadcastManager.getInstance(getContext());

        /**
         * Sets the progress bar to indeterminate or not, also makes it visible
         *
         * @param indeterminate if indeterminate
         */
        public void setIndeterminate(boolean indeterminate) {
            manager.sendBroadcast(new Intent(GB.ACTION_SET_PROGRESS_BAR).putExtra(GB.PROGRESS_BAR_INDETERMINATE, indeterminate));
        }

        /**
         * Sets the status text and logs it
         */
        public void setProgress(int progress) {
            manager.sendBroadcast(new Intent(GB.ACTION_SET_PROGRESS_BAR).putExtra(GB.PROGRESS_BAR_PROGRESS, progress));
        }

        /**
         * Sets the text that describes progress
         *
         * @param progressText text to display
         */
        public void setProgressText(String progressText) {
            manager.sendBroadcast(new Intent(GB.ACTION_SET_PROGRESS_TEXT).putExtra(GB.DISPLAY_MESSAGE_MESSAGE, progressText));
        }

        @Override
        public void onDeviceConnecting(final String mac) {
            this.setIndeterminate(true);
            this.setProgressText(getContext().getString(R.string.devicestatus_connecting));
        }

        @Override
        public void onDeviceConnected(final String mac) {
            this.setIndeterminate(true);
            this.setProgressText(getContext().getString(R.string.devicestatus_connected));
        }

        @Override
        public void onEnablingDfuMode(final String mac) {
            this.setIndeterminate(true);
            this.setProgressText(getContext().getString(R.string.devicestatus_upload_starting));
        }

        @Override
        public void onDfuProcessStarting(final String mac) {
            this.setIndeterminate(true);
            this.setProgressText(getContext().getString(R.string.devicestatus_upload_starting));
        }

        @Override
        public void onDfuProcessStarted(final String mac) {
            this.setIndeterminate(true);
            this.setProgressText(getContext().getString(R.string.devicestatus_upload_started));
        }

        @Override
        public void onDeviceDisconnecting(final String mac) {
            this.setProgressText(getContext().getString(R.string.devicestatus_disconnecting));
        }

        @Override
        public void onDeviceDisconnected(final String mac) {
            this.setIndeterminate(true);
            this.setProgressText(getContext().getString(R.string.devicestatus_disconnected));
        }

        @Override
        public void onDfuCompleted(final String mac) {
            this.setProgressText(getContext().getString(R.string.devicestatus_upload_completed));
            this.setIndeterminate(false);
            this.setProgress(100);

            handler = null;
            controller = null;
            DfuServiceListenerHelper.unregisterProgressListener(getContext(), progressListener);
            gbDevice.unsetBusyTask();
            // TODO: Request reconnection
        }

        @Override
        public void onFirmwareValidating(final String mac) {
            this.setIndeterminate(true);
            this.setProgressText(getContext().getString(R.string.devicestatus_upload_validating));
        }

        @Override
        public void onDfuAborted(final String mac) {
            this.setProgressText(getContext().getString(R.string.devicestatus_upload_aborted));
            gbDevice.unsetBusyTask();
        }

        @Override
        public void onError(final String mac, int error, int errorType, final String message) {
            this.setProgressText(getContext().getString(R.string.devicestatus_upload_failed));
            gbDevice.unsetBusyTask();
        }

        @Override
        public void onProgressChanged(final String mac,
                                      int percent,
                                      float speed,
                                      float averageSpeed,
                                      int segment,
                                      int totalSegments) {
            this.setProgress(percent);
            this.setIndeterminate(false);
            this.setProgressText(String.format(Locale.ENGLISH,
                    getContext().getString(R.string.firmware_update_progress),
                    percent, speed, averageSpeed, segment, totalSegments));
        }
    };

    @Override
    public boolean useAutoConnect() {
        return false;
    }

    private final int maxMsgLength = 255;

    private void sendMsgToWatch(TransactionBuilder builder, byte[] msg) {

        if (msg.length > maxMsgLength) {
            int msgpartlength = 0;
            byte[] msgpart = null;

            do {
                if ((msg.length - msgpartlength) < maxMsgLength) {
                    msgpart = new byte[msg.length - msgpartlength];
                    System.arraycopy(msg, msgpartlength, msgpart, 0, msg.length - msgpartlength);
                    msgpartlength += (msg.length - msgpartlength);
                } else {
                    msgpart = new byte[maxMsgLength];
                    System.arraycopy(msg, msgpartlength, msgpart, 0, maxMsgLength);
                    msgpartlength += maxMsgLength;
                }
                builder.write(writeCharacteristic, msgpart);
            } while (msgpartlength < msg.length);
            builder.write(writeCharacteristic, new byte[]{0x00});
        } else {
            builder.write(writeCharacteristic, msg);
        }

    }

    @Override
    public void onNotification(NotificationSpec notificationSpec) {

        int subject_length = 0;
        int body_length = notificationSpec.body.getBytes(StandardCharsets.UTF_8).length;
        if (body_length > 100) {
            body_length = 100;
        }
        int notification_length = body_length;
        byte[] subject = null;
        byte[] notification;
        Calendar time = GregorianCalendar.getInstance();
        // convert every single digit of the date to ascii characters
        // we do it like so: use the base chrachter of '0' and add the digit
        byte[] datetimeBytes = new byte[]{
                (byte) ((time.get(Calendar.YEAR) / 1000) + '0'),
                (byte) (((time.get(Calendar.YEAR) / 100) % 10) + '0'),
                (byte) (((time.get(Calendar.YEAR) / 10) % 10) + '0'),
                (byte) ((time.get(Calendar.YEAR) % 10) + '0'),
                (byte) (((time.get(Calendar.MONTH) + 1) / 10) + '0'),
                (byte) (((time.get(Calendar.MONTH) + 1) % 10) + '0'),
                (byte) ((time.get(Calendar.DAY_OF_MONTH) / 10) + '0'),
                (byte) ((time.get(Calendar.DAY_OF_MONTH) % 10) + '0'),
                (byte) 'T',
                (byte) ((time.get(Calendar.HOUR_OF_DAY) / 10) + '0'),
                (byte) ((time.get(Calendar.HOUR_OF_DAY) % 10) + '0'),
                (byte) ((time.get(Calendar.MINUTE) / 10) + '0'),
                (byte) ((time.get(Calendar.MINUTE) % 10) + '0'),
                (byte) ((time.get(Calendar.SECOND) / 10) + '0'),
                (byte) ((time.get(Calendar.SECOND) % 10) + '0'),
        };

        if (notificationSpec.sender != null) {
            notification_length += notificationSpec.sender.getBytes(StandardCharsets.UTF_8).length;
            subject_length = notificationSpec.sender.getBytes(StandardCharsets.UTF_8).length;
            subject = new byte[subject_length];
            System.arraycopy(notificationSpec.sender.getBytes(StandardCharsets.UTF_8), 0, subject, 0, subject_length);
        } else if (notificationSpec.phoneNumber != null) {
            notification_length += notificationSpec.phoneNumber.getBytes(StandardCharsets.UTF_8).length;
            subject_length = notificationSpec.phoneNumber.getBytes(StandardCharsets.UTF_8).length;
            subject = new byte[subject_length];
            System.arraycopy(notificationSpec.phoneNumber.getBytes(StandardCharsets.UTF_8), 0, subject, 0, subject_length);
        } else if (notificationSpec.subject != null) {
            notification_length += notificationSpec.subject.getBytes(StandardCharsets.UTF_8).length;
            subject_length = notificationSpec.subject.getBytes(StandardCharsets.UTF_8).length;
            subject = new byte[subject_length];
            System.arraycopy(notificationSpec.subject.getBytes(StandardCharsets.UTF_8), 0, subject, 0, subject_length);
        } else if (notificationSpec.title != null) {
            notification_length += notificationSpec.title.getBytes(StandardCharsets.UTF_8).length;
            subject_length = notificationSpec.title.getBytes(StandardCharsets.UTF_8).length;
            subject = new byte[subject_length];
            System.arraycopy(notificationSpec.title.getBytes(StandardCharsets.UTF_8), 0, subject, 0, subject_length);
        }


        notification_length += datetimeBytes.length + 10; // add message overhead
        notification = new byte[notification_length];
        notification[0] = 0x01;
        notification[1] = (byte) notificationSpec.getId();
        notification[2] = 0x02;
        notification[3] = (byte) ((notification_length - 6) & 0xff);
        notification[4] = (byte) ((notification_length - 6) >> 8);
        notification[6] = 0x03;
        notification[7] = (byte) subject_length;
        notification[8] = (byte) body_length;
        System.arraycopy(subject, 0, notification, 9, subject_length);
        System.arraycopy(notificationSpec.body.getBytes(StandardCharsets.UTF_8), 0, notification, 9 + subject_length, body_length);
        System.arraycopy(datetimeBytes, 0, notification, 9 + subject_length + body_length, datetimeBytes.length);
        notification[notification_length - 1] = 0x0f;

        switch (notificationSpec.type) {
            case GENERIC_SMS:
                notification[5] = PineTimeLiteConstants.NOTIFICATION_SMS;
                break;
            case GENERIC_PHONE:
                notification[5] = PineTimeLiteConstants.NOTIFICATION_MISSED_CALL;
                break;
            case GMAIL:
            case GOOGLE_INBOX:
            case MAILBOX:
            case OUTLOOK:
            case YAHOO_MAIL:
            case GENERIC_EMAIL:
                notification[5] = PineTimeLiteConstants.NOTIFICATION_EMAIL;
                break;
            case WECHAT:
                notification[5] = PineTimeLiteConstants.NOTIFICATION_WECHAT;
                break;
            case VIBER:
                notification[5] = PineTimeLiteConstants.NOTIFICATION_VIBER;
                break;
            case WHATSAPP:
                notification[5] = PineTimeLiteConstants.NOTIFICATION_WHATSAPP;
                break;
            case FACEBOOK:
            case FACEBOOK_MESSENGER:
                notification[5] = PineTimeLiteConstants.NOTIFICATION_FACEBOOK;
                break;
            case LINE:
                notification[5] = PineTimeLiteConstants.NOTIFICATION_LINE;
                break;
            case SKYPE:
                notification[5] = PineTimeLiteConstants.NOTIFICATION_SKYPE;
                break;
            case GOOGLE_HANGOUTS:
            case CONVERSATIONS:
            case RIOT:
            case SIGNAL:
            case WIRE:
            case TELEGRAM:
            case THREEMA:
            case KONTALK:
            case ANTOX:
            case GOOGLE_MESSENGER:
            case HIPCHAT:
            case KIK:
            case KAKAO_TALK:
            case SLACK:
                notification[5] = PineTimeLiteConstants.NOTIFICATION_MESSENGER;
                break;
            case SNAPCHAT:
                notification[5] = PineTimeLiteConstants.NOTIFICATION_SNAPCHAT;
                break;
            case INSTAGRAM:
                notification[5] = PineTimeLiteConstants.NOTIFICATION_INSTAGRAM;
                break;
            case TWITTER:
                notification[5] = PineTimeLiteConstants.NOTIFICATION_TWITTER;
                break;
            case LINKEDIN:
                notification[5] = PineTimeLiteConstants.NOTIFICATION_LINKEDIN;
                break;
            case GENERIC_CALENDAR:
                notification[5] = PineTimeLiteConstants.NOTIFICATION_CALENDAR;
                break;
            default:
                notification[5] = PineTimeLiteConstants.NOTIFICATION_SOCIAL;
                break;
        }

        try {
            TransactionBuilder builder = performInitialized("notification");
            sendMsgToWatch(builder, notification);
            builder.queue(getQueue());
        } catch (IOException e) {
            GB.toast(getContext(), "Error sending notification: " + e.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);
        }

    }

    @Override
    public void onDeleteNotification(int id) {

    }

    @Override
    public void onSetTime() {
        // Since this is a standard we should generalize this in Gadgetbridge (properly)
        GregorianCalendar now = BLETypeConversions.createCalendar();
        byte[] bytes = BLETypeConversions.calendarToRawBytes(now);
        byte[] tail = new byte[]{0, BLETypeConversions.mapTimeZone(now.getTimeZone(), BLETypeConversions.TZ_FLAG_INCLUDE_DST_IN_TZ)};
        byte[] all = BLETypeConversions.join(bytes, tail);

        TransactionBuilder builder = new TransactionBuilder("set time");
        builder.write(getCharacteristic(GattCharacteristic.UUID_CHARACTERISTIC_CURRENT_TIME), all);
        builder.queue(getQueue());
    }

    @Override
    public void onSetAlarms(ArrayList<? extends Alarm> alarms) {

    }

    @Override
    public void onSetCallState(CallSpec callSpec) {

        int name_length = 0;
        int number_length = 0;
        int notification_length = 0;
        byte[] name = null;
        byte[] number = null;
        byte[] notification;

        Calendar time = GregorianCalendar.getInstance();
        // convert every single digit of the date to ascii characters
        // we do it like so: use the base chrachter of '0' and add the digit
        byte[] datetimeBytes = new byte[]{
                (byte) ((time.get(Calendar.YEAR) / 1000) + '0'),
                (byte) (((time.get(Calendar.YEAR) / 100) % 10) + '0'),
                (byte) (((time.get(Calendar.YEAR) / 10) % 10) + '0'),
                (byte) ((time.get(Calendar.YEAR) % 10) + '0'),
                (byte) (((time.get(Calendar.MONTH) + 1) / 10) + '0'),
                (byte) (((time.get(Calendar.MONTH) + 1) % 10) + '0'),
                (byte) ((time.get(Calendar.DAY_OF_MONTH) / 10) + '0'),
                (byte) ((time.get(Calendar.DAY_OF_MONTH) % 10) + '0'),
                (byte) 'T',
                (byte) ((time.get(Calendar.HOUR_OF_DAY) / 10) + '0'),
                (byte) ((time.get(Calendar.HOUR_OF_DAY) % 10) + '0'),
                (byte) ((time.get(Calendar.MINUTE) / 10) + '0'),
                (byte) ((time.get(Calendar.MINUTE) % 10) + '0'),
                (byte) ((time.get(Calendar.SECOND) / 10) + '0'),
                (byte) ((time.get(Calendar.SECOND) % 10) + '0'),
        };

        if (callIncoming || (callSpec.command == CallSpec.CALL_INCOMING)) {

            if (callSpec.command == CallSpec.CALL_INCOMING) {
                if (callSpec.name != null) {
                    notification_length += callSpec.name.getBytes(StandardCharsets.UTF_8).length;
                    name_length = callSpec.name.getBytes(StandardCharsets.UTF_8).length;
                    name = new byte[name_length];
                    System.arraycopy(callSpec.name.getBytes(StandardCharsets.UTF_8), 0, name, 0, name_length);
                }
                if (callSpec.number != null) {
                    notification_length += callSpec.number.getBytes(StandardCharsets.UTF_8).length;
                    number_length = callSpec.number.getBytes(StandardCharsets.UTF_8).length;
                    number = new byte[number_length];
                    System.arraycopy(callSpec.number.getBytes(StandardCharsets.UTF_8), 0, number, 0, number_length);
                }
                notification_length += datetimeBytes.length + 10; // add message overhead

                notification = new byte[notification_length];
                notification[0] = 0x01;
                notification[1] = 0x0;
                notification[2] = 0x02;
                notification[3] = (byte) ((notification_length - 6) & 0xff);
                notification[4] = (byte) ((notification_length - 6) >> 8);
                notification[5] = PineTimeLiteConstants.NOTIFICATION_INCOME_CALL;
                notification[6] = 0x03;
                notification[7] = (byte) number_length;
                notification[8] = (byte) name_length;
                System.arraycopy(number, 0, notification, 9, number_length);
                System.arraycopy(name, 0, notification, 9 + number_length, name_length );
                System.arraycopy(datetimeBytes, 0, notification, 9 + number_length + name_length, datetimeBytes.length);
                notification[notification_length - 1] = 0x0f;
                callIncoming = true;
            } else {

                notification_length = datetimeBytes.length + 10; // add message overhead
                notification = new byte[notification_length];
                notification[0] = 0x01;
                notification[1] = 0x0;
                notification[2] = 0x02;
                notification[3] = (byte) ((notification_length - 6) & 0xff);
                notification[4] = (byte) ((notification_length - 6) >> 8);
                notification[5] = PineTimeLiteConstants.NOTIFICATION_CALL_OFF;
                notification[6] = 0x03;
                notification[7] = (byte) 0;
                notification[8] = (byte) 0;
                System.arraycopy(datetimeBytes, 0, notification, 9, datetimeBytes.length);
                notification[notification_length - 1] = 0x0f;
                callIncoming = false;
            }
            try {
                TransactionBuilder builder = performInitialized("incomingcall");
                sendMsgToWatch(builder, notification);
                builder.queue(getQueue());
            } catch (IOException e) {
                GB.toast(getContext(), "Error sending notification: " + e.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);
            }
        }
    }

    @Override
    public void onSetCannedMessages(CannedMessagesSpec cannedMessagesSpec) {

    }

    @Override
    public void onEnableRealtimeSteps(boolean enable) {

    }

    @Override
    public void onInstallApp(Uri uri) {
        try {
            handler = new PineTimeLiteInstallHandler(uri, getContext());

            // TODO: Check validity more closely
            if (true) {
                DfuServiceInitiator starter = new DfuServiceInitiator(getDevice().getAddress())
                        .setDeviceName(getDevice().getName())
                        .setKeepBond(true)
                        .setForeground(false)
                        .setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(false)
                        .setMtu(517)
                        .setZip(uri);

                controller = starter.start(getContext(), PineTimeLiteDFUService.class);
                DfuServiceListenerHelper.registerProgressListener(getContext(), progressListener);
                DfuServiceListenerHelper.registerLogListener(getContext(), this);

                LocalBroadcastManager.getInstance(getContext()).sendBroadcast(new Intent(GB.ACTION_SET_PROGRESS_BAR)
                        .putExtra(GB.PROGRESS_BAR_INDETERMINATE, true)
                );
                LocalBroadcastManager.getInstance(getContext()).sendBroadcast(new Intent(GB.ACTION_SET_PROGRESS_TEXT)
                        .putExtra(GB.DISPLAY_MESSAGE_MESSAGE, getContext().getString(R.string.devicestatus_upload_starting))
                );
                gbDevice.setBusyTask("firmware upgrade");
            } else {
                // TODO: Handle invalid firmware files
            }
        } catch (Exception ex) {
            GB.toast(getContext(), getContext().getString(R.string.updatefirmwareoperation_write_failed) + ":" + ex.getMessage(), Toast.LENGTH_LONG, GB.ERROR, ex);
            if (gbDevice.isBusy()) {
                gbDevice.unsetBusyTask();
            }
        }
    }

    @Override
    public void onAppInfoReq() {

    }

    @Override
    public void onAppStart(UUID uuid, boolean start) {

    }

    @Override
    public void onAppDelete(UUID uuid) {

    }

    @Override
    public void onAppConfiguration(UUID appUuid, String config, Integer id) {

    }

    @Override
    public void onAppReorder(UUID[] uuids) {

    }

    @Override
    public void onFetchRecordedData(int dataTypes) {

    }

    @Override
    public void onReset(int flags) {

    }

    @Override
    public void onHeartRateTest() {

    }

    @Override
    public void onEnableRealtimeHeartRateMeasurement(boolean enable) {

    }

    @Override
    public void onFindDevice(boolean start) {
        TransactionBuilder builder = new TransactionBuilder("Enable alert");
        builder.write(getCharacteristic(GattCharacteristic.UUID_CHARACTERISTIC_ALERT_LEVEL), new byte[]{(byte) (start ? 0x01 : 0x00)});
        builder.queue(getQueue());
    }

    @Override
    public void onSetConstantVibration(int intensity) {

    }

    @Override
    public void onScreenshotReq() {

    }

    @Override
    public void onEnableHeartRateSleepSupport(boolean enable) {

    }

    @Override
    public void onSetHeartRateMeasurementInterval(int seconds) {

    }

    @Override
    public void onAddCalendarEvent(CalendarEventSpec calendarEventSpec) {

    }

    @Override
    public void onDeleteCalendarEvent(byte type, long id) {

    }

    @Override
    public void onSetMusicInfo(MusicSpec musicSpec) {
    }

    @Override
    public void onSetMusicState(MusicStateSpec stateSpec) {
    }

    @Override
    public boolean onCharacteristicRead(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic, int status) {
        if (super.onCharacteristicRead(gatt, characteristic, status)) {
            return true;
        }
        UUID characteristicUUID = characteristic.getUuid();

        LOG.info("Unhandled characteristic read: " + characteristicUUID);
        return false;
    }

    @Override
    public void onSendConfiguration(String config) {
        try {
            TransactionBuilder builder = performInitialized("sendConfiguration");
            switch (config) {
                /*case PineTimeLiteConstants.PREF_SCREENTIME:
                    setScreenTime(builder);
                    break;
                case PineTimeLiteConstants.PREF_ANALOG_MODE:
                    setAnalogMode(builder);
                    break;
                case PineTimeLiteConstants.PREF_DO_NOT_DISTURB:
                    setDoNotDisturb(builder);
                    break;*/
                case DeviceSettingsPreferenceConst.PREF_TIMEFORMAT:
                    setTimeFormate(builder);
                    break;
                /*case PineTimeLiteConstants.PREF_SHOCK_STRENGTH:
                    setShockStrength(builder);
                case PineTimeLiteConstants.PREF_USER_STEP_GOAL:
                    setUserGoals(builder);
                    break;*/
            }
            builder.queue(getQueue());
        } catch (IOException e) {
            GB.toast(getContext(), "Error sending configuration: " + e.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);
        }
    }

    private void setTimeFormate(TransactionBuilder builder) {
        GBPrefs gbPrefs = new GBPrefs(new Prefs(GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress())));

        String timeFormat = gbPrefs.getTimeFormat();
        int type = 1;
        if ("am/pm".equals(timeFormat)) {
            type = 2;
        }

        byte[] timeformat = {
                (byte) 0x01,
                (byte) 0x01, // CMD_TIME_SURFACE_SETTINGS
                (byte) 0x01,  //CMD_SEND
                (byte) 0x8,
                (byte) 0x0,
                (byte) 0xff, // set to ff to not change anything on the watch
                (byte) type,
                (byte) 0xff, // set to ff to not change anything on the watch
                (byte) 0xff, // set to ff to not change anything on the watch
                (byte) 0xff, // set to ff to not change anything on the watch
                (byte) 0xff, // set to ff to not change anything on the watch
                (byte) 0xff, // set to ff to not change anything on the watch
                (byte) 0xff, // set to ff to not change anything on the watch
                (byte) 0x0f // End
        };

        sendMsgToWatch(builder, timeformat);
    }

    @Override
    public void onReadConfiguration(String config) {

    }

    @Override
    public void onTestNewFunction() {

    }

    @Override
    public boolean onCharacteristicChanged(BluetoothGatt gatt,
                                           BluetoothGattCharacteristic characteristic) {
        if (super.onCharacteristicChanged(gatt, characteristic)) {
            return true;
        }

        UUID characteristicUUID = characteristic.getUuid();

        if (characteristicUUID.equals(GattService.UUID_SERVICE_BATTERY_SERVICE)) {
            byte[] value = characteristic.getValue();
            Context context = getContext();
            GB.updateBatteryNotification(context.getString(R.string.notif_battery_low_percent, gbDevice.getName(), String.valueOf(value)),
                   ""
                    , context);
        } else if (characteristicUUID.equals(PineTimeLiteConstants.UUID_CHARACTERISTIC_MSG_NOTIFICATION_EVENT)) {
            byte[] value = characteristic.getValue();
            GBDeviceEventCallControl deviceEventCallControl = new GBDeviceEventCallControl();
            switch (value[0]) {
                case 0:
                    deviceEventCallControl.event = GBDeviceEventCallControl.Event.REJECT;
                    break;
                case 1:
                    deviceEventCallControl.event = GBDeviceEventCallControl.Event.ACCEPT;
                    break;
                case 2:
                    deviceEventCallControl.event = GBDeviceEventCallControl.Event.IGNORE;
                    break;
                default:
                    return false;
            }
            evaluateGBDeviceEvent(deviceEventCallControl);
            return true;
        }

        LOG.info("Unhandled characteristic changed: " + characteristicUUID);
        return false;
    }

    @Override
    public void onSendWeather(WeatherSpec weatherSpec) {
        byte[] weather = new byte[weatherSpec.location.getBytes(StandardCharsets.UTF_8).length + 26]; // 26 bytes for weatherdata and overhead
        weather[0] = (byte) 0x01;
        weather[1] = (byte) 0x00; //CMD_PUSH_WEATHER_DATA;
        weather[2] = (byte) 0x01; //CMD_SEND;
        weather[3] = (byte) ((weatherSpec.location.getBytes(StandardCharsets.UTF_8).length + 20) & 0xff);
        weather[4] = (byte) ((weatherSpec.location.getBytes(StandardCharsets.UTF_8).length + 20) >> 8);
        weather[5] = 0; // celsius
        weather[6] = (byte) (weatherSpec.currentTemp - 273);
        weather[7] = (byte) (weatherSpec.todayMinTemp - 273);
        weather[8] = (byte) (weatherSpec.todayMaxTemp - 273);

        weather[9] = Weather.mapToZeTimeCondition(weatherSpec.currentConditionCode);

        for (int forecast = 0; forecast < 3; forecast++) {
            weather[10 + (forecast * 5)] = 0; // celsius
            weather[11 + (forecast * 5)] = (byte) 0xff;
            weather[12 + (forecast * 5)] = (byte) (weatherSpec.forecasts.get(forecast).minTemp - 273);
            weather[13 + (forecast * 5)] = (byte) (weatherSpec.forecasts.get(forecast).maxTemp - 273);

            weather[14 + (forecast * 5)] = Weather.mapToZeTimeCondition(weatherSpec.forecasts.get(forecast).conditionCode);

        }
        System.arraycopy(weatherSpec.location.getBytes(StandardCharsets.UTF_8), 0, weather, 25, weatherSpec.location.getBytes(StandardCharsets.UTF_8).length);
        weather[weather.length - 1] = (byte) 0x0f;
        try {
            TransactionBuilder builder = performInitialized("sendWeahter");
            sendMsgToWatch(builder, weather);
            builder.queue(getQueue());
        } catch (IOException e) {
            GB.toast(getContext(), "Error sending weather: " + e.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);
        }
    }

    /**
     * Helper function that just converts an integer into a byte array
     */
    private static byte[] intToBytes(int source) {
        return ByteBuffer.allocate(4).putInt(source).array();
    }

    /**
     * This will check if the characteristic exists and can be written
     * <p>
     * Keeps backwards compatibility with firmware that can't take all the information
     */
    private void safeWriteToCharacteristic(TransactionBuilder builder, UUID uuid, byte[] data) {
        BluetoothGattCharacteristic characteristic = getCharacteristic(uuid);
        if (characteristic != null &&
                (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
            builder.write(characteristic, data);
        }
    }

    private void setInitialized(TransactionBuilder builder) {
        builder.add(new SetDeviceStateAction(getDevice(), GBDevice.State.INITIALIZED, getContext()));
    }

    private void requestDeviceInfo(TransactionBuilder builder) {
        LOG.debug("Requesting Device Info!");
        deviceInfoProfile.requestDeviceInfo(builder);
    }

    private void handleDeviceInfo(nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfo info) {
        LOG.warn("Device info: " + info);
        versionCmd.hwVersion = info.getHardwareRevision();
        versionCmd.fwVersion = info.getFirmwareRevision();

        if(versionCmd.fwVersion != null && !versionCmd.fwVersion.isEmpty()) {
            // FW version format : "major.minor.patch". Ex : "0.8.2"
            String[] tokens = StringUtils.split(versionCmd.fwVersion, ".");
            if(tokens.length == 3) {
                firmwareVersionMajor = Integer.parseInt(tokens[0]);
                firmwareVersionMinor = Integer.parseInt(tokens[1]);
                firmwareVersionPatch = Integer.parseInt(tokens[2]);
            }
        }

        handleGBDeviceEvent(versionCmd);
    }

    private boolean IsFirmwareAtLeastVersion0_9() {
        return firmwareVersionMajor > 0 || firmwareVersionMinor >= 9;
    }

    /**
     * Nordic DFU needs this function to log DFU-related messages
     */
    @Override
    public void onLogEvent(final String deviceAddress, final int level, final String message) {
        LOG.debug(message);
    }
}
