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
        List<ItemStack> stash = null;
        if (UltraLoginConfig.SANDBOX_HIDE_INVENTORY.get()) {
            Inventory inv = player.getInventory();
            stash = new ArrayList<>(inv.getContainerSize());
            for (int i = 0; i < inv.getContainerSize(); i++) {
                stash.add(inv.getItem(i).copy());
            }
            inv.clearContent();
            player.containerMenu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();
        }

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

        if (UltraLoginConfig.SANDBOX_GODMODE.get()) {
            player.setInvulnerable(true);
        }
    }

    public void authenticate(ServerPlayer player) {
        PendingPlayer state = pending.remove(player.getUUID());
        if (state == null) {
            return;
        }
        restore(player, state);
        if (UltraLoginConfig.SANDBOX_FREEZE_MOVEMENT.get() || UltraLoginConfig.SANDBOX_FREEZE_ROTATION.get()) {
            player.connection.teleport(state.returnPos.x, state.returnPos.y, state.returnPos.z, state.yaw, state.pitch);
        }
        LOGGER.info("[UltraLogin] {} authenticated", player.getGameProfile().getName());
    }

    public void handleLogoutWithoutAuth(ServerPlayer player) {
        PendingPlayer state = pending.remove(player.getUUID());
        if (state == null) {
            return;
        }
        restore(player, state);
        
        if (UltraLoginConfig.SANDBOX_FREEZE_MOVEMENT.get() || UltraLoginConfig.SANDBOX_FREEZE_ROTATION.get()) {
            player.setPos(state.returnPos.x, state.returnPos.y, state.returnPos.z);
            player.setYRot(state.yaw);
            player.setXRot(state.pitch);
        }
    }

    private void restore(ServerPlayer player, PendingPlayer state) {
        if (state.stashedInventory != null) {
            Inventory inv = player.getInventory();
            
            // Collect any items acquired while unauthenticated (e.g. starter kits)
            List<ItemStack> newItems = new ArrayList<>();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty()) {
                    newItems.add(stack.copy());
                }
            }
            
            inv.clearContent();
            
            // Restore original stashed inventory
            for (int i = 0; i < state.stashedInventory.size() && i < inv.getContainerSize(); i++) {
                inv.setItem(i, state.stashedInventory.get(i));
            }
            
            // Give back the items acquired during unauth
            for (ItemStack newItem : newItems) {
                if (!player.getInventory().add(newItem)) {
                    player.drop(newItem, false);
                }
            }
        }
        
        player.getFoodData().setFoodLevel(state.foodLevel);
        player.getFoodData().setSaturation(state.saturation);
        player.setInvulnerable(false); // Clear godmode
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
        revokeAuthenticationByUuid(
                player.getUUID(),
                player.serverLevel().dimension(),
                player.position(),
                player.getYRot(),
                player.getXRot(),
                player.getFoodData().getFoodLevel(),
                player.getFoodData().getSaturationLevel());
    }

    void revokeAuthenticationByUuid(UUID uuid, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim,
                                    Vec3 pos, float yaw, float pitch, int food, float sat) {
        PendingPlayer existing = pending.get(uuid);
        if (existing != null) {
            return; // Already unauthenticated, leave the existing state (and stash) intact
        }
        PendingPlayer dummy = new PendingPlayer(
                dim,
                pos,
                yaw,
                pitch,
                null,
                food,
                sat,
                0);
        pending.put(uuid, dummy);
    }
}
