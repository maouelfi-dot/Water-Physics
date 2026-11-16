package com.oceanphysics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class WaveConfig {
    public enum Size {
        SMALL(0.25), MEDIUM(0.6), LARGE(1.25);
        public final double amplitude;
        Size(double a) { this.amplitude = a; }
    }

    public boolean enabled = true;
    public Size waveSize = Size.MEDIUM;     // SMALL / MEDIUM / LARGE
    public double waveSpeed = 0.6;          // pengali kecepatan ombak
    public double windAngleDegrees = 35.0;  // arah angin utama
    public long seed = 1337L;               // samakan di semua pemain
    public double fullDepth = 8.0;          // kedalaman air agar ombak penuh (blok)

    // kapal
    public boolean boatTilt = true;
    public double boatFollowStrength = 1.0; // 0 = mati, 1 = normal
    public double boatSlide = 0.01;         // dorongan menuruni lereng ombak

    // pemain, item, pancing
    public boolean swimBobbing = true;
    public double waveLift = 0.5;           // pengangkatan rata-rata permukaan (x amplitudo)
    public boolean renderWaterMesh = true;  // permukaan air bergelombang (visual)
    public boolean meshWithShaders = false; // coba gambar mesh juga saat shader aktif
    public int meshRadius = 32;             // jari-jari mesh air (blok); kecil = ringan
    public int meshCell = 2;                // ukuran sel mesh (blok); besar = ringan
    public boolean itemBobbing = true;      // item jatuh + pelampung pancing ikut ombak

    // busa
    public boolean foam = true;
    public int foamSamples = 10;            // titik yang dicek per tick (turunkan jika berat)
    public double foamRadius = 28.0;
    public double foamIntensity = 1.0;

    // blok terapung
    public boolean floatingBlocks = true;
    public double blockSlide = 0.004;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static WaveConfig INSTANCE = new WaveConfig();

    public static WaveConfig get() { return INSTANCE; }

    public static void load() {
        Path p = FabricLoader.getInstance().getConfigDir().resolve("oceanphysics.json");
        try {
            if (Files.exists(p)) {
                WaveConfig c = GSON.fromJson(Files.readString(p), WaveConfig.class);
                if (c != null) INSTANCE = c;
            }
            Files.writeString(p, GSON.toJson(INSTANCE));
        } catch (Exception e) {
            OceanPhysicsMod.LOGGER.error("Gagal memuat config", e);
        }
        WaveMath.init(INSTANCE.seed);
    }
}
