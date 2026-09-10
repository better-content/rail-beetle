package com.bettercontent.railbeetle.compat;

import com.Polarice3.Goety.utils.SEHelper;
import net.minecraft.world.entity.player.Player;

/** Loaded only after Forge confirms the pinned Goety API is present. */
public final class GoetySoulIntegration {
    private GoetySoulIntegration() {
    }

    public static int takeSouls(final Player player, final int limit) {
        final int moved = Math.min(limit, SEHelper.getSESouls(player));
        if (moved <= 0 || !SEHelper.decreaseSESouls(player, moved)) {
            return 0;
        }
        SEHelper.sendSEUpdatePacket(player);
        return moved;
    }
}
