package com.ultralogin.auth;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class PendingPlayer {

    public final ResourceKey<Level> dimension;
    public final Vec3 returnPos;
    public final float yaw;
    public final float pitch;
    public final List<ItemStack> stashedInventory;
    public final int foodLevel;
    public final float saturation;

    public int ticksRemaining;
    public boolean registered;

    public PendingPlayer(ResourceKey<Level> dimension, Vec3 returnPos, float yaw, float pitch,
                         List<ItemStack> stashedInventory, int foodLevel, float saturation,
                         int ticksRemaining) {
        this.dimension = dimension;
        this.returnPos = returnPos;
        this.yaw = yaw;
        this.pitch = pitch;
        this.stashedInventory = stashedInventory;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.ticksRemaining = ticksRemaining;
    }
}
