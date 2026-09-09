package com.bettercontent.railbeetle.upgrade;

public enum ModuleKind {
    HIGH_SPEED_GOVERNOR_I("high_speed_governor_1", ModuleFamily.GOVERNOR, 1),
    HIGH_SPEED_GOVERNOR_II("high_speed_governor_2", ModuleFamily.GOVERNOR, 2),
    EXHAUST_RECUPERATOR_I("exhaust_recuperator_1", ModuleFamily.RECUPERATOR, 1),
    EXHAUST_RECUPERATOR_II("exhaust_recuperator_2", ModuleFamily.RECUPERATOR, 2),
    BRAKE_MANIFOLD_I("brake_manifold_1", ModuleFamily.BRAKES, 1),
    BRAKE_MANIFOLD_II("brake_manifold_2", ModuleFamily.BRAKES, 2),
    ADHESION_SANDERS_I("adhesion_sanders_1", ModuleFamily.ADHESION, 1),
    ADHESION_SANDERS_II("adhesion_sanders_2", ModuleFamily.ADHESION, 2),
    COMPOUND_TORQUE_CLUTCH_I("compound_torque_clutch_1", ModuleFamily.TORQUE, 1),
    COMPOUND_TORQUE_CLUTCH_II("compound_torque_clutch_2", ModuleFamily.TORQUE, 2),
    REINFORCED_DRAWGEAR_I("reinforced_drawgear_1", ModuleFamily.DRAWGEAR, 1),
    REINFORCED_DRAWGEAR_II("reinforced_drawgear_2", ModuleFamily.DRAWGEAR, 2),
    TELESCOPIC_SURVEY_ARRAY_I("telescopic_survey_array_1", ModuleFamily.SURVEY, 1),
    TELESCOPIC_SURVEY_ARRAY_II("telescopic_survey_array_2", ModuleFamily.SURVEY, 2),
    DISPATCH_RECEIVER_I("dispatch_receiver_1", ModuleFamily.REMOTE, 1),
    DISPATCH_RECEIVER_II("dispatch_receiver_2", ModuleFamily.REMOTE, 2),
    TRESTLE_ERECTOR_I("trestle_erector_1", ModuleFamily.TRESTLE, 1),
    TRESTLE_ERECTOR_II("trestle_erector_2", ModuleFamily.TRESTLE, 2),
    CAGED_SEARCHLIGHT("caged_searchlight", ModuleFamily.SEARCHLIGHT, 1);

    private final String id;
    private final ModuleFamily family;
    private final int tier;

    ModuleKind(String id, ModuleFamily family, int tier) {
        this.id = id;
        this.family = family;
        this.tier = tier;
    }

    public String id() { return id; }
    public ModuleFamily family() { return family; }
    public int tier() { return tier; }
}
