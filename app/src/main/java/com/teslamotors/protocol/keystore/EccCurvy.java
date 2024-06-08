package com.teslamotors.protocol.keystore;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;

public class EccCurvy {

    private static final byte UNCOMPRESSED_POINT_INDICATOR = 0x04;

    public static ECPublicKey fromUncompressedPoint(final byte[] uncompressedPoint, final ECParameterSpec params) throws Exception {

        int offset = 0;
        if (uncompressedPoint[offset++] != UNCOMPRESSED_POINT_INDICATOR) {
            throw new IllegalArgumentException("Invalid uncompressedPoint encoding, no uncompressed point indicator");
        }

        int keySizeBytes = (params.getOrder().bitLength() + Byte.SIZE - 1) / Byte.SIZE;

        if (uncompressedPoint.length != 1 + 2 * keySizeBytes) {
            throw new IllegalArgumentException("Invalid uncompressedPoint encoding, not the correct size");
        }

        final BigInteger x = new BigInteger(1, Arrays.copyOfRange(uncompressedPoint, offset, offset + keySizeBytes));
        offset += keySizeBytes;
        final BigInteger y = new BigInteger(1, Arrays.copyOfRange(uncompressedPoint, offset, offset + keySizeBytes));
        final ECPoint w = new ECPoint(x, y);
        final ECPublicKeySpec ecPublicKeySpec = new ECPublicKeySpec(w, params);
        final KeyFactory keyFactory = KeyFactory.getInstance("EC");
        return (ECPublicKey) keyFactory.generatePublic(ecPublicKeySpec);
    }

    public static byte[] toUncompressedPoint(final ECPublicKey publicKey) {

        int keySizeBytes = (publicKey.getParams().getOrder().bitLength() + Byte.SIZE - 1) / Byte.SIZE;

        final byte[] uncompressedPoint = new byte[1 + 2 * keySizeBytes];
        int offset = 0;
        uncompressedPoint[offset++] = 0x04;

        final byte[] x = publicKey.getW().getAffineX().toByteArray();
        if (x.length <= keySizeBytes) {
            System.arraycopy(x, 0, uncompressedPoint, offset + keySizeBytes - x.length, x.length);
        } else if (x.length == keySizeBytes + 1 && x[0] == 0) {
            System.arraycopy(x, 1, uncompressedPoint, offset, keySizeBytes);
        } else {
            throw new IllegalStateException("x value is too large");
        }
        offset += keySizeBytes;

        final byte[] y = publicKey.getW().getAffineY().toByteArray();
        if (y.length <= keySizeBytes) {
            System.arraycopy(y, 0, uncompressedPoint, offset + keySizeBytes - y.length, y.length);
        } else if (y.length == keySizeBytes + 1 && y[0] == 0) {
            System.arraycopy(y, 1, uncompressedPoint, offset, keySizeBytes);
        } else {
            throw new IllegalStateException("y value is too large");
        }

        return uncompressedPoint;
    }
}
