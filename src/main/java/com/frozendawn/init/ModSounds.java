package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

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

    public static final DeferredHolder<SoundEvent, SoundEvent> EVA_BREATHING = SOUNDS.register("ambient.eva_breathing",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.eva_breathing")));

    public static final DeferredHolder<SoundEvent, SoundEvent> EVA_SUFFOCATE = SOUNDS.register("ambient.eva_suffocate",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.eva_suffocate")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SANITY_WHISPER = SOUNDS.register("ambient.sanity_whisper",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.sanity_whisper")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SANITY_FOOTSTEP = SOUNDS.register("ambient.sanity_footstep",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.sanity_footstep")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SANITY_THUD = SOUNDS.register("ambient.sanity_thud",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.sanity_thud")));

    // --- Menu Music ---
    public static final DeferredHolder<SoundEvent, SoundEvent> MENU_MUSIC = SOUNDS.register("music.menu",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "music.menu")));

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
    public static final DeferredHolder<SoundEvent, SoundEvent> FOREST_NIGHT = register("music.biome.forest_night");
    public static final DeferredHolder<SoundEvent, SoundEvent> PHASE4_GUEST_1 = register("music.phase4_guest_1");

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
}
