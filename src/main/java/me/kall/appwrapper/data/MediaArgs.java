package me.kall.appwrapper.data;

import me.kall.appwrapper.config.MediaConfig;

public record MediaArgs(String absVideoPath, String absAudioPath, int channelCount, int sampleRate, int openALFormat, double fps, int width, int height, double duration) {
    @Override
    public int width() {
        int width = this.width;
        int widthCap = MediaConfig.getCap().width;
        if (width > widthCap) width = widthCap;
        return width;
    }

    @Override
    public int height() {
        int height = this.height;
        int heightCap = MediaConfig.getCap().height;
        if (height > heightCap) height = heightCap;
        return height;
    }
}