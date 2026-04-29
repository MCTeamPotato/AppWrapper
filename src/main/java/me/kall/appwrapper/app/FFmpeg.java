package me.kall.appwrapper.app;

import com.google.common.collect.Lists;
import com.google.common.io.Files;
import me.kall.appwrapper.data.MediaArgs;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL10;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FFmpeg {
    private static final Pattern VIDEO_STREAM = Pattern.compile("\\{[^}]*\"codec_type\"\\s*:\\s*\"video\"[^}]*}");
    private static final Pattern R_FPS = Pattern.compile("\"r_frame_rate\"\\s*:\\s*\"(\\d+)/(\\d+)\"");
    private static final Pattern AVG_FPS = Pattern.compile("\"avg_frame_rate\"\\s*:\\s*\"(\\d+)/(\\d+)\"");
    private static final Pattern DURATION = Pattern.compile("\"format\"\\s*:\\s*\\{[^}]*\"duration\"\\s*:\\s*\"([0-9.]+)\"");
    private static final Pattern WIDTH = Pattern.compile("\"width\"\\s*:\\s*(\\d+)");
    private static final Pattern HEIGHT = Pattern.compile("\"height\"\\s*:\\s*(\\d+)");
    private static final Pattern AUDIO_STREAM = Pattern.compile("\\{[^}]*\"codec_type\"\\s*:\\s*\"audio\"[^}]*}");
    private static final Pattern SAMPLE_RATE = Pattern.compile("\"sample_rate\"\\s*:\\s*\"(\\d+)\"");
    private static final Pattern CHANNEL_COUNT = Pattern.compile("\"channels\"\\s*:\\s*(\\d+)");

    private static final Logger LOGGER = Logger.getLogger(FFmpeg.class.getSimpleName());

    private final String absFFmpegPath, absFFprobePath;

    private FFmpeg(String absFFmpegPath, String absFFprobePath) {
        this.absFFmpegPath = absFFmpegPath;
        this.absFFprobePath = absFFprobePath;
    }

    @Contract("_, _ -> new")
    public static @NotNull FFmpeg create(@NotNull String absFFmpegPath, @NotNull String absFFprobePath) {
        return new FFmpeg(absFFmpegPath, absFFprobePath);
    }

    @Contract("_, _ -> new")
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public @NotNull MediaArgs read(@NotNull String absVideoPath, @NotNull String absAudioPath) {
        String videoJson = Executable.runCommand(new String[]{this.absFFprobePath, "-v", "quiet", "-print_format", "json", "-show_streams", "-show_format", absVideoPath}, false);
        if (videoJson == null) throw new RuntimeException("Error generating probe json for " + absVideoPath + ". Using " + this.absFFprobePath + ". Reading log for details.");

        String audioJson = Executable.runCommand(new String[]{this.absFFprobePath, "-v", "quiet", "-print_format", "json", "-show_streams", "-show_format", absAudioPath}, false);
        if (audioJson == null) throw new RuntimeException("Error generating probe json for " + absAudioPath + ". Using " + this.absFFprobePath + ". Reading log for details.");

        try {
            Matcher videoMatcher = VIDEO_STREAM.matcher(videoJson);
            Matcher audioMatcher = AUDIO_STREAM.matcher(audioJson);
            if (!videoMatcher.find()) throw new RuntimeException("Error matching video block");
            if (!audioMatcher.find()) throw new RuntimeException("Error matching audio block");
            String videoBlock = videoMatcher.group();
            String audioBlock = audioMatcher.group();

            Matcher channelCountMatcher = CHANNEL_COUNT.matcher(audioBlock);
            Matcher sampleRateMatcher = SAMPLE_RATE.matcher(audioBlock);

            if (!channelCountMatcher.find()) throw new RuntimeException("Error matching channel count");
            if (!sampleRateMatcher.find()) throw new RuntimeException("Error matching sample rate");

            Matcher avgFpsMatcher = AVG_FPS.matcher(videoBlock);
            Matcher rFpsMatcher = R_FPS.matcher(videoBlock);
            Matcher widthMatcher = WIDTH.matcher(videoBlock);
            Matcher heightMatcher = HEIGHT.matcher(videoBlock);
            Matcher durationMatcher = DURATION.matcher(videoJson);

            avgFpsMatcher.find();
            rFpsMatcher.find();
            widthMatcher.find();
            heightMatcher.find();
            durationMatcher.find();

            int channelCount = Integer.parseInt(channelCountMatcher.group(1));
            int sampleRate = Integer.parseInt(sampleRateMatcher.group(1));

            double avgFps = Double.parseDouble(avgFpsMatcher.group(1)) / Double.parseDouble(avgFpsMatcher.group(2));
            double rFps = Double.parseDouble(rFpsMatcher.group(1)) / Double.parseDouble(rFpsMatcher.group(2));

            int width = Integer.parseInt(widthMatcher.group(1));
            int height = Integer.parseInt(heightMatcher.group(1));

            double duration = Double.parseDouble(durationMatcher.group(1)) * 1000D;
            return new MediaArgs(absVideoPath, absAudioPath, channelCount, sampleRate, channelCount == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16, avgFps > 0 ? avgFps : rFps, width, height, duration);
        } catch (Throwable throwable) {
            LOGGER.severe("————————————————————————————");
            LOGGER.severe("Exception reading video " + absVideoPath + " using " + this.absFFprobePath);
            LOGGER.severe(videoJson);
            LOGGER.severe("————————————————————————————");
            LOGGER.severe("Exception reading audio " + absAudioPath + " using " + this.absFFprobePath);
            LOGGER.severe(audioJson);
            LOGGER.severe("————————————————————————————");
            throwable.printStackTrace(System.err);
            throw new RuntimeException(throwable);
        }
    }

    @Contract("_ -> new")
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public @NotNull CompletableFuture<@Nullable String> convertAudio(String absSource) {
        return CompletableFuture.supplyAsync(() -> {
            if ("ogg".equals(Files.getFileExtension(absSource)) && this.isMono(absSource)) return absSource;

            File absOutput = this.setOutput(absSource);
            String absOutputPath = absOutput.getAbsolutePath();
            if (absOutput.exists()) {
                if (this.isMono(absOutputPath) && "ogg".equals(Files.getFileExtension(absOutputPath))) {
                    return absOutputPath;
                } else {
                    absOutput.delete();
                }
            }

            Executable.runCommand(Lists.newArrayList(this.absFFmpegPath, "-i", absSource, "-vn", "-acodec", "libvorbis", "-ac", "1", "-q:a", "4", "-y", absOutputPath), false);
            return absOutput.exists() ? absOutputPath : null;
        });
    }

    private @NotNull File setOutput(String absSource) {
        File sourceFile = new File(absSource);
        if (!sourceFile.exists() || !sourceFile.isFile()) throw new IllegalArgumentException("Invalid source audio: " + absSource);

        String toConvertName = sourceFile.getName();

        return sourceFile.toPath().getParent().resolve(toConvertName.substring(0, toConvertName.lastIndexOf(".")) + ".ogg").toFile();
    }

    private boolean isMono(String absAudioPath) {
        return "1".equals(Executable.runCommand(new String[]{this.absFFprobePath, "-v", "error", "-select_streams", "a:0", "-show_entries", "stream=channels", "-of", "default=noprint_wrappers=1:nokey=1", absAudioPath}, false));
    }
}