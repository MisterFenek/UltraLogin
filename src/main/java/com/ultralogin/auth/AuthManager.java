package com.ultralogin.auth;

import com.mojang.logging.LogUtils;
import com.ultralogin.config.Messages;
import com.ultralogin.config.UltraLoginConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<UUID, PendingPlayer> pending = new ConcurrentHashMap<>();

    public boolean isAuthenticated(ServerPlayer player) {
        return !pending.containsKey(player.getUUID());
    }

    public PendingPlayer getPending(ServerPlayer player) {
        return pending.get(player.getUUID());
    }

    public void beginPreLogin(ServerPlayer player) {
        Inventory inv = player.getInventory();
        List<ItemStack> stash = new ArrayList<>(inv.getContainerSize());
        for (int i = 0; i < inv.getContainerSize(); i++) {
            stash.add(inv.getItem(i).copy());
        }
        inv.clearContent();
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();

        int timeoutTicks = UltraLoginConfig.AUTH_TIMEOUT_SECONDS.get() * 20;
        PendingPlayer state = new PendingPlayer(
                player.serverLevel().dimension(),
                player.position(),
                player.getYRot(),
                player.getXRot(),
                stash,
                player.getFoodData().getFoodLevel(),
                player.getFoodData().getSaturationLevel(),
                timeoutTicks);
        pending.put(player.getUUID(), state);

        player.setInvulnerable(true);
    }

    public void authenticate(ServerPlayer player) {
        PendingPlayer state = pending.remove(player.getUUID());
        if (state == null) {
            return;
        }
        restore(player, state);
        player.connection.teleport(state.returnPos.x, state.returnPos.y, state.returnPos.z, state.yaw, state.pitch);
        LOGGER.info("[UltraLogin] {} authenticated", player.getGameProfile().getName());
    }

    public void handleLogoutWithoutAuth(ServerPlayer player) {
        PendingPlayer state = pending.remove(player.getUUID());
        if (state == null || state.stashedInventory == null) {
            return;
        }
        restore(player, state);
        player.setPos(state.returnPos.x, state.returnPos.y, state.returnPos.z);
        player.setYRot(state.yaw);
        player.setXRot(state.pitch);
    }

    private void restore(ServerPlayer player, PendingPlayer state) {
        Inventory inv = player.getInventory();
        inv.clearContent();
        for (int i = 0; i < state.stashedInventory.size() && i < inv.getContainerSize(); i++) {
            inv.setItem(i, state.stashedInventory.get(i));
        }
        player.getFoodData().setFoodLevel(state.foodLevel);
        player.getFoodData().setSaturation(state.saturation);
        player.setInvulnerable(false);
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
    }

    public void tickPending(ServerPlayer player) {
        PendingPlayer state = pending.get(player.getUUID());
        if (state == null) {
            return;
        }

        player.getFoodData().setFoodLevel(20);
        player.setAirSupply(player.getMaxAirSupply());
        player.clearFire();
        player.fallDistance = 0.0F;

        if (state.ticksRemaining % 100 == 0) {
            player.sendSystemMessage(Messages.msg(state.registered ? "prelogin.need_login" : "prelogin.need_register"));
        }

        if (--state.ticksRemaining <= 0) {
            player.connection.disconnect(Messages.msg("prelogin.timeout_kick"));
        }
    }

    public void forget(UUID uuid) {
        pending.remove(uuid);
    }

    public void revokeAuthentication(ServerPlayer player) {
        PendingPlayer dummy = new PendingPlayer(
                player.serverLevel().dimension(),
                player.position(),
                player.getYRot(),
                player.getXRot(),
                null,
                player.getFoodData().getFoodLevel(),
                player.getFoodData().getSaturationLevel(),
                0);
        pending.put(player.getUUID(), dummy);
    }
}
