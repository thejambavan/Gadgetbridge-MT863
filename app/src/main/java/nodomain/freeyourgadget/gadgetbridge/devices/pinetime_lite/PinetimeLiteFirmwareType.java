package nodomain.freeyourgadget.gadgetbridge.devices.pinetime_lite;

public enum PinetimeLiteFirmwareType {
    UKN((byte) 0),
    FIRMWARE((byte) 1),
    RES((byte) 2),
    WATCHFACE((byte)3);

    private final byte value;

    PinetimeLiteFirmwareType(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }
}
