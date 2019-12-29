/*  Copyright (C) 2019 krzys_h

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
package nodomain.freeyourgadget.gadgetbridge.devices.dafit;

import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.le.ScanFilter;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelUuid;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;

import nodomain.freeyourgadget.gadgetbridge.GBException;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.InstallHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings.DafitEnumDeviceVersion;
import nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings.DafitEnumMetricSystem;
import nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings.DafitEnumTimeSystem;
import nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings.DafitSetting;
import nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings.DafitSettingBool;
import nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings.DafitSettingByte;
import nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings.DafitSettingEnum;
import nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings.DafitSettingInt;
import nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings.DafitSettingLanguage;
import nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings.DafitSettingRemindersToMove;
import nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings.DafitSettingTimeRange;
import nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings.DafitSettingUserInfo;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;

public class MT863DeviceCoordinator extends AbstractDeviceCoordinator {

    @Override
    public DeviceType getDeviceType() {
        return DeviceType.MT863;
    }

    @Override
    public String getManufacturer() {
        return "Media-Tech";
    }

    @NonNull
    @Override
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public Collection<? extends ScanFilter> createBLEScanFilters() {
        ParcelUuid service = new ParcelUuid(DafitConstants.UUID_SERVICE_DAFIT);
        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(service).build();
        return Collections.singletonList(filter);
    }

    @NonNull
    @Override
    public DeviceType getSupportedType(GBDeviceCandidate candidate) {
        // TODO: It would be nice to also filter on "manufacturer" (which is used as a protocol version) being MOYOUNG-V2 or MOYOUNG but I have no idea if it's possible to do that at this point
        if (candidate.supportsService(DafitConstants.UUID_SERVICE_DAFIT)) {
            return DeviceType.MT863;
        }
        return DeviceType.UNKNOWN;
    }

    @Override
    public int getBondingStyle() {
        return BONDING_STYLE_NONE;
    }

    @Nullable
    @Override
    public Class<? extends Activity> getPairingActivity() {
        return null;
    }

    @Override
    protected void deleteDevice(@NonNull GBDevice gbDevice, @NonNull Device device, @NonNull DaoSession session) throws GBException {

    }

    @Override
    public boolean supportsActivityDataFetching() {
        return true;
    }

    @Override
    public boolean supportsActivityTracking() {
        return true;
    }

    @Override
    public SampleProvider<? extends ActivitySample> getSampleProvider(GBDevice device, DaoSession session) {
        return new MT863SampleProvider(device, session);
    }

    @Override
    public InstallHandler findInstallHandler(Uri uri, Context context) {
        return null;
    }

    @Override
    public boolean supportsScreenshots() {
        return false;
    }

    @Override
    public int getAlarmSlotCount() {
        return 3;
    }

    @Override
    public boolean supportsSmartWakeup(GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsHeartRateMeasurement(GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsAppsManagement() {
        return false;
    }

    @Override
    public Class<? extends Activity> getAppsManagementActivity() {
        return null;
    }

    @Override
    public boolean supportsCalendarEvents() {
        return false;
    }

    @Override
    public boolean supportsRealtimeData() {
        return true;
    }

    @Override
    public boolean supportsWeather() {
        return true;
    }

    @Override
    public boolean supportsFindDevice() {
        return true;
    }

    @Override
    public boolean supportsActivityTracks() {
        return true;
    }

    @Override
    public boolean supportsMusicInfo() {
        return false;
    }

    @Override
    public boolean supportsLedColor() {
        return false;
    }

    @Override
    public boolean supportsRgbLedColor() {
        return false;
    }

    @NonNull
    @Override
    public int[] getColorPresets() {
        return new int[0];
    }

    @Override
    public boolean supportsUnicodeEmojis() { return false; }

    private static final DafitSetting[] DAFIT_SETTINGS = new DafitSetting[] {
        new DafitSettingUserInfo("USER_INFO", DafitConstants.CMD_SET_USER_INFO),
        new DafitSettingByte("STEP_LENGTH", (byte)-1, DafitConstants.CMD_SET_STEP_LENGTH),
        // (*) new DafitSettingEnum<>("DOMINANT_HAND", DafitConstants.CMD_QUERY_DOMINANT_HAND, DafitConstants.CMD_SET_DOMINANT_HAND, DafitEnumDominantHand.class),
        new DafitSettingInt("GOAL_STEP", DafitConstants.CMD_QUERY_GOAL_STEP, DafitConstants.CMD_SET_GOAL_STEP),

        new DafitSettingEnum<>("DEVICE_VERSION", DafitConstants.CMD_QUERY_DEVICE_VERSION, DafitConstants.CMD_SET_DEVICE_VERSION, DafitEnumDeviceVersion.class),
        new DafitSettingLanguage("DEVICE_LANGUAGE", DafitConstants.CMD_QUERY_DEVICE_LANGUAGE, DafitConstants.CMD_SET_DEVICE_LANGUAGE),
        new DafitSettingEnum<>("TIME_SYSTEM", DafitConstants.CMD_QUERY_TIME_SYSTEM, DafitConstants.CMD_SET_TIME_SYSTEM, DafitEnumTimeSystem.class),
        new DafitSettingEnum<>("METRIC_SYSTEM", DafitConstants.CMD_QUERY_METRIC_SYSTEM, DafitConstants.CMD_SET_METRIC_SYSTEM, DafitEnumMetricSystem.class),

        // (*) new DafitSetting("DISPLAY_DEVICE_FUNCTION", DafitConstants.CMD_QUERY_DISPLAY_DEVICE_FUNCTION, DafitConstants.CMD_SET_DISPLAY_DEVICE_FUNCTION),
        // (*) new DafitSetting("SUPPORT_WATCH_FACE", DafitConstants.CMD_QUERY_SUPPORT_WATCH_FACE, (byte)-1),
        // (*) new DafitSetting("WATCH_FACE_LAYOUT", DafitConstants.CMD_QUERY_WATCH_FACE_LAYOUT, DafitConstants.CMD_SET_WATCH_FACE_LAYOUT),
        new DafitSettingByte("DISPLAY_WATCH_FACE", DafitConstants.CMD_QUERY_DISPLAY_WATCH_FACE, DafitConstants.CMD_SET_DISPLAY_WATCH_FACE),
        new DafitSettingBool("OTHER_MESSAGE_STATE", DafitConstants.CMD_QUERY_OTHER_MESSAGE_STATE, DafitConstants.CMD_SET_OTHER_MESSAGE_STATE),

        new DafitSettingBool("QUICK_VIEW", DafitConstants.CMD_QUERY_QUICK_VIEW, DafitConstants.CMD_SET_QUICK_VIEW),
        new DafitSettingTimeRange("QUICK_VIEW_TIME", DafitConstants.CMD_QUERY_QUICK_VIEW_TIME, DafitConstants.CMD_SET_QUICK_VIEW_TIME),
        new DafitSettingBool("SEDENTARY_REMINDER", DafitConstants.CMD_QUERY_SEDENTARY_REMINDER, DafitConstants.CMD_SET_SEDENTARY_REMINDER),
        new DafitSettingRemindersToMove("REMINDERS_TO_MOVE_PERIOD", DafitConstants.CMD_QUERY_REMINDERS_TO_MOVE_PERIOD, DafitConstants.CMD_SET_REMINDERS_TO_MOVE_PERIOD),
        new DafitSettingTimeRange("DO_NOT_DISTURB_TIME", DafitConstants.CMD_QUERY_DO_NOT_DISTURB_TIME, DafitConstants.CMD_SET_DO_NOT_DISTURB_TIME),
        // (*) new DafitSetting("PSYCHOLOGICAL_PERIOD", DafitConstants.CMD_QUERY_PSYCHOLOGICAL_PERIOD, DafitConstants.CMD_SET_PSYCHOLOGICAL_PERIOD),

        new DafitSettingBool("BREATHING_LIGHT", DafitConstants.CMD_QUERY_BREATHING_LIGHT, DafitConstants.CMD_SET_BREATHING_LIGHT)
    };

    @Override
    public int[] getSupportedDeviceSpecificSettings(GBDevice device) {
        return new int[]{
            R.xml.devicesettings_personalinfo,
            //R.xml.devicesettings_steplength, // TODO is this needed? does it work? write-only so hard to tell
            R.xml.devicesettings_dafit_device_version,
            R.xml.devicesettings_dafit_language,
            R.xml.devicesettings_timeformat,
            R.xml.devicesettings_measurementsystem,
            R.xml.devicesettings_dafit_watchface,
            //R.xml.devicesettings_dafit_othermessage, // not implemented because this doesn't really do anything on the watch side, only enables/disables sending of "other" notifications in the app (no idea why they store the setting on the watch)
            R.xml.devicesettings_liftwrist_display,
            R.xml.devicesettings_dafit_sedentary_reminder,
            R.xml.devicesettings_donotdisturb_no_auto_v2,
            //R.xml.devicesettings_dafit_breathinglight, // No idea what this does but it doesn't seem to change anything
        };
    }

    public DafitSetting[] getSupportedSettings() {
        return DAFIT_SETTINGS;
    }
}
