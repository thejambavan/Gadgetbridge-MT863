package nodomain.freeyourgadget.gadgetbridge.devices.pinetime_lite;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import nodomain.freeyourgadget.gadgetbridge.util.ArrayUtils;
import nodomain.freeyourgadget.gadgetbridge.util.FileUtils;
import nodomain.freeyourgadget.gadgetbridge.util.UriHelper;

public class FirmwareHelper {
    private static final Logger LOG = LoggerFactory.getLogger(FirmwareHelper.class);

    @NonNull
    private final byte[] fw;

    private static final byte[] FW_RES_HEADER = new byte[]{
            (byte)0xAA, 0x52, 0x45, 0x53, 0x2D, 0x50, 0x2D, 0x4C, 0x49, 0x54, 0x45, 0x00, 0x01
    };

    private static final byte[] FW_ZIP_HEADER = new byte[]{
            0x50, 0x4B
    };

    private boolean valid = false;
    private String version = "(Unknown version)";
    protected PinetimeLiteFirmwareType firmwareInfo = PinetimeLiteFirmwareType.UKN;

    public FirmwareHelper(Uri uri, Context context) throws IOException {
        UriHelper uriHelper = UriHelper.get(uri, context);

        try (InputStream in = new BufferedInputStream(uriHelper.openInputStream())) {
            this.fw = FileUtils.readAll(in, 1024 * 4096); // 4.0 MB
            determineFirmwareType(fw);
        } catch (IOException ex) {
            throw ex; // pass through
        } catch (IllegalArgumentException ex) {
            throw new IOException("This doesn't seem to be a Pinetime Lite firmware: " + ex.getLocalizedMessage(), ex);
        } catch (Exception e) {
            throw new IOException("Error reading firmware file: " + uri.toString(), e);
        }
    }

    private void determineFirmwareType(byte[] fw) {

        if (ArrayUtils.equals(fw, FW_ZIP_HEADER, 0)) {
            firmwareInfo = PinetimeLiteFirmwareType.FIRMWARE;
            version = "Firmware for Pinetime Lite 1.0.0";
            valid = true;
        } else if (ArrayUtils.equals(fw, FW_RES_HEADER, 0)) {
            firmwareInfo = PinetimeLiteFirmwareType.RES;
            int version1 = fw[0x0d] % 0xff;
            int version2 = fw[0x0e] % 0xff;
            int version3 = fw[0x0f] % 0xff;
            version = "Resources version " + version1 + "." + version2+ "." + version3;
            valid = true;
        };

    }

    public PinetimeLiteFirmwareType getFirmwareType() {
        return firmwareInfo;
    }

    public String getHumanFirmwareVersion() {
        return version;
    }

    public boolean checkValid() {
        return valid;
    }

    @NonNull
    public byte[] getFw() {
        return fw;
    }

}
