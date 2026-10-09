package cn.nitrowater.core.api;

import cn.nitrowater.lib.common.io.FileExtension;

import java.util.UUID;

public record UploadItem(
        int originalIndex,
        String path,
        String uuidPlain,
        UUID uuid,
        FileExtension ext
) {}