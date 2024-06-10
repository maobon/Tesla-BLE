package com.teslamotors.protocol.ble.beacon;

import static com.teslamotors.protocol.util.JUtils.bytesToHex;

import androidx.annotation.NonNull;

import com.teslamotors.protocol.util.JUtils;


public class BeaconUtil {

    private final static BeaconUtil sBeaconUtil = new BeaconUtil();

    public static BeaconUtil getInstance() {
        return sBeaconUtil;
    }

    private BeaconUtil() {
    }

    private String uuid;
    private int major;
    private int minor;

    private String completeLocalName;

    public String getUuid() {
        return uuid;
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public String getCompleteLocalName() {
        return completeLocalName;
    }

    public boolean findBeaconPattern(byte[] scanRecord) {
        int startByte = 2;
        boolean patternFound = false;

        while (startByte <= 5) {
            if (((int) scanRecord[startByte + 2] & 0xff) == 0x02 && // Identifies an iBeacon
                    ((int) scanRecord[startByte + 3] & 0xff) == 0x15) { // Identifies correct data length
                patternFound = true;
                break;
            }
            startByte++;
        }

        if (patternFound) {
            // Convert to hex String
            byte[] uuidBytes = new byte[16];
            System.arraycopy(scanRecord, startByte + 4, uuidBytes, 0, 16);
            String hexString = bytesToHex(uuidBytes);

            // UUID detection
            uuid = hexString.substring(0, 8) + "-" +
                    hexString.substring(8, 12) + "-" +
                    hexString.substring(12, 16) + "-" +
                    hexString.substring(16, 20) + "-" +
                    hexString.substring(20, 32);

            // major
            major = (scanRecord[startByte + 20] & 0xff) * 0x100 + (scanRecord[startByte + 21] & 0xff);

            // minor
            minor = (scanRecord[startByte + 22] & 0xff) * 0x100 + (scanRecord[startByte + 23] & 0xff);

            completeLocalName = getCompleteLocalName(scanRecord);
        }

        return patternFound;
    }

    @NonNull
    private String getCompleteLocalName(byte[] scanRecord) {
        String hex = JUtils.bytesToHex(scanRecord);
        String nameHex = hex.substring(hex.lastIndexOf("1309") + 4);
        return new String(JUtils.hexToBytes(nameHex));
    }
}
