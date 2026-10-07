package cn.nitrowater.core.lib.utils.generator;

import cn.nitrowater.core.lib.common.io.FileExtension;
import cn.nitrowater.core.lib.utils.PathUtil;

import java.util.UUID;

public final class CosKeyPathGenerator {
    public static String ofUser(Long userUid, UUID uuid, FileExtension ext){
        return PathUtil.buildResourcePath(
                userUid.toString(),
                PathUtil.getDataStampFilePath(uuid.toString().replace("-",""), ext)
        );
    }

    public static String of(UUID resourceUUID, FileExtension ext){
        return PathUtil.getDataStampFilePath(resourceUUID.toString().replace("-",""), ext);
    }

}
