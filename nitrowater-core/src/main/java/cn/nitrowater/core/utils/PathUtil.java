package cn.nitrowater.core.utils;

import cn.hutool.core.date.DateUtil;
import cn.nitrowater.core.common.io.FileExtension;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PathUtil {
    public static String getUniqueDateStampFilePath(String fileSuffix) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return DateUtil.today().replace("-", "/") + "/" + uuid + "." + fileSuffix;
    }

    public static String getDataStampFilePath(String fileName, FileExtension ext){
        return DateUtil.today().replace("-", "/") + "/" + fileName + "." + ext.getExt();
    }

    /**
     * Build path by default separator "/". Flatten array segments into individual segments.
     * Using {@link #buildResourcePath(Serializable...)} or {@link #buildSystemPath(Serializable...)} instead is recommended.
     * @param segments segments
     * @return result
     */
    @Deprecated
    public static String buildPath(Serializable... segments){
        return build("/", segments);
    }

    /**
     * Build path by system separator
     * @param segments segments
     * @return result
     */
    public static String buildSystemPath(Serializable... segments){
        return build(File.separator, segments);
    }

    /**
     * Build resource path
     * @param segments segments
     * @return result
     */
    public static String buildResourcePath(Serializable... segments){
        return build("/", segments);
    }

    private static String build(String delimiter, Serializable... segments){
        if (segments == null || segments.length == 0) {
            return "";
        }

        List<String> flattened = new ArrayList<>();
        for (Serializable seg : segments) {
            if (seg == null) {
                flattened.add("");
            } else if (seg.getClass().isArray()) { // flat map array segments
                int len = Array.getLength(seg);
                for (int i = 0; i < len; i++) {
                    Object item = Array.get(seg, i);
                    flattened.add(item != null ? item.toString() : "");
                }
            } else {
                flattened.add(seg.toString());
            }
        }

        return String.join(delimiter, flattened);
    }

    public static String getFilenameWithNoSuffix(String path){
        return path.substring(path.lastIndexOf("/") + 1, path.lastIndexOf("."));
    }

    public static String getSuffix(String fullKeyPath) {
        return fullKeyPath.substring(fullKeyPath.lastIndexOf(".") + 1);
    }
}
