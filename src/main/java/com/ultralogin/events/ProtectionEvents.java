package com.ultralogin.events;

import com.ultralogin.UltraLogin;
import com.ultralogin.auth.PendingPlayer;
import com.ultralogin.config.Messages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Locale;
import java.util.Set;

public final class ProtectionEvents {

    private static final Set<String> ALLOWED_PRE_LOGIN_COMMANDS =
            Set.of("login", "l", "register", "reg", "recovery", "ultralogin", "ulogin", "ul");

    private static boolean locked(Player player) {
        return player instanceof ServerPlayer sp && !UltraLogin.auth().isAuthenticated(sp);
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || UltraLogin.auth().isAuthenticated(player)) {
            return;
        }
        PendingPlayer state = UltraLogin.auth().getPending(player);
        if (state != null) {
            double dx = player.getX() - state.returnPos.x;
            double dy = player.getY() - state.returnPos.y;
            double dz = player.getZ() - state.returnPos.z;
            if (dx * dx + dy * dy + dz * dz > 0.04) {
                player.connection.teleport(state.returnPos.x, state.returnPos.y, state.returnPos.z,
                        state.yaw, state.pitch);
            }
            UltraLogin.auth().tickPending(player);
        }
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        if (locked(event.getPlayer())) {
            event.setCanceled(true);
            event.getPlayer().sendSystemMessage(Messages.msg("prelogin.blocked_chat"));
        }
    }

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        if (!(event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player)
                || UltraLogin.auth().isAuthenticated(player)) {
            return;
        }
        String input = event.getParseResults().getReader().getString();
        String root = input.startsWith("/") ? input.substring(1) : input;
        int space = root.indexOf(' ');
        if (space >= 0) {
            root = root.substring(0, space);
        }
        if (!ALLOWED_PRE_LOGIN_COMMANDS.contains(root.toLowerCase(Locale.ROOT))) {
            event.setCanceled(true);
            player.sendSystemMessage(Messages.msg("prelogin.blocked_command"));
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (locked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (locked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (locked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (locked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (locked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() != null && locked(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (locked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onUseItem(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player player && locked(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player player && locked(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        if (locked(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (locked(event.getPlayer())) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player && locked(player)) {
            player.closeContainer();
            player.sendSystemMessage(Messages.msg("prelogin.blocked_action"));
        }
    }
}
