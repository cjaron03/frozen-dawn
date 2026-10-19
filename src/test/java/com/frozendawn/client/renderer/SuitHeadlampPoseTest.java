package com.frozendawn.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SuitHeadlampPoseTest {
    @Test void lensClearsTheArmorShellAndGogglesAtTheForehead() {
        var root=SuitHeadlampCurioModel.createBodyLayer().bakeRoot();
        var helmet=cubes(LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(1),0),64,32)
                .bakeRoot().getChild("head")).getFirst();
        var goggles=cubes(BlizzardGogglesCurioModel.createBodyLayer().bakeRoot().getChild("head")).getFirst();
        for(var cube:cubes(root.getChild("lens"))) {
            assertTrue(cube.maxZ<helmet.minZ,"Lens sits outside the inflated EVA helmet shell");
            assertTrue(cube.maxY<goggles.minY,"Lamp stays above the goggles rather than covering the eyes");
            assertTrue(cube.minX>helmet.minX&&cube.maxX<helmet.maxX,"Lamp is centered inside helmet width");
        }
    }

    @Test void renderedVerticesFollowHeadYawPitchAndTranslationWithoutLeakingThePose() {
        var model=new SuitHeadlampCurioModel(SuitHeadlampCurioModel.createBodyLayer().bakeRoot());
        var head=LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE,0),64,32).bakeRoot().getChild("head");
        var baseline=vertices(model,head);
        head.x=2;head.y=3;head.z=-1;head.yRot=(float)Math.PI/2;
        var yaw=vertices(model,head);
        assertEquals(baseline.size(),yaw.size());
        for(int i=0;i<baseline.size();i++) {
            var a=baseline.get(i);var b=yaw.get(i);
            assertEquals(a.z+2f/16,b.x,0.0001);assertEquals(a.y+3f/16,b.y,0.0001);assertEquals(-a.x-1f/16,b.z,0.0001);
        }
        head.x=head.y=head.z=head.yRot=0;head.xRot=(float)Math.PI/2;
        var pitch=vertices(model,head);
        for(int i=0;i<baseline.size();i++) {
            var a=baseline.get(i);var b=pitch.get(i);
            assertEquals(a.x,b.x,0.0001);assertEquals(-a.z,b.y,0.0001);assertEquals(a.y,b.z,0.0001);
        }
    }

    private static List<ModelPart.Cube> cubes(ModelPart part) {
        var cubes=new ArrayList<ModelPart.Cube>();
        part.visit(new PoseStack(),(pose,path,index,cube)->cubes.add(cube));return cubes;
    }

    private static List<Vector3f> vertices(SuitHeadlampCurioModel model,ModelPart head) {
        var result=new ArrayList<Vector3f>();
        var consumer=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},
                (proxy,method,args)->{
                    if(method.isDefault())return InvocationHandler.invokeDefault(proxy,method,args);
                    if(method.getName().equals("addVertex")&&args.length==3)
                        result.add(new Vector3f((float)args[0],(float)args[1],(float)args[2]));
                    return method.getReturnType()==void.class?null:proxy;
                });
        var pose=new PoseStack();model.renderAttached(pose,head,consumer,0,0);
        assertTrue(pose.last().pose().equals(new org.joml.Matrix4f()),"Rendering restores the caller's pose");
        assertFalse(result.isEmpty());return result;
    }
}
