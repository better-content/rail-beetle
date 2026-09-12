package com.bettercontent.railbeetle.api;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;
import java.util.UUID;
public final class BeetleWorkEvent extends Event {
    public enum Kind { ROUTE_FINISHED, MAGICAL_MOTION }
    public final ServerLevel level;
    public final UUID operator, beetle, operation;
    public final Kind kind;
    public final String engine;
    public BeetleWorkEvent(ServerLevel level, UUID operator, UUID beetle, UUID operation, Kind kind, String engine) {
        this.level=level; this.operator=operator; this.beetle=beetle; this.operation=operation; this.kind=kind; this.engine=engine;
    }
}
