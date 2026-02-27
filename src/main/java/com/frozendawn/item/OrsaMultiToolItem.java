package com.frozendawn.item;

import com.frozendawn.block.GeothermalCoreBlockEntity;
import com.frozendawn.block.ThermalHeaterBlockEntity;
import com.frozendawn.block.TransponderBlock;
import com.frozendawn.block.TransponderBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * ORSA-issued diagnostic tool. Right-click on Frozen Dawn blocks
 * (heaters, transponder, geothermal core) to view detailed stats.
 */
public class OrsaMultiToolItem extends Item {

    public OrsaMultiToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) return InteractionResult.sidedSuccess(true);
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.PASS;

        BlockEntity be = level.getBlockEntity(context.getClickedPos());

        if (be instanceof ThermalHeaterBlockEntity heater) {
            showHeaterDiagnostics(player, heater);
            return InteractionResult.sidedSuccess(false);
        }

        if (be instanceof TransponderBlockEntity transponder) {
            showTransponderDiagnostics(player, transponder);
            return InteractionResult.sidedSuccess(false);
        }

        if (be instanceof GeothermalCoreBlockEntity core) {
            showCoreDiagnostics(player, core);
            return InteractionResult.sidedSuccess(false);
        }

        return InteractionResult.PASS;
    }

    private void showHeaterDiagnostics(ServerPlayer player, ThermalHeaterBlockEntity heater) {
        String p = "\u00A77[\u00A76ORSA\u00A77] ";
        player.sendSystemMessage(Component.literal(p + "\u00A7e--- Heater Diagnostics ---"));
        player.sendSystemMessage(Component.literal(p + "\u00A77Heat Output: \u00A7f+" + heater.getPublicHeatOutput() + "\u00B0C"));
        player.sendSystemMessage(Component.literal(p + "\u00A77Base Radius: \u00A7f" + heater.getPublicBaseRadius() + " blocks"));

        int effectiveRadius = heater.getEffectiveRadius();
        String radiusInfo = effectiveRadius + " blocks";
        if (heater.isWindExposed()) {
            radiusInfo += " \u00A7c(wind reduced)";
        }
        player.sendSystemMessage(Component.literal(p + "\u00A77Effective Radius: \u00A7f" + radiusInfo));

        boolean sheltered = heater.getCachedSheltered();
        player.sendSystemMessage(Component.literal(p + "\u00A77Shelter: " + (sheltered ? "\u00A7aDetected" : "\u00A7cExposed")));

        int consumption = heater.getPublicPhaseConsumption();
        String burnInfo = consumption + "x";
        if (consumption > 1) {
            burnInfo += " \u00A7c(phase " + heater.getPhase() + " scaling)";
        }
        player.sendSystemMessage(Component.literal(p + "\u00A77Burn Rate: \u00A7f" + burnInfo));

        if (heater.isLit()) {
            player.sendSystemMessage(Component.literal(p + "\u00A77Fuel ETA: \u00A7f" + heater.getBurnEtaMinutes() + " min"));
        } else {
            player.sendSystemMessage(Component.literal(p + "\u00A77Status: \u00A7cNo Fuel"));
        }
    }

    private void showTransponderDiagnostics(ServerPlayer player, TransponderBlockEntity transponder) {
        String p = "\u00A77[\u00A76ORSA\u00A77] ";
        player.sendSystemMessage(Component.literal(p + "\u00A7e--- Transponder Diagnostics ---"));

        int state = transponder.getBlockState().getValue(TransponderBlock.STATE);
        String stateStr = switch (state) {
            case TransponderBlockEntity.STATE_IDLE -> "\u00A77Idle";
            case TransponderBlockEntity.STATE_BROADCASTING -> "\u00A7aBroadcasting";
            case TransponderBlockEntity.STATE_COMPLETE -> "\u00A7dComplete";
            case TransponderBlockEntity.STATE_PAUSED -> "\u00A7cPaused";
            default -> "\u00A77Unknown";
        };
        player.sendSystemMessage(Component.literal(p + "\u00A77State: " + stateStr));

        if (state == TransponderBlockEntity.STATE_BROADCASTING || state == TransponderBlockEntity.STATE_PAUSED) {
            int total = transponder.getTotalBroadcastTicks();
            int remaining = transponder.getBroadcastTicksRemaining();
            int progress = total > 0 ? (int) (100f * (total - remaining) / total) : 0;
            player.sendSystemMessage(Component.literal(p + "\u00A77Progress: \u00A7f" + progress + "%"));
            player.sendSystemMessage(Component.literal(p + "\u00A77ETA: \u00A7f" + (remaining / 1200) + " min"));
        }

        player.sendSystemMessage(Component.literal(p + "\u00A77Depth: \u00A7fY=" + transponder.getBlockPos().getY()));
    }

    private void showCoreDiagnostics(ServerPlayer player, GeothermalCoreBlockEntity core) {
        String p = "\u00A77[\u00A76ORSA\u00A77] ";
        player.sendSystemMessage(Component.literal(p + "\u00A7e--- Geothermal Core ---"));
        player.sendSystemMessage(Component.literal(p + "\u00A77Status: \u00A7aOnline"));
        player.sendSystemMessage(Component.literal(p + "\u00A77Position: \u00A7f" +
                core.getBlockPos().getX() + ", " + core.getBlockPos().getY() + ", " + core.getBlockPos().getZ()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right-click blocks for diagnostics").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
