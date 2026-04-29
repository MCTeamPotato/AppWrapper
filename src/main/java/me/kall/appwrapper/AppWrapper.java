package me.kall.appwrapper;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(AppWrapper.MOD_ID)
public final class AppWrapper {
    public static final String MOD_ID = "appwrapper";
    public static final Logger LOGGER = LogManager.getLogger(AppWrapper.class);

    public AppWrapper(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
    }
}
