package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, FrozenDawn.MOD_ID);

    private static DeferredHolder<SoundEvent, SoundEvent> register(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, id)));
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> WIND_LIGHT = SOUNDS.register("ambient.wind_light",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.wind_light")));

    public static final DeferredHolder<SoundEvent, SoundEvent> WIND_STRONG = SOUNDS.register("ambient.wind_strong",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.wind_strong")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SHELTER_CREAK = SOUNDS.register("ambient.shelter_creak",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.shelter_creak")));

    public static final DeferredHolder<SoundEvent, SoundEvent> FLAG_FLUTTER = SOUNDS.register("ambient.flag_flutter",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.flag_flutter")));

    public static final DeferredHolder<SoundEvent, SoundEvent> ALARM_BEACON = SOUNDS.register("blocks.alarm_beacon",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "blocks.alarm_beacon")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TOWN_PA_CLEAR = SOUNDS.register("blocks.town_pa_clear",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "blocks.town_pa_clear")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TOWN_PA_DEGRADED = SOUNDS.register("blocks.town_pa_degraded",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "blocks.town_pa_degraded")));

    public static final DeferredHolder<SoundEvent, SoundEvent> EVA_BREATHING = SOUNDS.register("ambient.eva_breathing",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.eva_breathing")));

    public static final DeferredHolder<SoundEvent, SoundEvent> EVA_SUFFOCATE = SOUNDS.register("ambient.eva_suffocate",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.eva_suffocate")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SUIT_PUNCTURE_WARNING =
            register("ui.suit.puncture_warning");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUIT_OXYGEN_CRITICAL =
            register("ui.suit.oxygen_critical");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUIT_OXYGEN_BEEP =
            register("ui.suit.oxygen_beep");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUIT_LEAK_HISS =
            register("ui.suit.leak_hiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUIT_EMERGENCY_RESERVE =
            register("ui.suit.emergency_reserve");

    public static final DeferredHolder<SoundEvent, SoundEvent> SANITY_WHISPER = SOUNDS.register("ambient.sanity_whisper",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.sanity_whisper")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SANITY_FOOTSTEP = SOUNDS.register("ambient.sanity_footstep",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.sanity_footstep")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SANITY_THUD = SOUNDS.register("ambient.sanity_thud",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.sanity_thud")));

    public static final DeferredHolder<SoundEvent, SoundEvent> GEOTHERMAL_TREMOR = SOUNDS.register("ambient.geothermal_tremor",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.geothermal_tremor")));

    public static final DeferredHolder<SoundEvent, SoundEvent> GEOTHERMAL_QUAKE = SOUNDS.register("ambient.geothermal_quake",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.geothermal_quake")));

    // --- Menu Music ---
    public static final DeferredHolder<SoundEvent, SoundEvent> MENU_MUSIC = SOUNDS.register("music.menu",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "music.menu")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_MUSIC_GHOST = register(
            "music.master_architect.ghost");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_MUSIC_TETHERS = register(
            "music.master_architect.tethers");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_MUSIC_LAST_WALL = register(
            "music.master_architect.last_wall");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_MUSIC_MIND = register(
            "music.master_architect.mind");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_MUSIC_ORREN = register(
            "music.master_architect.orren");
    public static final DeferredHolder<SoundEvent, SoundEvent> THAE_IVEN_HEART_MUSIC = register(
            "music.thae_iven_heart");

    // Curated melancholy gameplay music.
    public static final DeferredHolder<SoundEvent, SoundEvent> SAD_MOOG_CITY_2 = register("music.sad.moog_city_2");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAD_MUTATION = register("music.sad.mutation");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAD_DREITON = register("music.sad.dreiton");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAD_HAUNT_MUSKIE = register("music.sad.haunt_muskie");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAD_TASWELL = register("music.sad.taswell");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAD_CLARK = register("music.sad.clark");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAD_DRY_HANDS = register("music.sad.dry_hands");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAD_LIVING_MICE = register("music.sad.living_mice");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAD_MICE_ON_VENUS = register("music.sad.mice_on_venus");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAD_MINECRAFT = register("music.sad.minecraft");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAD_ONE_MORE_DAY = register("music.sad.one_more_day");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAD_OXYGENE = register("music.sad.oxygene");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAD_SUBWOOFER_LULLABY = register("music.sad.subwoofer_lullaby");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAD_SWEDEN = register("music.sad.sweden");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAD_WET_HANDS = register("music.sad.wet_hands");
    public static final DeferredHolder<SoundEvent, SoundEvent> PHASE3_FROSTBITTEN = register("music.phase3_frostbitten");
    public static final DeferredHolder<SoundEvent, SoundEvent> PHASE5_IGLOO = register("music.phase5_igloo");
    public static final DeferredHolder<SoundEvent, SoundEvent> PHASE6_SUB_ZERO = register("music.phase6_sub_zero");

    // --- Frostbitten Mob ---
    public static final DeferredHolder<SoundEvent, SoundEvent> FROSTBITTEN_AMBIENT = SOUNDS.register("entity.frostbitten.ambient",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.frostbitten.ambient")));

    public static final DeferredHolder<SoundEvent, SoundEvent> FROSTBITTEN_HURT = SOUNDS.register("entity.frostbitten.hurt",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.frostbitten.hurt")));

    public static final DeferredHolder<SoundEvent, SoundEvent> FROSTBITTEN_DEATH = SOUNDS.register("entity.frostbitten.death",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.frostbitten.death")));

    public static final DeferredHolder<SoundEvent, SoundEvent> FROSTBITTEN_THROW = SOUNDS.register("entity.frostbitten.throw",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.frostbitten.throw")));

    public static final DeferredHolder<SoundEvent, SoundEvent> FROSTBITTEN_EMERGE = SOUNDS.register("entity.frostbitten.emerge",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.frostbitten.emerge")));

    // --- Frostmite Mob ---
    public static final DeferredHolder<SoundEvent, SoundEvent> FROSTMITE_AMBIENT = SOUNDS.register("entity.frostmite.ambient",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.frostmite.ambient")));

    public static final DeferredHolder<SoundEvent, SoundEvent> FROSTMITE_HURT = SOUNDS.register("entity.frostmite.hurt",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.frostmite.hurt")));

    public static final DeferredHolder<SoundEvent, SoundEvent> FROSTMITE_DEATH = SOUNDS.register("entity.frostmite.death",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.frostmite.death")));

    public static final DeferredHolder<SoundEvent, SoundEvent> FROSTMITE_STEP = SOUNDS.register("entity.frostmite.step",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.frostmite.step")));

    // --- Hollow Mob ---
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_AMBIENT = SOUNDS.register("entity.hollow.ambient",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.hollow.ambient")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_HURT = SOUNDS.register("entity.hollow.hurt",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.hollow.hurt")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_DEATH = SOUNDS.register("entity.hollow.death",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.hollow.death")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_GRAB = SOUNDS.register("entity.hollow.grab",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.hollow.grab")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_ENTOMB = SOUNDS.register("entity.hollow.entomb",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.hollow.entomb")));

    // --- Returned Mob ---
    public static final DeferredHolder<SoundEvent, SoundEvent> RETURNED_AMBIENT = SOUNDS.register("entity.returned.ambient",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.returned.ambient")));

    public static final DeferredHolder<SoundEvent, SoundEvent> RETURNED_HURT = SOUNDS.register("entity.returned.hurt",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.returned.hurt")));

    public static final DeferredHolder<SoundEvent, SoundEvent> RETURNED_DEATH = SOUNDS.register("entity.returned.death",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.returned.death")));

    public static final DeferredHolder<SoundEvent, SoundEvent> RETURNED_STEP = SOUNDS.register("entity.returned.step",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.returned.step")));

    // --- Mimic Mob ---
    public static final DeferredHolder<SoundEvent, SoundEvent> MIMIC_HURT = SOUNDS.register("entity.mimic.hurt",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.mimic.hurt")));

    public static final DeferredHolder<SoundEvent, SoundEvent> MIMIC_DEATH = SOUNDS.register("entity.mimic.death",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.mimic.death")));

    public static final DeferredHolder<SoundEvent, SoundEvent> MIMIC_ATTACK = SOUNDS.register("entity.mimic.attack",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.mimic.attack")));

    public static final DeferredHolder<SoundEvent, SoundEvent> MIMIC_STARE = SOUNDS.register("entity.mimic.stare",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.mimic.stare")));

    // --- Architect Mob ---
    public static final DeferredHolder<SoundEvent, SoundEvent> ARCHITECT_MINE = SOUNDS.register("entity.architect.mine",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.architect.mine")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ARCHITECT_AMBIENT = SOUNDS.register("entity.architect.ambient",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.architect.ambient")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ARCHITECT_OBSERVE = SOUNDS.register("entity.architect.observe",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.architect.observe")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ARCHITECT_WATCHED = SOUNDS.register("entity.architect.watched",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.architect.watched")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ARCHITECT_DRINK = SOUNDS.register("entity.architect.drink",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.architect.drink")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ARCHITECT_HURT = SOUNDS.register("entity.architect.hurt",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.architect.hurt")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ARCHITECT_DEATH = SOUNDS.register("entity.architect.death",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.architect.death")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ARCHITECT_ICE_PLACE = SOUNDS.register("entity.architect.ice_place",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.architect.ice_place")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ARCHITECT_LAND = SOUNDS.register("entity.architect.land",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.architect.land")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_CAST = register(
            "entity.master_architect.cast");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_STAFF = register(
            "entity.master_architect.staff");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_FRACTURE = register(
            "entity.master_architect.fracture");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_THERMAL_SEVER = register(
            "entity.master_architect.thermal_sever");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_LAST_WALL = register(
            "entity.master_architect.last_wall");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_CONSTRUCTION = register(
            "entity.master_architect.construction");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_THERMAL_PULSE = register(
            "entity.master_architect.thermal_pulse");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_STORM_MAINTAIN = register(
            "entity.master_architect.storm_maintain");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_AMBIENT = register(
            "entity.master_architect.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_HURT = register(
            "entity.master_architect.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_DEATH = register(
            "entity.master_architect.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_DETONATE = register(
            "entity.master_architect.detonate");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_TETHER_DEFLECT = register(
            "entity.master_architect.tether_deflect");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_TETHER_STRAIN = register(
            "entity.master_architect.tether_strain");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_TETHER_DEPLOY = register(
            "entity.master_architect.tether_deploy");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_TETHER_WAIL = register(
            "entity.master_architect.tether_wail");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_OBSTRUCTION_SMASH = register(
            "entity.master_architect.obstruction_smash");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_FLOOD_BEGIN = register(
            "entity.master_architect.flood_begin");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_FLOOD_MOTE = register(
            "entity.master_architect.flood_mote");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_CORE_REVEAL = register(
            "entity.master_architect.core_reveal");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_FLOOD_HIT = register(
            "entity.master_architect.flood_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_FLOOD_RETURN = register(
            "entity.master_architect.flood_return");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_MIND_DEATH_WAIL = register(
            "entity.master_architect.mind_death_wail");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_MIND_HEAL_ESCALATE = register(
            "entity.master_architect.mind_heal_escalate");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_MIND_SCAN_TTS = register(
            "ui.master_architect.mind_scan");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_TELEMETRY_MISMATCH_TTS = register(
            "ui.master_architect.telemetry_mismatch");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_TELEMETRY_RESTORED_TTS = register(
            "ui.master_architect.telemetry_restored");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_THERMAL_CHARGE = register(
            "entity.master_architect.thermal_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_THERMAL_COMMIT = register(
            "entity.master_architect.thermal_commit");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_THERMAL_CANCEL = register(
            "entity.master_architect.thermal_cancel");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_THUNDERSNOW_CLOSE = register(
            "entity.master_architect.thundersnow_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_THUNDERSNOW_MID = register(
            "entity.master_architect.thundersnow_mid");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_THUNDERSNOW_DISTANT = register(
            "entity.master_architect.thundersnow_distant");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_ARC_CRACKLE = register(
            "entity.master_architect.arc_crackle");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_INFRASOUND = register(
            "entity.master_architect.infrasound");
    public static final DeferredHolder<SoundEvent, SoundEvent> THAE_IVEN_HEART_FORMATION = register(
            "entity.thae_iven_heart.formation");
    public static final DeferredHolder<SoundEvent, SoundEvent> THAE_IVEN_HEART_ECHO_APPEAR = register(
            "entity.thae_iven_heart.echo_appear");
    public static final DeferredHolder<SoundEvent, SoundEvent> THAE_IVEN_HEART_ECHO_ACKNOWLEDGE = register(
            "entity.thae_iven_heart.echo_acknowledge");
    public static final DeferredHolder<SoundEvent, SoundEvent> THAE_IVEN_HEART_ECHO_SCREAM = register(
            "entity.thae_iven_heart.echo_scream");
    public static final DeferredHolder<SoundEvent, SoundEvent> THAE_IVEN_HEART_ECHO_BREAK = register(
            "entity.thae_iven_heart.echo_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_AURA_DEATH = register(
            "entity.master_architect.aura_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_AURA_TEMPERATURE_TTS = register(
            "ui.master_architect.aura_temperature");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASTER_ARCHITECT_AURA_FIGHT_TTS = register(
            "ui.master_architect.aura_fight");

    // --- Terminal Boot ---
    public static final DeferredHolder<SoundEvent, SoundEvent> TERMINAL_BOOT_ORSA = register("terminal.boot_orsa");
    public static final DeferredHolder<SoundEvent, SoundEvent> ORSA_AWAKENING_VOICE = register("ui.orsa_awakening_voice");
    public static final DeferredHolder<SoundEvent, SoundEvent> ORSA_AWAKENING_RING = register("ui.orsa_awakening_ring");
    public static final DeferredHolder<SoundEvent, SoundEvent> THAEVEN_CONTACT = register("ui.thaeven_contact");
    public static final DeferredHolder<SoundEvent, SoundEvent> THAEVEN_ORSHA = register("ui.thaeven_orsha");
    public static final DeferredHolder<SoundEvent, SoundEvent> THAEVEN_RESOLVE = register("ui.thaeven_resolve");
    public static final DeferredHolder<SoundEvent, SoundEvent> THAEVEN_INTERRUPT = register("ui.thaeven_interrupt");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEARTH_BOUNDARY_WARNING = register(
            "entity.hearth.boundary_warning");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEARTH_BOUNDARY_ORSATHAE = register(
            "entity.hearth.orsathae");
    public static final List<DeferredHolder<SoundEvent, SoundEvent>> BLACKGLASS_SEGMENTS = List.of(
            register("terminal.blackglass_segment_01"),
            register("terminal.blackglass_segment_02"),
            register("terminal.blackglass_segment_03"),
            register("terminal.blackglass_segment_04"),
            register("terminal.blackglass_segment_05"),
            register("terminal.blackglass_segment_06"),
            register("terminal.blackglass_segment_07"),
            register("terminal.blackglass_segment_08")
    );

    // --- Camp Radio ---
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_STATIC_BURST = register("radio.static_burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_STATIC_MEDIUM = register("radio.static_medium");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_STATIC_AMBIENT = register("radio.static_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_STATIC_HEAVY = register("radio.static_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_SIGNAL_LOCK = register("radio.signal_lock");
    public static final DeferredHolder<SoundEvent, SoundEvent> SURVEYOR_LENS_TICK = register("item.surveyor_lens.tick");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_MARTIAN_COMMAND_MESSAGE = register("radio.martian_command_message");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_CUTOFF = register("radio.cutoff");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_TOWER = register("radio.voice.tower");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_SIGNAL = register("radio.voice.signal");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_AT = register("radio.voice.at");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_COORDINATES = register("radio.voice.coordinates");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_NEGATIVE = register("radio.voice.negative");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_X_COORD = register("radio.voice.x_coord");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_Z_COORD = register("radio.voice.z_coord");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_ZERO = register("radio.voice.zero");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_ONE = register("radio.voice.one");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_TWO = register("radio.voice.two");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_THREE = register("radio.voice.three");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_FOUR = register("radio.voice.four");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_FIVE = register("radio.voice.five");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_SIX = register("radio.voice.six");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_SEVEN = register("radio.voice.seven");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_EIGHT = register("radio.voice.eight");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_NINE = register("radio.voice.nine");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_HUNDRED = register("radio.voice.hundred");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_THOUSAND = register("radio.voice.thousand");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_ORSA = register("radio.voice.orsa");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_FIELD_UNIT = register("radio.voice.field_unit");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_REPEAT = register("radio.voice.repeat");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_UNABLE = register("radio.voice.unable");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_VOICE_NO_LOCK = register("radio.voice.no_lock");
}
