package com.frozendawn.thermal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/** Data-pack overrides win; sound defaults cover unfamiliar modded full blocks. Seal logic stays separate. */
public final class RoomInsulation {
    private RoomInsulation() {}
    private static TagKey<Block> tag(String name) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("frozendawn", "insulation/"+name));
    }
    private static final TagKey<Block> INSULATOR=tag("insulator"),WOOD=tag("wood"),STONE=tag("stone"),CONDUCTOR=tag("conductor");
    public static double conductance(ServerLevel level, BlockPos pos, BlockState state) {
        if(state.is(INSULATOR))return .1;
        if(state.is(WOOD))return .3;
        if(state.is(STONE))return .8;
        if(state.is(CONDUCTOR))return 1;
        SoundType sound=state.getSoundType(level,pos,null);
        if(sound==SoundType.WOOL||sound==SoundType.SNOW)return .1;
        if(sound==SoundType.WOOD||sound==SoundType.NETHER_WOOD||sound==SoundType.CHERRY_WOOD||sound==SoundType.BAMBOO_WOOD)return .3;
        if(sound==SoundType.STONE||sound==SoundType.DEEPSLATE||sound==SoundType.DEEPSLATE_BRICKS||sound==SoundType.DEEPSLATE_TILES||sound==SoundType.TUFF)return .8;
        if(sound==SoundType.METAL||sound==SoundType.GLASS||sound==SoundType.COPPER)return 1;
        return .5;
    }
}
