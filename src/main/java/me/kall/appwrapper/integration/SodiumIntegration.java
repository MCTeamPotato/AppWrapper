package me.kall.appwrapper.integration;

import me.jellysquid.mods.sodium.client.gui.options.OptionImpact;
import me.kall.appwrapper.config.MediaConfig;
import me.kall.duplicationless.util.SodiumOptions;
import net.minecraftforge.common.MinecraftForge;
import org.embeddedt.embeddium.api.OptionGUIConstructionEvent;
import org.jetbrains.annotations.NotNull;

public final class SodiumIntegration {
    private static void loadOptions(@NotNull OptionGUIConstructionEvent event) {
        event.addPage(SodiumOptions.newPage("video_size_cap_page", SodiumOptions.newGroup("video_size_cap_group", SodiumOptions.enumOption(MediaConfig.SizeCap.class, "video_size_cap", true, MediaConfig::getCap, MediaConfig::setCap, OptionImpact.MEDIUM))));
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(SodiumIntegration::loadOptions);
    }
}
