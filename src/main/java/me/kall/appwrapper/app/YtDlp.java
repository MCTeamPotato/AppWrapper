package me.kall.appwrapper.app;

import me.kall.appwrapper.data.Downloaded;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class YtDlp {
    public static final String VIDEO_DOWNLOADING_FILE = "video-downloading.txt";
    public static final String AUDIO_DOWNLOADING_FILE = "audio-downloading.txt";

    private static final String VIDEO_DOWNLOADED_FILE = "video-downloaded.txt";
    private static final String AUDIO_DOWNLOADED_FILE = "audio-downloaded.txt";

    private static final Logger LOGGER = Logger.getLogger(YtDlp.class.getSimpleName());

    private static final ExecutorService DOWNLOADER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "NarutoDownloadThread");
        thread.setDaemon(true);
        return thread;
    });

    private final String absFFmpegPath, absYtDlpPath;
    private String absCookieSource = "";

    private YtDlp(String absFFmpegPath, String absYtDlpPath) {
        this.absFFmpegPath = absFFmpegPath;
        this.absYtDlpPath = absYtDlpPath;
    }

    @Contract("_, _ -> new")
    public static @NotNull YtDlp create(String absFFmpegPath, String absYtDlpPath) {
        return new YtDlp(absFFmpegPath, absYtDlpPath);
    }

    public void setCookie(@Nullable String cookie) {
        this.absCookieSource = cookie == null ? "" : cookie;
    }

    public CompletableFuture<Downloaded> download(String url, Path outputDirectory) {
        return this.download(url, url, outputDirectory);
    }

    public CompletableFuture<Downloaded> download(String videoUrl, String audioUrl, Path outputDirectory) {
        return CompletableFuture.supplyAsync(() -> {
            Path videoDownloading = outputDirectory.resolve(VIDEO_DOWNLOADING_FILE);
            Path audioDownloading = outputDirectory.resolve(AUDIO_DOWNLOADING_FILE);

            try {
                Files.createDirectories(outputDirectory);
                Files.writeString(videoDownloading, videoUrl);
                Files.writeString(audioDownloading, audioUrl);
            } catch (Throwable throwable) {
                LOGGER.severe("Exception storing download source. Video: " + videoUrl + ". Audio: " + audioUrl + ". Output directory: " + outputDirectory);
                throwable.printStackTrace(System.err);
                throw new RuntimeException(throwable);
            }

            Path absVideoPath = this.downloadVideo(videoUrl, outputDirectory, "video");
            Path absAudioPath = this.downloadAudio(audioUrl, outputDirectory, "audio");

            try {
                Files.move(videoDownloading, outputDirectory.resolve(VIDEO_DOWNLOADED_FILE));
                Files.move(audioDownloading, outputDirectory.resolve(AUDIO_DOWNLOADED_FILE));
            } catch (Throwable throwable) {
                LOGGER.severe("Exception validating downloaded source url. Output directory: " + outputDirectory);
                throwable.printStackTrace(System.err);
                throw new RuntimeException(throwable);
            }

            return new Downloaded(absVideoPath, absAudioPath);
        }, DOWNLOADER);
    }

    public Path downloadVideo(String url, @NotNull Path outputDirectory, String outputName) {
        Executable.runCommand(this.videoCommand(url, outputDirectory.resolve(outputName + ".%(ext)s").toString()), true);
        return outputDirectory.resolve(outputName + ".mp4");
    }

    public Path downloadAudio(String url, @NotNull Path outputDirectory, String outputName) {
        Executable.runCommand(this.audioCommand(url, outputDirectory.resolve(outputName + ".%(ext)s").toString()), true);
        return outputDirectory.resolve(outputName + ".mp3");
    }

    private @NotNull List<String> audioCommand(String audioUrl, String outputTemplate) {
        List<String> command = new ArrayList<>();

        command.add(this.absYtDlpPath);
        command.add(audioUrl);

        command.add("-o");
        command.add(outputTemplate);

        if (!this.absCookieSource.isBlank()) {
            command.add("--cookies");
            command.add(this.absCookieSource);
        }

        command.add("-x");

        command.add("--audio-format");
        command.add("mp3");

        command.add("--audio-quality");
        command.add("0");

        command.add("--no-playlist");
        command.add("--progress");

        command.add("--ffmpeg-location");
        command.add(this.absFFmpegPath);

        return command;
    }

    private @NotNull List<String> videoCommand(String videoUrl, String outputTemplate) {
        List<String> command = new ArrayList<>();

        command.add(this.absYtDlpPath);
        command.add(videoUrl);

        command.add("-o");
        command.add(outputTemplate);

        if (!this.absCookieSource.isBlank()) {
            command.add("--cookies");
            command.add(this.absCookieSource);
        }

        command.add("--format");
        command.add("bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best");

        command.add("--merge-output-format");
        command.add("mp4");

        command.add("--no-playlist");
        command.add("--progress");

        command.add("--ffmpeg-location");
        command.add(this.absFFmpegPath);

        return command;
    }
}
