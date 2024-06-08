package com.teslamotors.protocol;

public class Utils {

    /**
     * 带符号的 byte
     *
     * @param value
     * @return
     * @throws UtilityException
     */
    public static byte[] integerToTwoBytes(int value) throws UtilityException {
        byte[] result = new byte[2];
        if ((value > Math.pow(2, 31)) || (value < 0)) {
            throw new UtilityException("Integer value " + value + " is larger than 2^31");
        }
        result[0] = (byte) ((value >>> 8) & 0xFF);
        result[1] = (byte) (value & 0xFF);
        return result;
    }

    private static class UtilityException extends Exception {

        private static final long serialVersionUID = 3545800974716581680L;

        UtilityException(String mesg) {
            super(mesg);
        }
    }

    public static int read2BytesToInt(byte[] bytes) {
        return ((bytes[0] & 0xff) << 8) | (bytes[1] & 0xff);
    }


    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] hexToBytes(String hex) {
        hex = hex.length() % 2 != 0 ? "0" + hex : hex;

        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < b.length; i++) {
            int index = i * 2;
            int v = Integer.parseInt(hex.substring(index, index + 2), 16);
            b[i] = (byte) v;
        }
        return b;
    }

    //    private fun haha() {
//
//        val vin = "LRW3E7FA0MC171694"
//        val bytes = vin.toByteArray(StandardCharsets.UTF_8)
//
//        val md = MessageDigest.getInstance("SHA1")
//        val hash = md.digest(bytes)
//
//        val hex = Utils.bytesToHex(hash)
//        val bleName = hex.substring(0, 16)
//        Log.d(TAG, "haha: $bleName")
//    }


}

