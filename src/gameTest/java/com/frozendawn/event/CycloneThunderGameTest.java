package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModSounds;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CycloneThunderGameTest {
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="cyclone_thunder",timeoutTicks=20)
    public static void cycloneThunderReplayFunctionsParseAndSoundAliasesRegister(GameTestHelper h) {
        for(var name:java.util.List.of("load","tick","setup","controls","approach","fade","stop","open","roof","moderate","severe","catastrophic"))
            h.assertTrue(h.getLevel().getServer().getFunctions().get(ResourceLocation.fromNamespaceAndPath("cyclone_thunder",name)).isPresent(),"Native parser loaded "+name);
        for(var sound:java.util.List.of(ModSounds.CYCLONE_THUNDER_CLOSE,ModSounds.CYCLONE_THUNDER_MID,ModSounds.CYCLONE_THUNDER_DISTANT))
            h.assertTrue(sound.get().getLocation().getPath().startsWith("ambient.cyclone.thunder_"),"Cyclone sound registered independently of boss events");
        h.succeed();
    }
}
