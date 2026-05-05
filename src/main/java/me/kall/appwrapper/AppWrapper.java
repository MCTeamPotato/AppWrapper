package me.kall.appwrapper;

import me.kall.appwrapper.integration.SodiumIntegration;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod(AppWrapper.MOD_ID)
public final class AppWrapper {
    public static final String MOD_ID = "appwrapper";

    public AppWrapper() {
        if (ModList.get().isLoaded("embeddium")) SodiumIntegration.register();
    }
}
