package com.bettercontent.railbeetle.menu;

import com.bettercontent.railbeetle.RailBeetleRegistries;
import com.bettercontent.railbeetle.entity.RailBeetleEntity;
import com.bettercontent.railbeetle.entity.BeetleMode;
import com.bettercontent.railbeetle.entity.BeetleSpeed;
import com.bettercontent.railbeetle.upgrade.BeetleProfile;
import com.bettercontent.railbeetle.upgrade.EngineKind;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class RemoteBeetleMenu extends AbstractContainerMenu {
    private final RailBeetleEntity beetle;
    private final int entityId;
    private final EngineKind snapshotEngine;
    private final int snapshotResource;
    private final int snapshotFallback;
    private final BeetleMode snapshotMode;
    private final BeetleSpeed snapshotSpeed;
    private final BeetleProfile snapshotProfile;
    private final boolean snapshotSearchlight;

    public static RemoteBeetleMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        Entity entity = inventory.player.level().getEntity(entityId);
        RailBeetleEntity beetle = entity instanceof RailBeetleEntity value ? value : null;
        EngineKind engine = buffer.readEnum(EngineKind.class);
        int resource = buffer.readVarInt();
        int fallback = buffer.readVarInt();
        BeetleMode mode = buffer.readEnum(BeetleMode.class);
        BeetleSpeed speed = buffer.readEnum(BeetleSpeed.class);
        long moduleMask = buffer.readLong();
        boolean searchlight = buffer.readBoolean();
        return new RemoteBeetleMenu(id, beetle, entityId, engine, resource, fallback, mode, speed,
                RailBeetleEntity.profileForMask(moduleMask), searchlight);
    }

    public RemoteBeetleMenu(int id, Inventory inventory, RailBeetleEntity beetle) {
        this(id, beetle, beetle.getId(), beetle.engineKind(), beetle.engineResource(), beetle.fallbackFuel(), beetle.mode(),
                beetle.speedTier(), beetle.profile(), beetle.searchlightOn());
    }

    private RemoteBeetleMenu(int id, RailBeetleEntity beetle, int entityId, EngineKind engine,
                             int resource, int fallback, BeetleMode mode, BeetleSpeed speed, BeetleProfile profile,
                             boolean searchlight) {
        super(RailBeetleRegistries.REMOTE_BEETLE_MENU.get(), id);
        this.beetle = beetle;
        this.entityId = entityId;
        this.snapshotEngine = engine;
        this.snapshotResource = resource;
        this.snapshotFallback = fallback;
        this.snapshotMode = mode;
        this.snapshotSpeed = speed;
        this.snapshotProfile = profile;
        this.snapshotSearchlight = searchlight;
    }

    public RailBeetleEntity beetle() { return beetle; }
    public int entityId() { return entityId; }
    public EngineKind engineKind() { return beetle == null ? snapshotEngine : beetle.engineKind(); }
    public int engineResource() { return beetle == null ? snapshotResource : beetle.engineResource(); }
    public int fallbackFuel() { return beetle == null ? snapshotFallback : beetle.fallbackFuel(); }
    public BeetleMode mode() { return beetle == null ? snapshotMode : beetle.mode(); }
    public BeetleSpeed speedTier() { return beetle == null ? snapshotSpeed : beetle.speedTier(); }
    public BeetleProfile profile() { return beetle == null ? snapshotProfile : beetle.profile(); }
    public boolean searchlightOn() { return beetle == null ? snapshotSearchlight : beetle.searchlightOn(); }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return beetle == null || beetle.canRemoteControl(player); }
}
