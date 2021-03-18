package nodomain.freeyourgadget.gadgetbridge.service.devices.pinetime_lite.operations;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventDisplayMessage;
import nodomain.freeyourgadget.gadgetbridge.devices.pinetime_lite.FirmwareHelper;
import nodomain.freeyourgadget.gadgetbridge.devices.pinetime_lite.PinetimeLiteFirmwareType;
import nodomain.freeyourgadget.gadgetbridge.service.btle.BLETypeConversions;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.actions.SetDeviceBusyAction;
import nodomain.freeyourgadget.gadgetbridge.service.btle.actions.SetProgressAction;
import nodomain.freeyourgadget.gadgetbridge.service.devices.pinetime_lite.PineTimeLiteSupport;
import nodomain.freeyourgadget.gadgetbridge.devices.pinetime_lite.PineTimeLiteConstants;
import nodomain.freeyourgadget.gadgetbridge.util.CheckSums;
import nodomain.freeyourgadget.gadgetbridge.util.GB;


public class FirmwareOperation  extends AbstractPinetimeLiteOperation {

    private static final Logger LOG = LoggerFactory.getLogger(FirmwareOperation.class);

    private FirmwareHelper fwHelper;

    private boolean firmwareInfoSent = false;

    final BluetoothGattCharacteristic fwCControlChar;
    final BluetoothGattCharacteristic fwCDataChar;

    public FirmwareOperation(Uri uri, PineTimeLiteSupport pineTimeLiteSupport) throws IOException {
        super(pineTimeLiteSupport);

       fwHelper = new FirmwareHelper(uri, getContext());

        fwCControlChar = getCharacteristic(PineTimeLiteConstants.UUID_CONTROL_FILE);
        fwCDataChar = getCharacteristic(PineTimeLiteConstants.UUID_CHARACTERISTIC_FILE);

    }

    public PinetimeLiteFirmwareType getFirmwareType() {
        return fwHelper.getFirmwareType();
    }

    public void done() {
        LOG.info("Operation done.");
        fwHelper = null;
        /*LocalBroadcastManager.getInstance(getContext()).sendBroadcast(new Intent(GB.ACTION_SET_PROGRESS_BAR)
                .putExtra(GB.PROGRESS_BAR_INDETERMINATE, false)
        );*/
        operationFinished();
        unsetBusy();
    }

