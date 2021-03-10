/*  Copyright (C) 2020-2021 Taavi Eomäe

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
package nodomain.freeyourgadget.gadgetbridge.devices.pinetime_lite;

import java.util.UUID;

public class PineTimeLiteConstants {
    public static final UUID UUID_SERVICE_MUSIC_CONTROL = UUID.fromString("00000000-78fc-48fe-8e23-433b3a1942d0");

    public static final UUID UUID_CHARACTERISTICS_MUSIC_EVENT = UUID.fromString("00000001-78fc-48fe-8e23-433b3a1942d0");
    public static final UUID UUID_CHARACTERISTICS_MUSIC_STATUS = UUID.fromString("00000002-78fc-48fe-8e23-433b3a1942d0");
    public static final UUID UUID_CHARACTERISTICS_MUSIC_ARTIST = UUID.fromString("00000003-78fc-48fe-8e23-433b3a1942d0");
    public static final UUID UUID_CHARACTERISTICS_MUSIC_TRACK = UUID.fromString("00000004-78fc-48fe-8e23-433b3a1942d0");
    public static final UUID UUID_CHARACTERISTICS_MUSIC_ALBUM = UUID.fromString("00000005-78fc-48fe-8e23-433b3a1942d0");
    public static final UUID UUID_CHARACTERISTICS_MUSIC_POSITION = UUID.fromString("00000006-78fc-48fe-8e23-433b3a1942d0");
    public static final UUID UUID_CHARACTERISTICS_MUSIC_LENGTH_TOTAL = UUID.fromString("00000007-78fc-48fe-8e23-433b3a1942d0");
    public static final UUID UUID_CHARACTERISTICS_MUSIC_TRACK_NUMBER = UUID.fromString("00000008-78fc-48fe-8e23-433b3a1942d0");
    public static final UUID UUID_CHARACTERISTICS_MUSIC_TRACK_TOTAL = UUID.fromString("00000009-78fc-48fe-8e23-433b3a1942d0");
    public static final UUID UUID_CHARACTERISTICS_MUSIC_PLAYBACK_SPEED = UUID.fromString("0000000a-78fc-48fe-8e23-433b3a1942d0");
    public static final UUID UUID_CHARACTERISTICS_MUSIC_REPEAT = UUID.fromString("0000000b-78fc-48fe-8e23-433b3a1942d0");
    public static final UUID UUID_CHARACTERISTICS_MUSIC_SHUFFLE = UUID.fromString("0000000c-78fc-48fe-8e23-433b3a1942d0");

    public static final UUID UUID_CHARACTERISTIC_ALERT_NOTIFICATION_EVENT = UUID.fromString("00020001-78fc-48fe-8e23-433b3a1942d0");

    // notification types and icons
    public static final byte NOTIFICATION_MISSED_CALL = (byte) 0x00;
    public static final byte NOTIFICATION_SMS = (byte) 0x01;
    public static final byte NOTIFICATION_SOCIAL = (byte) 0x02;
    public static final byte NOTIFICATION_EMAIL = (byte) 0x03;
    public static final byte NOTIFICATION_CALENDAR = (byte) 0x04;
    public static final byte NOTIFICATION_INCOME_CALL = (byte) 0x05;
    public static final byte NOTIFICATION_CALL_OFF = (byte) 0x06;
    public static final byte NOTIFICATION_WECHAT = (byte) 0x07;
    public static final byte NOTIFICATION_VIBER = (byte) 0x08;
    public static final byte NOTIFICATION_SNAPCHAT = (byte) 0x09;
    public static final byte NOTIFICATION_WHATSAPP = (byte) 0x0A;
    public static final byte NOTIFICATION_FACEBOOK = (byte) 0x0C;
    public static final byte NOTIFICATION_MESSENGER = (byte) 0x0F;
    public static final byte NOTIFICATION_INSTAGRAM = (byte) 0x10;
    public static final byte NOTIFICATION_TWITTER = (byte) 0x11;
    public static final byte NOTIFICATION_LINKEDIN = (byte) 0x12;
    public static final byte NOTIFICATION_LINE = (byte) 0x14;
    public static final byte NOTIFICATION_SKYPE = (byte) 0x15;


    public static final String PREF_SCREENTIME = "pinetime_screentime";
    public static final String PREF_ANALOG_MODE = "pinetime_analog_mode";
    public static final String PREF_DO_NOT_DISTURB = "pinetime_do_not_disturb";
    public static final String PREF_SHOCK_STRENGTH = "pinetime_shock_strength";
    public static final String PREF_USER_STEP_GOAL = "activity_user_sleep_duration";
}
