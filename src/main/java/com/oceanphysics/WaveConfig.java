package com.oceanphysics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class WaveConfig {
    public enum Size {
        SMALL(0.2), MEDIUM(0.45), LARGE(0.9);
        public final double amplitude;
        Size(double a) { this.amplitude = a; }
    }

    public int configVersion = 2;

    public boolean enabled = true;
    public Size waveSize = Size.MEDIUM;     // SMALL / MEDIUM / LARGE (tinggi ombak maksimum)
    public double waveSpeed = 0.8;          // pengali kecepatan ombak
    public double windAngleDegrees = 35.0;  // arah angin utama
    public long seed = 1337L;               // samakan di server dan semua pemain
    public double fullDepth = 6.0;          // kedalaman air agar ombak penuh (blok)

    // kapal
    public boolean boatTilt = true;
    public double boatFollowStrength = 1.0; // 0 = mati, 1 = normal
    public double boatSlide = 0.012;        // dorongan menuruni lereng ombak

    // pemain, mob, item, pancing
    public boolean swimBobbing = true;
    public boolean mobBobbing = true;
    public boolean itemBobbing = true;
    public boolean itemPhysics = true;      // item tergeletak rata di tanah, berputar di udara, memantul
    public boolean itemBounce = true;       // item memantul saat jatuh keras
    public boolean sneakPickup = true;      // item hanya bisa diambil saat pemain jongkok (Sneak) di dekatnya
    public boolean itemSinking = true;      // item berat (besi, batu, dll.) tenggelam, item ringan (kayu) mengapung
    public boolean fallingBlockDrag = true; // pasir/kerikil melambat di air
    public boolean splashShock = true;      // benda jatuh ke air mendorong benda terapung di sekitarnya
    public boolean playerFloat = true;      // pemain mengapung di permukaan saat diam (tahan Sneak untuk turun)
    public double swellStrength = 1.0;      // pengali gaya ombak pada pemain/mob/item

    // permukaan air (visual, client)
    public boolean renderWaterMesh = true;
    public boolean meshWithShaders = true;  // gambar juga saat shader Iris aktif
    public int meshRadius = 20;             // jari-jari (blok); kecil = ringan
    public int meshCell = 1;                // ukuran sel (blok); 2 = jauh lebih ringan
    public int meshAlpha = 140;             // 0..255 transparansi lapisan air

    // riak (simulasi persamaan gelombang 2D, client)
    public boolean ripples = true;
    public double rippleStrength = 1.0;     // kekuatan riak dari gerakan
    public double rippleSpeed = 0.28;       // 0.05..0.45 (kecepatan rambat)
    public double rippleDamping = 0.985;    // 0.95..0.995 (makin kecil makin cepat hilang)
    public boolean rainRipples = true;

    // busa
    public boolean foam = true;
    public int foamSamples = 6;
    public double foamRadius = 24.0;
    public double foamIntensity = 1.0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static WaveConfig INSTANCE = new WaveConfig();

    public static WaveConfig get() { return INSTANCE; }

    public static void load() {
        Path p = FabricLoader.getInstance().getConfigDir().resolve("oceanphysics.json");
        try {
            if (Files.exists(p)) {
                WaveConfig c = GSON.fromJson(Files.readString(p), WaveConfig.class);
                // config lama (versi < 2) dibuang supaya default baru berlaku
                if (c != null && c.configVersion >= 2) INSTANCE = c;
            }
            Files.writeString(p, GSON.toJson(INSTANCE));
        } catch (Exception e) {
            OceanPhysicsMod.LOGGER.error("Gagal memuat config", e);
        }
        INSTANCE.meshCell = Math.max(1, Math.min(4, INSTANCE.meshCell));
        INSTANCE.meshRadius = Math.max(8, Math.min(60, INSTANCE.meshRadius));
        INSTANCE.rippleSpeed = Math.max(0.05, Math.min(0.45, INSTANCE.rippleSpeed));
        INSTANCE.rippleDamping = Math.max(0.9, Math.min(0.998, INSTANCE.rippleDamping));
        WaveMath.init(INSTANCE.seed);
    }
}
