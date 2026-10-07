package cn.nitrowater.core.lib.api;

import cn.nitrowater.core.lib.common.io.FileExtension;

import java.util.UUID;

public record UploadItem(
        int originalIndex,
        String path,
        String uuidPlain,
        UUID uuid,
        FileExtension ext
) {}