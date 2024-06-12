package com.teslamotors.protocol.ble.beacon;

import com.teslamotors.protocol.util.ConstKt;
import com.teslamotors.protocol.util.ConversionUtils;

import java.util.UUID;

public class TeslaBeacon {

    public static final int MANUFACTURER_ID = 76;

    // the manufacturer data byte is the filter!
    private static final byte[] manufacturerData = new byte[]
            {
                    0, 0,

                    // uuid
                    0, 0, 0, 0,
                    0, 0,
                    0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,

                    // major
                    0, 0,

                    // minor
                    0, 0,

                    0
            };

    // the mask tells what bytes in the filter need to match, 1 if it has to match, 0 if not
    private static final byte[] manufacturerDataMask = new byte[]
            {
                    0, 0,

                    // uuid
                    1, 1, 1, 1,
                    1, 1,
                    1, 1,
                    1, 1, 1, 1, 1, 1, 1, 1,

                    // major
                    0, 0,

                    // minor
                    0, 0,

                    0
            };

    // copy UUID (with no dashes) into data array
    public static byte[] getManufactureData() {
        // UUID teslaUUID = UUID.fromString(ConstKt.TESLA_BLUETOOTH_BEACON_UUID);

        UUID teslaUUID = UUID.fromString(ConstKt.TESLA_BLUETOOTH_BEACON_UUID);
        System.arraycopy(ConversionUtils.UuidToByteArray(teslaUUID), 0, manufacturerData, 2, 16);

        // copy major into data array
        // System.arraycopy(ConversionUtils.integerToByteArray(11488), 0, manufacturerData, 18, 2);

        // copy minor into data array
        // System.arraycopy(ConversionUtils.integerToByteArray(24252), 0, manufacturerData, 20, 2);

        return manufacturerData;
    }

    public static byte[] getManufactureDataMask() {
        return manufacturerDataMask;
    }
}
