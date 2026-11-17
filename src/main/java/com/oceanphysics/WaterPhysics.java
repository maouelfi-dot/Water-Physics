package com.oceanphysics;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
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

    /** Item di air: yang berat tenggelam dengan hambatan (kecepatan terminal), yang ringan mengapung di garis air. */
    public static void item(ItemEntity e) {
        WaveConfig cfg = WaveConfig.get();
        World w = e.getWorld();
        if (w.isClient() || !cfg.enabled || !cfg.itemBobbing || !e.isTouchingWater()) return;

        double surface = WaveMath.findSurface(w, e.getX(), e.getY(), e.getZ());
        if (Double.isNaN(surface)) return;
        Vec3d v = e.getVelocity();

        if (cfg.itemSinking && isHeavy(e.getStack().getItem())) {
            double vy = Math.max(v.y - 0.011, -0.09);
            e.setVelocity(v.x * 0.97, vy, v.z * 0.97);
            e.velocityModified = true;
            return;
        }
        double h = WaveMath.height(w, e.getX(), surface, e.getZ(), 0f);
        double dy = (surface + h - 0.1) - e.getY(); // setimbang: sedikit di bawah garis air
        if (dy < -0.8 || dy > 1.5) return;
        double[] s = WaveMath.slope(w, e.getX(), surface, e.getZ(), 0f);
        double vy = v.y + (dy * 0.2 - v.y) * 0.35;
        e.setVelocity(v.x - s[0] * 0.005 * cfg.swellStrength, vy, v.z - s[1] * 0.005 * cfg.swellStrength);
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