    @Override
    public boolean onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            operationFailed();
        }
        return super.onCharacteristicWrite(gatt, characteristic, status);
    }

    void operationFailed() {
        GB.updateInstallNotification(getContext().getString(R.string.updatefirmwareoperation_write_failed), false, 0, getContext());
    }


    @Override
    protected void enableNeededNotifications(TransactionBuilder builder, boolean enable) {
        builder.notify(fwCControlChar, enable);
    }

    @Override
    protected void enableOtherNotifications(TransactionBuilder builder, boolean enable) {
    }

    @Override
    public boolean onCharacteristicChanged(BluetoothGatt gatt,
                                           BluetoothGattCharacteristic characteristic) {
        UUID characteristicUUID = characteristic.getUuid();
        if (fwCControlChar.getUuid().equals(characteristicUUID)) {
            handleNotificationNotif(characteristic.getValue());
        } else {
            super.onCharacteristicChanged(gatt, characteristic);
        }
        return false;
    }


    private void handleNotificationNotif(byte[] value) {
        if (value.length != 2) {
            LOG.error("Notifications should be 2 bytes long.");
            getSupport().logMessageContent(value);
            return;
        }

        boolean success = value[0] == PineTimeLiteConstants.SUCCESS;
        if (success) {
            try {
                switch (value[1]) {
                    case PineTimeLiteConstants.COMMAND_FIRMWARE_INIT: {
                        setProgressText("Sending firmware data.");
                        sendFirmwareData();
                        break;
                    }
                    case PineTimeLiteConstants.COMMAND_FIRMWARE_START_DATA: {
                        setProgressText("Validating checksum...");
                        sendChecksum();
                        break;
                    }
                    case PineTimeLiteConstants.COMMAND_FIRMWARE_CHECKSUM: {
                        GB.updateInstallNotification(getContext().getString(R.string.updatefirmwareoperation_update_complete), false, 100, getContext());
                        done();
                    }
                    default: {
                        LOG.error("Unexpected response during firmware update: ");
                        getSupport().logMessageContent(value);
                        operationFailed();
                        displayMessage(getContext(), "Problem with the firmware transfer. Unexpected notification.", Toast.LENGTH_LONG, GB.ERROR);
                        done();
                    }
                }
            } catch (Exception ex) {
                displayMessage(getContext(), "Problem with the firmware transfer. Info : " + ex.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);
                done();
            }
        } else {
            LOG.error("Unexpected notification during firmware update: ");
            operationFailed();
            getSupport().logMessageContent(value);
            displayMessage(getContext(), getContext().getString(R.string.updatefirmwareoperation_metadata_updateproblem), Toast.LENGTH_LONG, GB.ERROR);
            done();
    }

    }

    @Override
    protected void doPerform() throws IOException {

        // Todo (joaquimorg) : better validation needs to include the version in the resoure of the Pinetime Lite
        if (fwHelper.getFirmwareType() != PinetimeLiteFirmwareType.RES) {
            throw new IOException("Firmware is not compatible with the given device: " + getDevice().getAddress());
        }

        //LocalBroadcastManager.getInstance(getContext()).sendBroadcast(new Intent(GB.ACTION_SET_PROGRESS_BAR).putExtra(GB.PROGRESS_BAR_PROGRESS, 0));

        setProgressText("Sending firmware metadata.");
        if (!sendFwInfo()) {
            displayMessage(getContext(), "Error sending firmware info, aborting.", Toast.LENGTH_LONG, GB.ERROR);
            done();
        }
        /** the firmware will be sent by the {@link FirmwareOperation#handleNotificationNotif} if the band confirms that the metadata are ok. **/

    }

    private void displayMessage(Context context, String message, int duration, int severity) {
        getSupport().handleGBDeviceEvent(new GBDeviceEventDisplayMessage(message, duration, severity));
    }

    public void setProgressText(String progressText) {
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(new Intent(GB.ACTION_SET_PROGRESS_TEXT).putExtra(GB.DISPLAY_MESSAGE_MESSAGE, progressText));
    }

    public boolean sendFwInfo() {
        try {
            TransactionBuilder builder = performInitialized("send firmware info");
//                getSupport().setLowLatency(builder);
            builder.add(new SetDeviceBusyAction(getDevice(), getContext().getString(R.string.updating_firmware), getContext()));
            int fwSize = fwHelper.getFw().length;
            byte[] sizeBytes = BLETypeConversions.fromUint32(fwSize);
            int arraySize = 5;

            byte[] bytes = new byte[arraySize];
            int i = 0;
            bytes[i++] = PineTimeLiteConstants.COMMAND_SEND_FIRMWARE_INFO;
            bytes[i++] = sizeBytes[0];
            bytes[i++] = sizeBytes[1];
            bytes[i++] = sizeBytes[2];
            bytes[i++] = sizeBytes[3];

            builder.write(fwCControlChar, bytes);
            builder.queue(getQueue());
            return true;
        } catch (IOException e) {
            LOG.error("Error sending firmware info: " + e.getLocalizedMessage(), e);
            return false;
        }
    }

    private boolean sendFirmwareData() {
        int len = fwHelper.getFw().length;
        final int packetLength = 100;;
        int packets = len / packetLength;

        try {
            // going from 0 to len
            int firmwareProgress = 0;

            TransactionBuilder builder = performInitialized("send firmware packet");
            builder.write(fwCControlChar, new byte[]{PineTimeLiteConstants.COMMAND_FIRMWARE_START_DATA});

            for (int i = 0; i < packets; i++) {
                byte[] fwChunk = Arrays.copyOfRange(fwHelper.getFw(), i * packetLength, i * packetLength + packetLength);

                builder.write(fwCDataChar, fwChunk);
                firmwareProgress += packetLength;

                int progressPercent = (int) ((((float) firmwareProgress) / len) * 100);
                if ((i > 0) && (i % 100 == 0)) {
                    builder.write(fwCControlChar, new byte[]{PineTimeLiteConstants.COMMAND_FIRMWARE_UPDATE_SYNC});
                    builder.add(new SetProgressAction(getContext().getString(R.string.updatefirmwareoperation_update_in_progress), true, progressPercent, getContext()));


                    /*setProgressText(String.format(Locale.ENGLISH,
                            "Upload is in progress %1d%%",
                            progressPercent));*/

                    //LocalBroadcastManager.getInstance(getContext()).sendBroadcast(new Intent(GB.ACTION_SET_PROGRESS_BAR).putExtra(GB.PROGRESS_BAR_PROGRESS, progressPercent));

                }
            }

            if (firmwareProgress < len) {
                byte[] lastChunk = Arrays.copyOfRange(fwHelper.getFw(), packets * packetLength, len);
                builder.write(fwCDataChar, lastChunk);
            }

            builder.write(fwCControlChar, new byte[]{PineTimeLiteConstants.COMMAND_FIRMWARE_END_DATA});
            builder.queue(getQueue());

        } catch (IOException ex) {
            LOG.error("Unable to send fw to device", ex);
            GB.updateInstallNotification(getContext().getString(R.string.updatefirmwareoperation_firmware_not_sent), false, 0, getContext());
            return false;
        }
        return true;
    }


    protected void sendChecksum() throws IOException {
        TransactionBuilder builder = performInitialized("send firmware checksum");
        int crc16 = CheckSums.getCRC16(fwHelper.getFw());
        byte[] bytes = BLETypeConversions.fromUint16(crc16);
        builder.write(fwCControlChar, new byte[]{
                PineTimeLiteConstants.COMMAND_FIRMWARE_CHECKSUM,
                bytes[0],
                bytes[1],
        });
        builder.queue(getQueue());
    }

}
