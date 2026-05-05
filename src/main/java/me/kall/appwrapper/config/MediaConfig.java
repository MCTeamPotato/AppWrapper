package me.kall.appwrapper.config;

import me.kall.duplicationless.config.JsonConfig;
import org.jetbrains.annotations.NotNull;

public class MediaConfig {
    private static final JsonConfig CONFIG = JsonConfig.create("video_size_cap", "1")
            .put("video_width_cap", 7680)
            .put("video_height_cap", 4320)
            .initialize();

    public static SizeCap getCap() {
        return SizeCap.fromWidth(CONFIG.getInt("video_width_cap"));
    }

    public static void setCap(@NotNull SizeCap sizeCap) {
        CONFIG.put("video_width_cap", sizeCap.width).put("video_height_cap", sizeCap.height).saveToFile();
    }

    public enum SizeCap {
        SMOOTH(640, 360),
        SD(854, 480),
        HD(1280, 720),
        FULL_HD(1920, 1080),
        UHD_4K(3840, 2160),
        UHD_8K(7680, 4320);

        public final int width, height;

        SizeCap(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public static SizeCap fromWidth(int width) {
            if (width < 854) return SMOOTH;
            if (width < 1280) return SD;
            if (width < 1920) return HD;
            if (width < 3840) return FULL_HD;
            if (width < 7680) return UHD_4K;
            if (width > 7680) throw new RuntimeException("Unsupported video size. Width " + width + " cannot go beyond 7680 (8K)");
            return UHD_8K;
        }
    }
}
