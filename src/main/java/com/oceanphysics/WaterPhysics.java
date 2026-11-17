package com.oceanphysics;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;

/** Fisika air nyata (server + gameplay): daya apung, tenggelam, hambatan, hanyut oleh ombak, gelombang kejut percikan. */
public final class WaterPhysics {
    private static final String[] HEAVY = {
            "ingot", "nugget", "_ore", "raw_", "diamond", "emerald", "netherite", "iron", "gold", "copper",
            "stone", "deepslate", "cobble", "brick", "anvil", "obsidian", "coal", "lapis", "redstone", "quartz",
            "sword", "pickaxe", "_axe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots",
            "sand", "gravel", "dirt", "bucket", "shield", "trident", "_block", "amethyst", "flint", "clay", "glass"
    };
    private static final HashMap<Item, Boolean> HEAVY_CACHE = new HashMap<>();

    private WaterPhysics() {}

    public static boolean isHeavy(Item item) {
        Boolean c = HEAVY_CACHE.get(item);
        if (c != null) return c;
        String p = Registries.ITEM.getId(item).getPath();
        boolean heavy = false;
        // kayu, daun, wol, makanan, dll. mengapung walau namanya mengandung kata di atas
        boolean light = p.contains("log") || p.contains("planks") || p.contains("wood") || p.contains("leaves")
                || p.contains("wool") || p.contains("boat") || p.contains("sapling") || p.contains("stick")
                || p.contains("bamboo") || p.contains("sponge") || p.contains("carpet") || p.contains("slab") && p.contains("plank");
        if (!light) {
            for (String k : HEAVY) {
                if (p.contains(k)) { heavy = true; break; }
            }
        }
        HEAVY_CACHE.put(item, heavy);
        return heavy;
    }

    /**
     * Item di air (server): yang berat tenggelam dengan hambatan, yang ringan mengapung di garis AIR OMBAK.
     * Keduanya diseret arus ombak (kecepatan partikel air), termasuk saat berada di dalam badan ombak
     * yang sudah di atas permukaan air vanila.
     */
    public static void item(ItemEntity e) {
        WaveConfig cfg = WaveConfig.get();
        World w = e.getWorld();
        if (w.isClient() || !cfg.enabled || !cfg.itemBobbing) return;
        if (!e.isTouchingWater() && (!cfg.extendedWet || (e.age & 1) != 0)) return;

        WaterForces.Sample s = WaterForces.sample(w, e.getX(), e.getY() + 0.1, e.getZ());
        if (!WaterForces.wet(e, s)) return;
        Vec3d v = e.getVelocity();
        double sub = Math.max(0.4, WaterForces.submerged(e, s));

        if (cfg.itemSinking && isHeavy(e.getStack().getItem())) {
            double vy = Math.max(v.y - 0.011, -0.09);
            e.setVelocity(v.x * 0.97 + s.ux * 0.04 * sub, vy, v.z * 0.97 + s.uz * 0.04 * sub);
            e.velocityModified = true;
            return;
        }
        double dy = (s.surfaceY - 0.1) - e.getY(); // setimbang: sedikit di bawah garis air
        if (dy < -0.8 || dy > 1.5 + s.h) {
            WaterForces.drag(e, s, 0.05 * sub);
            return;
        }
        double vy = v.y + (dy * 0.2 + s.uy - v.y) * 0.35;
        e.setVelocity(v.x + (s.ux - v.x) * 0.06 * sub, vy, v.z + (s.uz - v.z) * 0.06 * sub);
        e.velocityModified = true;
    }

