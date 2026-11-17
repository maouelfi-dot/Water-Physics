package com.oceanphysics;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OceanPhysicsMod implements ModInitializer {
    public static final String MOD_ID = "oceanphysics";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        WaveConfig.load();
        SpillRules.register();
        TreeFelling.register();
        ServerTickEvents.END_WORLD_TICK.register(WaveErosion::tick);
        LOGGER.info("Water Physics v8 dimuat. Ukuran ombak: {}", WaveConfig.get().waveSize);
    }
}
