package cn.nitrowater.core.utils.io;

import cn.nitrowater.core.utils.PathUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class SimpleOutputUtil {
    public static void outputToDesktop(String filename, String content) {
        try {
            String desktop = PathUtil.buildSystemPath(System.getProperty("user.home"), "Desktop");
            Path path = Paths.get(desktop, filename);
            Files.writeString(path, content, StandardCharsets.UTF_8);
            System.out.println("The File has already saved to: " + path);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save the file", e);
        }
    }
}
