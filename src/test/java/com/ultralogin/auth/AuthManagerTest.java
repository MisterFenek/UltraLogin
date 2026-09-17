package com.ultralogin.auth;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;

class AuthManagerTest {

    @Test
    @SuppressWarnings("unchecked")
    void testRevokeAuthenticationLogic() throws Exception {
        AuthManager authManager = new AuthManager();
        UUID playerUuid = UUID.randomUUID();

        Field pendingField = AuthManager.class.getDeclaredField("pending");
        pendingField.setAccessible(true);
        Map<UUID, PendingPlayer> pendingMap = (Map<UUID, PendingPlayer>) pendingField.get(authManager);

        List<net.minecraft.world.item.ItemStack> realStash = new ArrayList<>();
        PendingPlayer existingPending = new PendingPlayer(
                null, null, 0, 0, realStash, 20, 5.0f, 600
        );
        pendingMap.put(playerUuid, existingPending);

        // We call the package-private testing method that contains the fix logic
        authManager.revokeAuthenticationByUuid(playerUuid, null, null, 0.0f, 0.0f, 20, 5.0f);

        PendingPlayer afterRevoke = pendingMap.get(playerUuid);
        assertNotNull(afterRevoke);
        assertSame(realStash, afterRevoke.stashedInventory, "stashedInventory must not be overwritten if already unauthenticated");
    }
}
