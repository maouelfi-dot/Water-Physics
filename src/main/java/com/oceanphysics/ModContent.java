package com.oceanphysics;

import com.oceanphysics.entity.FloatingBlockEntity;
import com.oceanphysics.item.FloatWandItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModContent {
    public static final EntityType<FloatingBlockEntity> FLOATING_BLOCK = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(OceanPhysicsMod.MOD_ID, "floating_block"),
            EntityType.Builder.<FloatingBlockEntity>create(FloatingBlockEntity::new, SpawnGroup.MISC)
                    .dimensions(1.0f, 1.0f)
                    .maxTrackingRange(10)
                    .trackingTickInterval(1)
                    .build("floating_block"));

    public static final Item FLOAT_WAND = Registry.register(
            Registries.ITEM,
            Identifier.of(OceanPhysicsMod.MOD_ID, "float_wand"),
            new FloatWandItem(new Item.Settings().maxCount(1)));

    private ModContent() {}

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> e.add(FLOAT_WAND));
    }
}
