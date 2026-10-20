package com.frozendawn.command;
import com.frozendawn.cyclone.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import java.util.Locale;
public final class CycloneDebugCommand {
    private CycloneDebugCommand() {}
    public static LiteralArgumentBuilder<CommandSourceStack> commands() {
        var root=Commands.literal("cyclone").requires(s->s.hasPermission(2));
        root.then(Commands.literal("status").executes(c->{c.getSource().sendSuccess(()->Component.literal(CycloneController.describe(c.getSource().getServer().overworld())),false);return 1;}));
        root.then(Commands.literal("stage-index").executes(c->{var p=c.getSource().getPosition();
            return CycloneController.sampleAt(c.getSource().getServer().overworld(),p.x,p.z).stage().ordinal();}));
        root.then(Commands.literal("plan").executes(c->{var d=CycloneSavedData.get(c.getSource().getServer());c.getSource().sendSuccess(()->Component.literal("Planned for "+d.plannedFor()+": "+d.plan()),false);return 1;}));
        root.then(Commands.literal("end").executes(c->{CycloneController.end(c.getSource().getServer().overworld());return 1;}));
        var start=Commands.literal("start");
        for(var sev:CycloneSeverity.values())start.then(Commands.literal(sev.name().toLowerCase(Locale.ROOT))
                .executes(c->start(c.getSource(),sev,c.getSource().getPlayerOrException(),0))
                .then(Commands.argument("player",EntityArgument.player())
                    .executes(c->start(c.getSource(),sev,EntityArgument.getPlayer(c,"player"),0))
                    .then(Commands.argument("bearing",FloatArgumentType.floatArg(-360,360))
                        .executes(c->start(c.getSource(),sev,EntityArgument.getPlayer(c,"player"),FloatArgumentType.getFloat(c,"bearing"))))));
        root.then(start);var stage=Commands.literal("stage");
        for(var st:CycloneStage.values())stage.then(Commands.literal(st.name().toLowerCase(Locale.ROOT)).executes(c->{
            try {CycloneController.forceStage(c.getSource().getServer().overworld(),st);return 1;}
            catch(IllegalArgumentException ex){throw new SimpleCommandExceptionType(Component.literal(ex.getMessage())).create();}
        }).then(Commands.argument("progress",FloatArgumentType.floatArg(0,.99f)).executes(c->{
            try {CycloneController.forceStage(c.getSource().getServer().overworld(),st,FloatArgumentType.getFloat(c,"progress"));return 1;}
            catch(IllegalArgumentException ex){throw new SimpleCommandExceptionType(Component.literal(ex.getMessage())).create();}
        })));return root.then(stage);
    }
    private static int start(CommandSourceStack source,CycloneSeverity sev,net.minecraft.server.level.ServerPlayer player,float bearing)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {CycloneController.forceStart(source.getServer().overworld(),sev,player,(float)Math.toRadians(bearing));return 1;}
        catch(IllegalArgumentException ex){throw new SimpleCommandExceptionType(Component.literal(ex.getMessage())).create();}
    }
}
