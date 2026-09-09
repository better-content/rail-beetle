package com.bettercontent.railbeetle.upgrade;

import net.minecraftforge.fml.ModList;

public enum EngineKind {
    FIREBOX("firebox", "", 0, "work"),
    STEAM("steam_drive", "create", 144_000, "work"),
    FLUX("flux_traction_motor", "powergrid", 36_864_000, "FE"),
    SOURCE("source_impeller", "ars_nouveau", 144_000, "Source"),
    LIFEFORCE("lifeforce_ram", "bloodmagic", 144_000, "mB"),
    PRESSURE("pneumatic_expansion_motor", "pneumaticcraft", 720_000, "air"),
    SOUL("soul_combustor", "goety", 7_200, "soul"),
    SPIRIT("spirit_warping_engine", "malum", 24, "spirits");

    private final String id;
    private final String requiredMod;
    private final int capacity;
    private final String unit;

    EngineKind(String id, String requiredMod, int capacity, String unit) {
        this.id = id;
        this.requiredMod = requiredMod;
        this.capacity = capacity;
        this.unit = unit;
    }

    public String id() { return id; }
    public String requiredMod() { return requiredMod; }
    public int capacity() { return capacity; }
    public String unit() { return unit; }
    public boolean builtIn() { return this == FIREBOX; }
    public boolean available() { return requiredMod.isEmpty() || ModList.get().isLoaded(requiredMod); }

    public int nativeCost(int work) {
        if (work <= 0) return 0;
        return switch (this) {
            case FIREBOX, STEAM, SOURCE, LIFEFORCE -> work;
            case FLUX -> Math.multiplyExact(work, 256);
            case PRESSURE -> Math.multiplyExact(work, 5);
            case SOUL -> Math.max(1, (work + 19) / 20);
            case SPIRIT -> Math.max(1, (work + 5_999) / 6_000);
        };
    }
}
