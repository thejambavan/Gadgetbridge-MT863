/*  Copyright (C) 2020-2021 Andreas Shimokawa, Taavi Eomäe

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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.InstallActivity;
import nodomain.freeyourgadget.gadgetbridge.devices.InstallHandler;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.model.GenericItem;
import nodomain.freeyourgadget.gadgetbridge.util.ArrayUtils;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.UriHelper;


public class PineTimeLiteInstallHandler implements InstallHandler {
    private static final Logger LOG = LoggerFactory.getLogger(PineTimeLiteInstallHandler.class);

    private Context context;

    private FirmwareHelper fwHelper;

    public PineTimeLiteInstallHandler(Uri uri, Context context) throws IOException {

        this.context = context;

        fwHelper = new FirmwareHelper(uri, context);

    }

    @Override
    public void validateInstallation(InstallActivity installActivity, GBDevice device) {
        if (device.isBusy()) {
            installActivity.setInfoText(device.getBusyTask());
            installActivity.setInstallEnabled(false);
            return;
        }

        if (device.getType() != DeviceType.PINETIME_LITE || !device.isConnected()) {
            installActivity.setInfoText("Firmware cannot be installed");
            installActivity.setInstallEnabled(false);
            return;
        }

        if ( fwHelper.getFirmwareType() == PinetimeLiteFirmwareType.UKN ) {
            installActivity.setInfoText("Firmware type is not supported.");
            installActivity.setInstallEnabled(false);
            return;
        }

        GenericItem installItem = new GenericItem();
        installItem.setIcon(R.drawable.ic_firmware);
        if ( fwHelper.getFirmwareType() == PinetimeLiteFirmwareType.RES ) {
            installItem.setName("PineTime Lite Resources");
        } else {
            installItem.setName("PineTime Lite Firmware");
        }
        installItem.setDetails(fwHelper.getHumanFirmwareVersion());

        installActivity.setInfoText(context.getString(R.string.firmware_install_warning, "1.0.0"));
        installActivity.setInstallEnabled(true);
        installActivity.setInstallItem(installItem);
        LOG.debug("Initialized PineTimeInstallHandler");
    }


    @Override
    public void onStartInstall(GBDevice device) {
    }

    @Override
    public boolean isValid() {
        return fwHelper.checkValid();
    }

}
