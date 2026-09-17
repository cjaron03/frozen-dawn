package com.frozendawn.debug.architect;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.network.ArchitectDebugPayload;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/** Operator subscriptions and controls. All inspection happens on the server thread. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ArchitectVisualDebug {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<MinecraftServer, Map<UUID, Subscription>> SERVERS = new HashMap<>();
    private enum Follow { ENTITY, LAB, WILDERNESS }
    private static final class Subscription {
        final Follow follow;
        final ResourceKey<Level> dimension;
        final UUID entity;
        ArchitectEntity bound;
        int layers = ArchitectDebugPayload.DEFAULT_LAYERS;
        boolean logging;
        boolean sentFrozen;
        String runId = "";
        Long historyTick;
        long forceThrough = Long.MIN_VALUE;
        long lastLogged = Long.MIN_VALUE;
        ArchitectDebugSnapshot sent;
        Subscription(Follow follow, ArchitectEntity actor) {
            this.follow = follow; dimension = actor.level().dimension(); entity = actor.getUUID();
        }
    }
    private ArchitectVisualDebug() { }

    public static LiteralArgumentBuilder<CommandSourceStack> commands(String follow) {
        var root = Commands.literal("debug").requires(s -> s.hasPermission(2));
        root.then(Commands.literal("on").executes(c -> command(c.getSource(), follow, "on", "", null))
                .then(Commands.argument("entity", EntityArgument.entity()).executes(c ->
                        command(c.getSource(), "entity", "on", "", EntityArgument.getEntity(c, "entity")))));
        for (String operation : new String[]{"off", "inspect", "dump", "freeze", "resume", "live"})
            root.then(Commands.literal(operation).executes(c -> command(c.getSource(), follow, operation, "", null)));
        root.then(Commands.literal("step").executes(c -> command(c.getSource(), follow, "step", "1", null))
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 200)).executes(c ->
                        command(c.getSource(), follow, "step", Integer.toString(IntegerArgumentType.getInteger(c, "ticks")), null))));
        root.then(Commands.literal("history").then(Commands.argument("ticksAgo", IntegerArgumentType.integer(1, 6000))
                .executes(c -> command(c.getSource(), follow, "history", Integer.toString(IntegerArgumentType.getInteger(c, "ticksAgo")), null))));
        root.then(Commands.literal("mark").then(Commands.argument("label", StringArgumentType.greedyString())
                .executes(c -> command(c.getSource(), follow, "mark", StringArgumentType.getString(c, "label"), null))));
        for (String layer : new String[]{"routes", "geometry", "hud", "trails", "labels", "log"}) {
            var toggle = Commands.literal(layer);
            for (String value : new String[]{"on", "off"})
                toggle.then(Commands.literal(value).executes(c -> command(c.getSource(), follow, layer, value, null)));
            root.then(toggle);
        }
        return root;
    }

    private static int command(CommandSourceStack source, String follow, String operation, String argument, Entity selected) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            MinecraftServer server = source.getServer();
            var subscriptions = SERVERS.computeIfAbsent(server, key -> new HashMap<>());
            if (operation.equals("off")) {
                Subscription old = subscriptions.remove(player.getUUID());
                if (old != null) release(old.bound, subscriptions);
                PacketDistributor.sendToPlayer(player, ArchitectDebugPayload.off());
                reply(source, "Architect visual debugger off. Captured evidence remains available in dumps.");
                LOGGER.info("[ArchitectVisual] OFF observer={}", player.getUUID());
                return 1;
            }
            if (operation.equals("on")) {
                ArchitectEntity actor = select(source, follow, selected);
                Follow mode = selected != null ? Follow.ENTITY
                        : ArchitectWildernessLab.debugActor(server) == actor ? Follow.WILDERNESS
                        : ArchitectLab.debugActor(source.getLevel()) == actor ? Follow.LAB : Follow.ENTITY;
                Subscription old = subscriptions.remove(player.getUUID());
                if (old != null) release(old.bound, subscriptions);
                Subscription subscription = new Subscription(mode, actor);
                subscriptions.put(player.getUUID(), subscription);
                bind(subscription, actor, subscriptions);
                actor.recordDecision("VISUAL_ON", null, "observer=" + player.getUUID());
                capture(actor, true);
                publish(player, subscription, "");
                reply(source, "Architect debugger on for #" + actor.getId() + ". Routes, geometry, HUD and trails enabled. Use debug off, freeze, step, history, mark or dump.");
                LOGGER.info("[ArchitectVisual] ON observer={} entity={} follow={}", player.getUUID(), actor.getUUID(), mode);
                return 1;
            }
            Subscription subscription = subscriptions.get(player.getUUID());
            if (subscription == null) throw new IllegalStateException("Use /fd architect debug on first");
            ArchitectEntity actor = resolve(server, subscription);
            if (actor == null || actor.level() != player.level()) throw new IllegalStateException("Selected Architect is unavailable; wait for lab preparation or select another entity");
            bind(subscription, actor, subscriptions);
            var clock = server.tickRateManager();
            switch (operation) {
                case "freeze" -> {
                    if (clock.isSprinting()) clock.stopSprinting();
                    if (clock.isSteppingForward()) clock.stopStepping();
                    clock.setFrozen(true); subscription.historyTick = null;
                    capture(actor, true);
                    reply(source, "Simulation frozen. Use debug step [ticks] or debug resume.");
                }
                case "step" -> {
                    int ticks = Integer.parseInt(argument);
                    if (!clock.stepGameIfPaused(ticks)) throw new IllegalStateException("Freeze the simulation before stepping");
                    subscription.historyTick = null;
                    subscription.forceThrough = actor.level().getGameTime() + ticks;
                    reply(source, "Stepping " + ticks + " tick(s); debugger captures each stepped tick.");
                }
                case "resume" -> {
                    if (clock.isSteppingForward()) clock.stopStepping();
                    clock.setFrozen(false); subscription.historyTick = null;
                    reply(source, "Simulation resumed.");
                }
                case "history" -> {
                    capture(actor, true);
                    var trace = actor.decisionJournal().visual();
                    long requested = trace.latest().gameTick() - Integer.parseInt(argument);
                    var frame = trace.atOrBefore(requested);
                    if (frame == null) throw new IllegalStateException("That tick is outside the retained visual history; inspect shows the available range");
                    subscription.historyTick = frame.gameTick();
                    reply(source, "Historical overlay at game tick " + frame.gameTick() + ". Terrain stays at its current state. Use debug live to return.");
                }
                case "live" -> { subscription.historyTick = null; capture(actor, true); reply(source, "Showing the latest server observation."); }
                case "mark" -> {
                    if (actor.decisionJournal().visual().sealed()) throw new IllegalStateException("This recording is complete; reset the lab before recording new markers");
                    actor.recordDecision("MARK", null, argument.substring(0, Math.min(200, argument.length())));
                    capture(actor, true);
                    LOGGER.info("[ArchitectVisual] MARK entity={} tick={} label={}", actor.getUUID(), actor.level().getGameTime(), argument.replace('\n', ' ').replace('\r', ' '));
                    reply(source, "Marked game tick " + actor.level().getGameTime() + ".");
                }
                case "inspect" -> {
                    capture(actor, true);
                    var frame = displayed(subscription);
                    if (frame == null) throw new IllegalStateException("Historical frame expired; use debug live");
                    reply(source, frame.logLine() + "\nVisual history: " + actor.decisionJournal().visual().summary());
                    LOGGER.info("[ArchitectVisual] INSPECT {}", frame.logLine());
                }
                case "dump" -> {
                    actor.recordDecision("VISUAL_DUMP", null, "operator capture");
                    capture(actor, true);
                    var frame = displayed(subscription);
                    if (frame == null) throw new IllegalStateException("Historical frame expired; use debug live");
                    var view = view(player, subscription, "");
                    var directory = ArchitectDebugReports.export(ArchitectLab.reports((ServerLevel) actor.level()), actor.decisionJournal(),
                            Map.of("actorUuid", actor.getUUID().toString(), "scenario", frame.lab().scenario(),
                                    "displayedGameTick", frame.gameTick(), "historicalView", subscription.historyTick != null),
                            Map.of("visual-view.json", ArchitectDebugSnapshot.JSON.toJson(view)));
                    reply(source, "Visual snapshot, history and decisions: " + directory);
                    LOGGER.info("[ArchitectVisual] DUMP {} directory={}", frame.logLine(), directory);
                }
                case "log" -> {
                    subscription.logging = argument.equals("on");
                    subscription.lastLogged = Long.MIN_VALUE;
                    reply(source, "Architect visual state logging " + argument + ". Full geometry remains in dumps.");
                }
                default -> {
                    int bit = switch (operation) {
                        case "routes" -> ArchitectDebugPayload.ROUTES;
                        case "geometry" -> ArchitectDebugPayload.GEOMETRY;
                        case "hud" -> ArchitectDebugPayload.HUD;
                        case "trails" -> ArchitectDebugPayload.TRAILS;
                        case "labels" -> ArchitectDebugPayload.LABELS;
                        default -> throw new IllegalArgumentException("Unknown debugger operation");
                    };
                    subscription.layers = argument.equals("on") ? subscription.layers | bit : subscription.layers & ~bit;
                    reply(source, "Debugger " + operation + " " + argument + ".");
                }
            }
            publish(player, subscription, "");
            return 1;
        } catch (CommandSyntaxException | IllegalArgumentException | IllegalStateException | IOException error) {
            source.sendFailure(Component.literal("Architect debugger: " + error.getMessage()));
            return 0;
        }
    }

    private static ArchitectEntity select(CommandSourceStack source, String follow, Entity selected) {
        ArchitectEntity actor = null;
        if (selected != null) {
            if (!(selected instanceof ArchitectEntity a)) throw new IllegalArgumentException("Select an Architect");
            actor = a;
        } else {
            if (!follow.equals("lab")) actor = ArchitectWildernessLab.debugActor(source.getServer());
            if (actor != null && actor.level() != source.getLevel()) actor = null;
            if (actor == null && !follow.equals("wilderness")) actor = ArchitectLab.debugActor(source.getLevel());
            if (actor == null && follow.equals("auto")) {
                double best = 96 * 96;
                for (var entity : source.getLevel().getAllEntities()) if (entity instanceof ArchitectEntity a && a.isAlive()) {
                    double distance = a.position().distanceToSqr(source.getPosition());
                    if (distance < best) { best = distance; actor = a; }
                }
            }
        }
        if (actor == null || actor.isRemoved()) throw new IllegalStateException("No prepared lab Architect or nearby Architect; use debug on <entity>");
        if (actor.level() != source.getLevel()) throw new IllegalArgumentException("Select an Architect in your dimension");
        if (actor.isMasterArchitectVisual()) throw new IllegalArgumentException("This debugger follows ordinary Architect navigation; select an ordinary Architect");
        return actor;
    }

    private static ArchitectEntity resolve(MinecraftServer server, Subscription subscription) {
        ServerLevel level = server.getLevel(subscription.dimension);
        if (level == null) return null;
        ArchitectEntity actor = switch (subscription.follow) {
            case LAB -> ArchitectLab.debugActor(level);
            case WILDERNESS -> ArchitectWildernessLab.debugActor(server);
            case ENTITY -> level.getEntity(subscription.entity) instanceof ArchitectEntity a ? a : null;
        };
        return actor == null || actor.isRemoved() ? null : actor;
    }

    private static void bind(Subscription subscription, ArchitectEntity actor, Map<UUID, Subscription> subscriptions) {
        if (subscription.bound == actor) {
            if (!subscription.runId.equals(actor.decisionJournal().visual().runId())) {
                subscription.runId = actor.decisionJournal().visual().runId();
                subscription.historyTick = null; subscription.sent = null;
                subscription.lastLogged = Long.MIN_VALUE; subscription.forceThrough = Long.MIN_VALUE;
            }
            return;
        }
        ArchitectEntity previous = subscription.bound;
        subscription.bound = actor; subscription.sent = null; subscription.historyTick = null;
        release(previous, subscriptions);
        var trace = actor.decisionJournal().visual();
        if (trace.runId().isEmpty()) trace.start(actor.decisionJournal().runId(), actor.level().getGameTime());
        trace.setEnabled(true);
        subscription.runId = trace.runId();
    }

    private static void release(ArchitectEntity actor, Map<UUID, Subscription> subscriptions) {
        if (actor != null && subscriptions.values().stream().noneMatch(s -> s.bound == actor)) actor.decisionJournal().visual().setEnabled(false);
    }

    /** Also called immediately before a lab stops navigation, so terminal geometry survives cleanup. */
    public static ArchitectDebugSnapshot capture(ArchitectEntity actor, boolean force) {
        return capture(actor, force, null);
    }

    public static ArchitectDebugSnapshot capture(ArchitectEntity actor, boolean force, ArchitectDebugSnapshot.Lab context) {
        if (!(actor.level() instanceof ServerLevel level)) return null;
        var subscriptions = SERVERS.get(level.getServer());
        if (subscriptions != null) for (var subscription : subscriptions.values())
            if (resolve(level.getServer(), subscription) == actor) bind(subscription, actor, subscriptions);
        var trace = actor.decisionJournal().visual();
        if (!trace.enabled() || (trace.sealed() && trace.latest() != null)) return trace.latest();
        if (force || trace.due(level.getGameTime())) {
            var lab = context == null ? ArchitectLab.debugContext(actor) : context;
            if (lab == null) lab = ArchitectWildernessLab.debugContext(actor);
            trace.accept(actor.debugSnapshot(lab == null ? ArchitectDebugSnapshot.Lab.field() : lab, level.getServer().tickRateManager().isFrozen()));
        }
        return trace.latest();
    }

    private static ArchitectDebugSnapshot displayed(Subscription subscription) {
        var trace = subscription.bound.decisionJournal().visual();
        return subscription.historyTick == null ? trace.latest() : trace.atOrBefore(subscription.historyTick);
    }

    private static ArchitectDebugPayload.View view(ServerPlayer player, Subscription subscription, String notice) {
        var frame = subscription.bound == null ? null : displayed(subscription);
        if (frame == null && notice.isEmpty()) notice = "Waiting for a server snapshot; use debug live if history has expired.";
        return new ArchitectDebugPayload.View(true, subscription.layers, subscription.historyTick != null,
                player.getServer().tickRateManager().isFrozen(), frame, notice);
    }

    private static void publish(ServerPlayer player, Subscription subscription, String notice) {
        var view = view(player, subscription, notice);
        PacketDistributor.sendToPlayer(player, ArchitectDebugPayload.of(view));
        subscription.sent = view.snapshot();
        subscription.sentFrozen = view.serverFrozen();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        var subscriptions = SERVERS.get(server);
        if (subscriptions == null) return;
        var logged = new HashSet<UUID>();
        var iterator = subscriptions.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var subscription = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.hasPermissions(2) || !player.level().dimension().equals(subscription.dimension)) {
                iterator.remove(); release(subscription.bound, subscriptions);
                if (player != null) PacketDistributor.sendToPlayer(player, ArchitectDebugPayload.off());
                continue;
            }
            ArchitectEntity actor = resolve(server, subscription);
            if (actor == null) {
                if (subscription.bound != null) {
                    var previous = subscription.bound; subscription.bound = null; release(previous, subscriptions);
                    publish(player, subscription, "Waiting for the lab Architect. Use debug off to stop following.");
                }
                if (subscription.follow == Follow.ENTITY) { iterator.remove(); PacketDistributor.sendToPlayer(player, ArchitectDebugPayload.off()); }
                continue;
            }
            bind(subscription, actor, subscriptions);
            boolean frozenChanged = subscription.sentFrozen != server.tickRateManager().isFrozen();
            capture(actor, frozenChanged || actor.level().getGameTime() <= subscription.forceThrough &&
                    (subscription.sent == null || subscription.sent.gameTick() != actor.level().getGameTime()));
            var current = displayed(subscription);
            if (current != subscription.sent || frozenChanged) publish(player, subscription, "");
            if (subscription.logging && current != null && logged.add(actor.getUUID())
                    && (subscription.lastLogged == Long.MIN_VALUE || current.gameTick() - subscription.lastLogged >= 100)) {
                LOGGER.info("[ArchitectVisual] {}", current.logLine());
                subscription.lastLogged = current.gameTick();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void stopping(ServerStoppingEvent event) {
        var subscriptions = SERVERS.remove(event.getServer());
        if (subscriptions != null) for (var subscription : subscriptions.values())
            if (subscription.bound != null) subscription.bound.decisionJournal().visual().setEnabled(false);
    }

    private static void reply(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }
}
