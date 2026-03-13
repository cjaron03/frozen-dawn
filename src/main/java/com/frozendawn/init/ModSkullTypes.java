package com.frozendawn.init;

import net.minecraft.world.level.block.SkullBlock;

public final class ModSkullTypes {
    public static final SkullBlock.Type ARCHITECT = register("architect");

    private ModSkullTypes() {
    }

    private static SkullBlock.Type register(String name) {
        return new SimpleType(name);
    }

    private record SimpleType(String name) implements SkullBlock.Type {
        private SimpleType {
            SkullBlock.Type.TYPES.put(name, this);
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
