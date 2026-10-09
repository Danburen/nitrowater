package cn.nitrowater.lib.utils;

import java.util.List;

/**
 * Data adapter for original data
 * @since 1.1-SNAPSHOT
 * @version 1.0
 * @author waterwood
 */
public final class DataAdapter {
    /**
     * Adapted object values to String values
     * @param value Object original values
     * @param defaultVal default values if can't be adapted
     * @return adapted data
     */
    public static List<String> stringListVal(Object value, List<String> defaultVal){
        if (value instanceof List<?> tempList) {
            if (tempList.stream().allMatch(item -> item instanceof String)) {
                return (List<String>) tempList;
            } else {
                return defaultVal;
            }
        } else {
            return defaultVal;
        }
    }

    /**
     * Round values to one decimal
     * @param value original Double values
     * @return only one decimal values
     */
    public static Double roundToOneDecimal(Double value){
        return Math.round(value * 10.0) / 10.0;
    }

    /**
     * parse dotStr like(1.x.x) to double version values -> 1.xx
     * @param dotStr String that contains dot.
     * @return double version
     */
    public static double parseVersion(String dotStr){
        int dotInd = dotStr.indexOf(".");
        String out;
        if(dotInd != -1){
            out = dotStr.substring(0,dotInd + 1) + dotStr.substring(dotInd + 1).replaceAll("\\.","");
            return Double.parseDouble(out);
        }else{
            return 0.0f;
        }
    }

    /**
     * Convert bytes to hex string
     * @param bytes  bytes
     * @return hex string
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
