package com.frozendawn.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectNoisePoseTest {
    @Test void bentElbowsPutBothHandsBesideTheHead() {
        var model=new ArchitectModel(ArchitectModel.createBodyLayer().bakeRoot());
        model.applyBlindNoisePose(1.0F);
        for(boolean right:new boolean[]{true,false}) {
            ModelPart arm=right?model.rightArm:model.leftArm;
            ModelPart upper=arm.getChild(right?"noise_upper_arm":"thinking_upper_arm");
            ModelPart forearm=upper.getChild(right?"noise_forearm":"thinking_forearm");
            var poses=new PoseStack();arm.translateAndRotate(poses);upper.translateAndRotate(poses);forearm.translateAndRotate(poses);
            var hand=new Vector4f((right?-1.0F:1.0F)/16.0F,5.0F/16.0F,0,1).mul(poses.last().pose()).mul(16.0F);
            assertTrue(Math.abs(hand.x)>4&&Math.abs(hand.x)<6,"Hands meet the outer head/helmet sides");
            assertTrue(hand.y>-4.5F&&hand.y<-2.5F,"Hands cover the ears rather than the shoulders");
            assertEquals(0.0F,hand.z,0.001F);assertTrue(arm.skipDraw&&upper.visible);
        }
    }
    @Test void sharedModelClearsThePoseBeforeRenderingAnotherActor() {
        var model=new ArchitectModel(ArchitectModel.createBodyLayer().bakeRoot());
        model.applyBlindNoisePose(1.0F);model.resetOptionalArmRigs();
        assertFalse(model.rightArm.skipDraw);assertFalse(model.leftArm.skipDraw);
        assertFalse(model.rightArm.getChild("noise_upper_arm").visible);
        assertFalse(model.leftArm.getChild("thinking_upper_arm").visible);
        assertEquals(0.0F,model.rightArm.getChild("noise_upper_arm").getChild("noise_forearm").zRot);
        assertEquals(0.0F,model.leftArm.getChild("thinking_upper_arm").getChild("thinking_forearm").zRot);
    }
}
