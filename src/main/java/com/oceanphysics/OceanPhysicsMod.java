package com.oceanphysics;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OceanPhysicsMod implements ModInitializer {
    public static final String MOD_ID = "oceanphysics";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        WaveConfig.load();
        LOGGER.info("Water Physics v2 dimuat. Ukuran ombak: {}", WaveConfig.get().waveSize);
    }
}
