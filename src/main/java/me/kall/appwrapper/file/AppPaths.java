package me.kall.appwrapper.file;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AppPaths {
    private static final boolean IS_WIN = System.getProperty("os.name").toLowerCase().contains("win");
    private static final String EXE_SUFFIX = IS_WIN ? ".exe" : "";

    private static final String ABS_FFMPEG_PATH = "appwrapper.ffmpeg";
    private static final String ABS_FFPROBE_PATH = "appwrapper.ffprobe";
    private static final String ABS_YTDLP_PATH = "appwrapper.ytdlp";

    static {
        AppPaths.setAbsFFmpegPath(AppPaths.findExecutable("ffmpeg"));
        AppPaths.setAbsFFprobePath(AppPaths.findExecutable("ffprobe"));
        AppPaths.setAbsYtDlpPath(Path.of(System.getProperty("user.dir")).resolve("yt-dlp").resolve("yt-dlp" + EXE_SUFFIX).toString());
    }

    private static @Nullable String findExecutable(String name) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isEmpty()) return null;
        for (String dir : pathEnv.split(File.pathSeparator)) {
            Path path = Paths.get(dir, name + EXE_SUFFIX);
            if (path.toFile().exists()) return path.normalize().toAbsolutePath().toString();
        }
        return null;
    }

    public static String absFFmpegPath() {
        return System.getProperty(ABS_FFMPEG_PATH);
    }

    public static String absFFprobePath() {
        return System.getProperty(ABS_FFPROBE_PATH);
    }

    public static String absYtDlpPath() {
        return System.getProperty(ABS_YTDLP_PATH);
    }

    public static void setAbsFFmpegPath(String absFFmpegPath) {
        System.setProperty(ABS_FFMPEG_PATH, absFFmpegPath);
    }

    public static void setAbsFFprobePath(String absFFprobePath) {
        System.setProperty(ABS_FFPROBE_PATH, absFFprobePath);
    }

    public static void setAbsYtDlpPath(String absYtDlpPath) {
        System.setProperty(ABS_YTDLP_PATH, absYtDlpPath);
    }
}
