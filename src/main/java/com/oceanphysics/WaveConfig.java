package com.oceanphysics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class WaveConfig {
    public enum Size {
        SMALL(0.4), MEDIUM(0.9), LARGE(1.5);
        public final double amplitude;
        Size(double a) { this.amplitude = a; }
    }

    public int configVersion = 7;

    public boolean enabled = true;
    public Size waveSize = Size.MEDIUM;     // SMALL / MEDIUM / LARGE (tinggi ombak maksimum)
    public double waveSpeed = 0.8;          // pengali kecepatan ombak
    public double windAngleDegrees = 35.0;  // arah angin utama
    public long seed = 1337L;               // samakan di server dan semua pemain
    public double fullDepth = 3.0;          // kedalaman air agar ombak penuh (blok)

    public double chop = 1.2;               // 0 = ombak bulat, makin besar puncak makin tajam (ombak pecah)
    public double minDepthFactor = 0.3;    // ombak minimum di air dangkal/kolam (0..1)

    // badai: hujan/petir membuat ombak makin tinggi, pendek-pendek, dan galak (server + client, dari cuaca dunia)
    public boolean stormWaves = true;
    public double stormWaveBoost = 2.0;     // tinggi ombak x(1 + boost) saat badai petir penuh
    public double stormChoppy = 2.0;        // ombak pendek/kasar makin kuat saat badai

    // arus ombak nyata: benda ikut kecepatan partikel air (orbital + drift Stokes), bukan sekadar naik-turun
    public boolean waterCurrent = true;
    public double currentStrength = 1.0;    // pengali arus ombak untuk semua benda
    public boolean shoreSurge = true;       // ombak menghempas ke pantai lalu menarik kembali (undertow)
    public double surgeStrength = 1.0;
    public double stokesDrift = 1.0;        // hanyut bersih searah ombak
    public boolean extendedWet = true;      // benda di dalam badan ombak (di atas air vanila) tetap dianggap di air

    // kapal: badan kaku pegas-peredam (pitch/roll) + naik-turun dari 5 titik
    public boolean boatRig = true;
    public double boatRockStiffness = 0.016; // kekakuan pegas (per tick^2)
    public double boatRockDamping = 0.07;    // redaman (makin kecil makin lama bergoyang)

    // ombak menyapu blok rapuh di tepi air (obor, bunga, tebu, redstone, dll.) saat badai
    public boolean washAway = true;
    public double washStormMin = 0.55;      // level badai minimum (0..1)
    public int washPerSecond = 6;           // sampel acak per detik per pemain (kecil = ringan)

    // pohon roboh saat ditebang
    public boolean treeFall = true;
    public int treeMaxLogs = 160;
    public int treeMaxLeaves = 70;

    // visual badai/matahari
    public boolean glare = true;            // pantulan matahari/bulan di air (silau)
    public double glareStrength = 1.0;
    public boolean stormTint = true;        // air jadi gelap kelabu saat badai
    public boolean spray = true;            // semprotan di puncak ombak saat badai
    public int spraySamples = 4;

    // fisika air total (server): ganti logika aliran vanila, air bermassa terbatas
    public boolean realFluid = true;
    public int realFluidPerTick = 500;      // maks sel air diproses per tick (kecil = ringan, air lebih lambat)

    // aliran air nyata (server): air vanilla mengalir lebih cepat dan lebih jauh
    public boolean fastFlow = true;
    public int flowTickRate = 2;            // vanilla 5; makin kecil makin cepat mengalir/tumpah
    public int flowDistance = 8;            // vanilla 4; jarak air mencari lubang/turunan

    // air tumpah (simulasi cairan partikel, client): ember air -> genangan yang mengalir
    public boolean spill = true;
    public boolean spillAlways = false;     // false = hanya saat Sneak + klik kanan ember air; true = selalu tumpah
    public int spillMax = 450;              // maks partikel (kecil = ringan)
    public int spillPerBucket = 260;        // partikel per ember
    public int spillLifeSeconds = 18;       // lama genangan sebelum meresap

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
    public int meshRadius = 24;             // jari-jari (blok); kecil = ringan
    public int meshCell = 1;                // ukuran sel (blok); 2 = jauh lebih ringan
    public int meshAlpha = 235;             // 0..255 transparansi lapisan air

    // riak (simulasi persamaan gelombang 2D, client)
    public boolean ripples = true;
    public double rippleStrength = 1.5;     // kekuatan riak dari gerakan
    public double rippleSpeed = 0.28;       // 0.05..0.45 (kecepatan rambat)
    public double rippleDamping = 0.99;    // 0.95..0.995 (makin kecil makin cepat hilang)
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
                if (c != null && c.configVersion >= 6) { INSTANCE = c; INSTANCE.configVersion = 7; }
            }
            Files.writeString(p, GSON.toJson(INSTANCE));
        } catch (Exception e) {
            OceanPhysicsMod.LOGGER.error("Gagal memuat config", e);
        }
        INSTANCE.meshCell = Math.max(1, Math.min(4, INSTANCE.meshCell));
        INSTANCE.meshRadius = Math.max(8, Math.min(60, INSTANCE.meshRadius));
        INSTANCE.flowTickRate = Math.max(1, Math.min(20, INSTANCE.flowTickRate));
        INSTANCE.flowDistance = Math.max(1, Math.min(12, INSTANCE.flowDistance));
        INSTANCE.minDepthFactor = Math.max(0.0, Math.min(1.0, INSTANCE.minDepthFactor));
        INSTANCE.rippleSpeed = Math.max(0.05, Math.min(0.45, INSTANCE.rippleSpeed));
        INSTANCE.rippleDamping = Math.max(0.9, Math.min(0.998, INSTANCE.rippleDamping));
        INSTANCE.stormWaveBoost = Math.max(0.0, Math.min(5.0, INSTANCE.stormWaveBoost));
        INSTANCE.stormChoppy = Math.max(0.0, Math.min(6.0, INSTANCE.stormChoppy));
        INSTANCE.currentStrength = Math.max(0.0, Math.min(6.0, INSTANCE.currentStrength));
        INSTANCE.surgeStrength = Math.max(0.0, Math.min(6.0, INSTANCE.surgeStrength));
        INSTANCE.boatRockStiffness = Math.max(0.002, Math.min(0.06, INSTANCE.boatRockStiffness));
        INSTANCE.boatRockDamping = Math.max(0.01, Math.min(0.3, INSTANCE.boatRockDamping));
        INSTANCE.washPerSecond = Math.max(0, Math.min(40, INSTANCE.washPerSecond));
        INSTANCE.spraySamples = Math.max(0, Math.min(16, INSTANCE.spraySamples));
        WaveMath.init(INSTANCE.seed);
    }
}