    /**
     * Benda umum (server): blok jatuh, kereta, TNT.
     * floats=false: tenggelam dengan hambatan (kecepatan terminal); floats=true: mengapung setengah terendam.
     */
    public static void body(Entity e, boolean floats, double dragXZ, double dragY, double terminal) {
        WaveConfig cfg = WaveConfig.get();
        World w = e.getWorld();
        if (w.isClient() || !cfg.enabled) return;
        if (!e.isTouchingWater() && (!cfg.extendedWet || (e.age & 1) != 0)) return;

        WaterForces.Sample s = WaterForces.sample(w, e.getX(), e.getY() + e.getHeight() * 0.5, e.getZ());
        if (!WaterForces.wet(e, s)) return;
        Vec3d v = e.getVelocity();
        double sub = Math.max(0.3, WaterForces.submerged(e, s));
        if (floats) {
            double dy = (s.surfaceY - e.getHeight() * 0.55) - e.getY();
            if (dy < -1.0 || dy > 1.5 + s.h) {
                WaterForces.drag(e, s, 0.05 * sub);
                return;
            }
            double vy = v.y + (dy * 0.2 + s.uy - v.y) * 0.3;
            e.setVelocity(v.x * 0.97 + s.ux * 0.04 * sub, vy, v.z * 0.97 + s.uz * 0.04 * sub);
        } else {
            e.setVelocity(v.x * dragXZ + s.ux * (1.0 - dragXZ) * sub, Math.max(v.y * dragY, terminal),
                    v.z * dragXZ + s.uz * (1.0 - dragXZ) * sub);
        }
        e.velocityModified = true;
    }

    /**
     * Mob (server): ikut naik-turun dengan permukaan ombak, didorong arus ombak, dan tidak jatuh menembus badan
     * ombak. Hanya mob yang dekat permukaan (ikan yang dalam tidak diganggu).
     */
    public static void mob(LivingEntity e) {
        WaveConfig cfg = WaveConfig.get();
        World w = e.getWorld();
        if (w.isClient() || !cfg.enabled || !cfg.mobBobbing || e.hasVehicle()) return;
        boolean touching = e.isTouchingWater();
        if (!touching && (!cfg.extendedWet || (e.age & 3) != 0)) return;

        WaterForces.Sample s = WaterForces.sample(w, e.getX(), e.getY() + e.getHeight() * 0.5, e.getZ());
        if (!WaterForces.wet(e, s)) return;
        double rel = s.surfaceY - e.getY(); // kaki di bawah puncak ombak
        if (rel > 1.8 + e.getHeight()) return;
        double sub = Math.max(0.0, Math.min(1.0, rel / Math.max(0.3, e.getHeight())));
        double k = cfg.swellStrength;
        Vec3d v = e.getVelocity();
        double vy = v.y + (s.uy - v.y) * 0.12 * k * sub;
        if (!touching) vy += 0.04 * sub; // di dalam badan ombak tapi di atas air vanila: tahan gravitasi
        double vx = v.x + s.ux * 0.06 * k * sub;
        double vz = v.z + s.uz * 0.06 * k * sub;
        e.setVelocity(vx, vy, vz);
        e.velocityModified = true;
    }

    /**
     * Gelombang kejut: benda yang jatuh ke air mendorong benda terapung di sekitarnya
     * (pindah momentum, berkurang menurut jarak kuadrat).
     */
    public static void shock(World w, Entity self, double energy) {
        WaveConfig cfg = WaveConfig.get();
        if (w.isClient() || !cfg.enabled || !cfg.splashShock || energy < 0.3) return;
        energy = Math.min(1.2, energy);
        double r = 2.0 + energy * 2.5;
        Box b = new Box(self.getX() - r, self.getY() - 1.5, self.getZ() - r, self.getX() + r, self.getY() + 1.5, self.getZ() + r);
        int n = 0;
        for (Entity o : w.getEntitiesByClass(Entity.class, b, o -> o != self && o.isTouchingWater() && !(o instanceof PlayerEntity))) {
            if (++n > 24) break;
            double dx = o.getX() - self.getX(), dz = o.getZ() - self.getZ();
            double d2 = dx * dx + dz * dz;
            double d = Math.sqrt(d2);
            if (d < 0.25) { dx = 1; dz = 0; d = 1; d2 = 1; }
            double f = energy * 0.22 / (1.0 + d2);
            Vec3d v = o.getVelocity();
            o.setVelocity(v.x + dx / d * f, v.y + energy * 0.04 / (1.0 + d2), v.z + dz / d * f);
            o.velocityModified = true;
        }
    }
}
