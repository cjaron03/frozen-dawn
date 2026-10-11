package com.frozendawn.entity;

import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.entity.architect.ArchitectBlockEnvironment;
import com.frozendawn.entity.blind.BlindArchitectPolicy;
import com.frozendawn.entity.blind.BlindRetreatNavigation;
import com.frozendawn.entity.blind.BlindArchitectSensing;
import com.frozendawn.entity.blind.BlindAcousticPropagation;
import com.frozendawn.entity.blind.BlindSignalMemory;
import com.frozendawn.entity.blind.BlindSignalMemory.Signal;
import com.frozendawn.entity.blind.BlindSignalMemory.Sense;
import com.frozendawn.entity.blind.BlindSensingBudget;
import com.frozendawn.entity.blind.BlindVibrationPropagation;
import com.frozendawn.entity.blind.BlindVibrationPropagation.Kind;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.init.ModSounds;
import com.frozendawn.maeve.MaeveDirector;
import com.frozendawn.world.GeothermalCoreRegistry;
import com.frozendawn.world.HeaterRegistry;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/** Standalone acoustic executor. Ordinary target, scout and combat controllers never run here. */
public final class BlindArchitectController {
    private record Return(Signal signal, BlindArchitectSensing.Result path, long due, boolean noise,
                          boolean explosion, BlindAcousticPropagation.Result echo) {
        Return(Signal signal, BlindArchitectSensing.Result path, long due, boolean noise, boolean explosion) {
            this(signal,path,due,noise,explosion,null);
        }
    }
    private final ArchitectEntity actor;
    private final ArchitectBlockBreaker breaker;
    private final BlindSignalMemory memory = new BlindSignalMemory();
    private final ArrayDeque<Return> pending = new ArrayDeque<>();
    private final java.util.LinkedHashMap<BlockPos, Long> missed = new java.util.LinkedHashMap<>();
    private long budgetTick = Long.MIN_VALUE, nextClick, nextRoute, nextRetreatRoute, nextAttack, noiseUntil, stunUntil, fleeUntil, deferredUntil;
    private long physicalExplosion = Long.MIN_VALUE, nextMessage, lastVocal;
    private int work, airWork, structuralWork;
    private long listenStarted = Long.MIN_VALUE;
    private float listenYaw;
    private long surveyStarted = Long.MIN_VALUE;
    private float surveyYaw;
    private long digTellUntil;
    private Signal digSignal;
    private Vec3 fleeOrigin;
    private UUID locked;
    private Signal selected;
    private String state = "WANDER";
    private int unknownReturns;
    private BlindVibrationPropagation.Result lastVibration;
    private BlindAcousticPropagation.Result lastEcho;
    public BlindArchitectController(ArchitectEntity actor, ArchitectBlockBreaker breaker) { this.actor = actor; this.breaker = breaker; }
    private ServerLevel level() { return (ServerLevel) actor.level(); }
    private long now() { return actor.level().getGameTime(); }
    private boolean erased() { return PostMaeveWorldState.isErased(level()); }
    private int remaining() {
        if (budgetTick != now()) { budgetTick = now(); work = 0; airWork = 0; structuralWork = 0; }
        return Math.min(BlindArchitectPolicy.ACTOR_WORK - work, BlindSensingBudget.forLevel(level()).remaining(now()));
    }
    private void spend(int count) { work += count; BlindSensingBudget.forLevel(level()).spend(now(), count); }
    private void spendAir(int count) { spend(count); airWork += count; }
    private BlindArchitectSensing.Result query(Vec3 from, Vec3 to, int range) {
        return query(from,to,range,0);
    }
    private BlindArchitectSensing.Result query(Vec3 from, Vec3 to, int range, int reserve) {
        int budget = Math.max(0, remaining()-reserve);
        var result = BlindArchitectSensing.air(level(), from, to, range, budget);
        spendAir(result.visited());
        if (result.exhausted()) { unknownReturns++; actor.recordDecision("BLIND_BUDGET", null, "UNKNOWN expansions=" + result.visited()); }
        return result;
    }
    public void tick() {
        actor.setTarget(null); actor.setSprinting(false);
        if (erased()) { actor.blindNoiseStunned(false); if (!state.equals("ERASED")) clear(); click(false); wander(); state = "ERASED"; return; }
        resolve();
        if (now() % 20 == 0) heat();
        actor.blindNoiseStunned(now() < stunUntil);
        if (now() < stunUntil) {
            listenStarted = Long.MIN_VALUE;
            actor.getNavigation().stop(); actor.setDeltaMovement(0, actor.getDeltaMovement().y, 0);
            breaker.clearTarget(); actor.blindAction(ArchitectEntity.ACTION_OBSERVE); state = "STUN"; return;
        }
        if (now() < fleeUntil) { listenStarted = Long.MIN_VALUE; retreat(); click(false); return; }
        if (now() < deferredUntil) { listenStarted = Long.MIN_VALUE; click(false); wander(); state = "ATTENTION_DEFERRED"; return; }
        Signal next = memory.select(actor.blockPosition(), now());
        // A modest margin retains a valid near-equal track instead of spinning between players.
        if (selected != null && memory.snapshot().contains(selected) && next != null && !next.lock(now())
                && actor.blockPosition().distSqr(selected.region()) <= actor.blockPosition().distSqr(next.region()) + 4) next = selected;
        selected = next;
        if (selected == null) { release(); wander(); click(false); return; }
        surveyStarted = Long.MIN_VALUE;
        if (selected.sense() == Sense.VIBRATION) { listen(selected); return; }
        listenStarted = Long.MIN_VALUE;
        if (!MaeveDirector.focusBlind(actor, selected.region())) { evicted(selected.region()); return; }
        boolean pursuit = selected.lock(now());
        if (pursuit && !selected.subject().equals(locked)) acquire(selected);
        if (!pursuit && locked != null) locked = null;
        state = pursuit ? "PURSUE" : selected.fresh(now()) ? "INVESTIGATE" : "SEARCH";
        lookForReturn(selected);
        click(true);
        if (pursuit) melee();
        navigate(selected);
    }
    private void lookForReturn(Signal signal) {
        double dx=signal.region().getX()+.5-actor.getX(),dz=signal.region().getZ()+.5-actor.getZ();
        long stale=Math.max(0,now()-signal.observed()-40);
        if(signal.sense()!=Sense.ECHO||stale==0) {
            actor.getLookControl().setLookAt(signal.region().getX()+.5,
                    signal.region().getY()+1.5,signal.region().getZ()+.5);
            return;
        }
        // Only the remembered measurement and its age guide this sweep. Fresh
        // returns focus it again; missing returns widen the next sampled bearings.
        double distance=Math.max(1,Math.hypot(dx,dz));
        double uncertainty=Math.toDegrees(Math.atan2(signal.uncertainty()+.5,distance));
        double growth=Math.min(1,stale/(double)(BlindArchitectPolicy.SEARCH_TICKS-40));
        double halfArc=Math.min(100,uncertainty+75*growth)*Math.min(1,stale/20.0);
        float yaw=(float)(Math.toDegrees(Math.atan2(-dx,dz))+halfArc*Math.sin(stale*Math.PI/40));
        scan(yaw,(signal.region().getY()+1.5-actor.getEyeY())/distance);
    }
    private void scan(float yaw,double slope) {
        if(actor.getNavigation().isDone()) {
            float body=Mth.approachDegrees(actor.yBodyRot,yaw,2);
            actor.setYRot(body);actor.setYBodyRot(body);
        }
        double angle=Math.toRadians(yaw);
        actor.getLookControl().setLookAt(actor.getX()-Math.sin(angle)*4,
                actor.getEyeY()+slope*4,actor.getZ()+Math.cos(angle)*4);
    }
    private void click(boolean sensing) {
        if (now() < nextClick) return;
        boolean pursuit = selected != null && selected.lock(now());
        double distance = selected == null ? 99 : Math.sqrt(actor.blockPosition().distSqr(selected.region()));
        nextClick = now() + BlindArchitectPolicy.cadence(pursuit, selected != null, distance, actor.getRandom().nextInt(100));
        level().playSound(null, actor.getX(), actor.getY() + actor.getEyeHeight(), actor.getZ(),
                ModSounds.BLIND_ARCHITECT_CLICK.get(), SoundSource.HOSTILE, 1.5F, .96F + actor.getRandom().nextFloat() * .08F);
        if (now() - lastVocal > (pursuit ? 140 : 240)) {
            lastVocal = now();
            var sound = pursuit ? ModSounds.BLIND_ARCHITECT_SEARCH : ModSounds.BLIND_ARCHITECT_IDLE;
            level().playSound(null, actor.blockPosition(), sound.get(), SoundSource.HOSTILE, .65F, 1F);
        }
        // Wandering still surveys. Only erasure and attention disengagement disable new returns.
        if (erased() || now() < deferredUntil || now() < fleeUntil) return;
        var candidates = new java.util.ArrayList<ServerPlayer>();
        level().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(ServerPlayer.class), actor.getBoundingBox().inflate(24),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator(), candidates, 32);
        var players = candidates.stream()
                .sorted(Comparator.comparingDouble((ServerPlayer p) -> actor.distanceToSqr(p)).thenComparing(Entity::getUUID)).limit(16).toList();
        Vec3 from = actor.getEyePosition();
        for (var player : players) {
            if (pending.size() >= BlindArchitectPolicy.MAX_RETURNS || remaining() <= 0) { unknownReturns++; break; }
            // Wandering and structural listening both leave half the actor budget
            // for an incoming contact. The first footstep must not need an earlier
            // vibration belief to receive capacity. Spent structural work counts.
            remaining();
            int reserve = selected == null || (selected.sense() == Sense.VIBRATION && now()-selected.observed()<=40)
                    ? Math.max(0,256-structuralWork) : 0;
            var result = BlindAcousticPropagation.echo(level(),from,actor.getYHeadRot(),actor.getXRot(),
                    player.getBoundingBox(),now(),Math.max(0,remaining()-reserve));
            spendAir(result.work()); lastEcho=result;
            if(result.unknown()) unknownReturns++;
            actor.recordDecision("BLIND_ECHO_QUERY",null,"emitted="+result.emitted()+" outcome="+result.outcome()
                    +" outward="+result.outwardLength()+" return="+result.returnLength()+" arrivalMs="+result.arrivalMillis()
                    +" gain="+result.directivity()+" energy="+result.received()+" clutter="+result.clutter()
                    +" snr="+result.signalToNoise()+" work="+result.work()+" measurement="+result.measurement());
            if (!result.detected()) continue;
            var measured=result.measurement();
            var signal = new Signal(Sense.ECHO,measured.region(),measured.uncertainty(),measured.confidence(),
                    measured.range(),now(),player.getUUID(),measured.qualityCeiling());
            pending.addLast(new Return(signal,null,result.due(),false,false,result));
            actor.recordDecision("BLIND_PULSE", null, "region=" + measured.region() + " uncertainty=" + measured.uncertainty()
                    +" bearing="+measured.bearing()+" range="+measured.range()+" due="+result.due());
        }
    }
    private void resolve() {
        for (var returned : List.copyOf(pending)) {
            if (returned.due > now()) continue;
            if (returned.echo != null) {
                var validation=BlindAcousticPropagation.validate(level(),returned.echo,actor.getEyePosition(),remaining());
                spendAir(validation.work());
                if(validation.outcome().name().startsWith("UNKNOWN_")) {
                    if(now()>returned.due+4) {
                        pending.remove(returned); unknownReturns++;
                        actor.recordDecision("BLIND_ECHO_RETURN",null,"emitted="+returned.echo.emitted()+" outcome="+validation.outcome());
                    }
                    continue;
                }
                actor.recordDecision("BLIND_ECHO_RETURN",null,"emitted="+returned.echo.emitted()+" outcome="
                        +(validation.open()?"ADMITTED":validation.outcome())+" work="+validation.work());
                if(!validation.open()) { pending.remove(returned); continue; }
            } else if (returned.signal.sense() != Sense.VIBRATION) {
                int checks = returned.path.path().size();
                if (checks > remaining()) { if (now() > returned.due + 4) { pending.remove(returned); unknownReturns++; } continue; }
                spendAir(checks);
                if (!BlindArchitectSensing.stillOpen(level(), returned.path)) { pending.remove(returned); continue; }
            }
            pending.remove(returned);
            if (returned.noise) { noise(returned.signal.region().getCenter(), returned.explosion); continue; }
            if (now() - returned.signal.observed() <= BlindArchitectPolicy.FRESH_TICKS
                    && missed.getOrDefault(returned.signal.region(), 0L) <= now()) memory.add(returned.signal);
        }
    }
    public void hear(Holder<GameEvent> event, GameEvent.Context context, Vec3 position) {
        if (!actor.isBlindArchitect() || actor.isNoAi() || !actor.isAlive() || erased() || now() < deferredUntil || now() < fleeUntil) return;
        Entity source = context.sourceEntity();
        BlockPos block = BlockPos.containing(position);
        if (!level().hasChunkAt(block)) return;
        boolean bell = event == GameEvent.BLOCK_CHANGE && level().getBlockState(block).getBlock() instanceof BellBlock;
        boolean explosion = event == GameEvent.EXPLODE;
        if (bell || explosion) {
            if (now() < noiseUntil || pending.size() >= BlindArchitectPolicy.MAX_RETURNS) return;
            var result = query(actor.getEyePosition(), position.add(0, bell ? .5 : 0, 0), bell ? 8 : 12);
            if (result.reached()) pending.addLast(new Return(new Signal(Sense.HEARING, block, 1, .9, result.cost(), now(), null), result, now() + 1, true, explosion));
            return;
        }
        if (source instanceof ArchitectEntity || source instanceof ServerPlayer p && (p.isCreative() || p.isSpectator())) return;
        int range = 0; double strength = 0;
        if (event == GameEvent.EAT || event == GameEvent.DRINK) range = 8;
        else if (event == GameEvent.STEP || event == GameEvent.HIT_GROUND) { range = source != null && source.isSprinting() || event == GameEvent.HIT_GROUND ? 20 : 12; strength = range == 20 ? 10 : 6; }
        else if (event == GameEvent.BLOCK_OPEN || event == GameEvent.BLOCK_CLOSE) range = 16;
        else if (event == GameEvent.PROJECTILE_LAND) range = 20;
        else if (event == GameEvent.BLOCK_DESTROY || event == GameEvent.BLOCK_PLACE) { range = 24; strength = 24; }
        else if (event == GameEvent.NOTE_BLOCK_PLAY || event == GameEvent.BLOCK_ACTIVATE || event == GameEvent.BLOCK_DEACTIVATE) { range = 24; strength = 10; }
        if (range == 0) return;
        if (strength > 0) {
            if (event == GameEvent.STEP || event == GameEvent.HIT_GROUND) {
                if (source != null) vibrate(BlindVibrationPropagation.foot(source,
                        event == GameEvent.HIT_GROUND ? Kind.LANDING : Kind.FOOTSTEP, strength, now()));
            } else {
                var material = context.affectedState() != null ? context.affectedState() : level().getBlockState(block);
                vibrate(BlindVibrationPropagation.block(Kind.BLOCK_IMPACT, block, material, position, strength, now()));
            }
        }
        // STEP is emitted at the floor boundary; sample the exposed air above
        // that contact instead of starting a sound ray inside its support.
        // Vanilla block impacts can report the struck block's center, inside its collision shape.
        // Use the projectile's last physical air position near that collision. Owner metadata
        // remains excluded: emit() admits only an anonymous frozen hearing region.
        Vec3 audible = position;
        if (event == GameEvent.PROJECTILE_LAND && source instanceof net.minecraft.world.entity.projectile.Projectile
                && source.position().distanceToSqr(position) <= 9) audible = source.position();
        else if (event == GameEvent.STEP || event == GameEvent.HIT_GROUND) audible = position.add(0,.15,0);
        emit(audible, source, range, net.minecraft.core.registries.BuiltInRegistries.GAME_EVENT.getKey(event.value()).toString());
    }
    public void emit(Vec3 position, Entity source, int range) {
        emit(position,source,range,"ACTIVITY");
    }
    public void emit(Vec3 position, Entity source, int range, String kind) {
        if (!actor.isBlindArchitect() || actor.isNoAi() || !actor.isAlive() || erased() || now() < deferredUntil || now() < fleeUntil
                || source instanceof ArchitectEntity || pending.size() >= BlindArchitectPolicy.MAX_RETURNS) return;
        BlockPos block = BlockPos.containing(position);
        if (!level().hasChunkAt(block)) return;
        if (range > 0) {
            var air = query(actor.getEyePosition(), position, range);
            actor.recordDecision("BLIND_AIR",null,"kind="+kind+" emitted="+now()+" range="+range
                    +" outcome="+(air.reached()?"RECEIVED":air.exhausted()?"UNKNOWN_BUDGET":"NO_PATH_WITHIN_RANGE")
                    +" length="+air.cost()+" work="+air.visited());
            if (air.reached()) admit(new Signal(Sense.HEARING, BlindArchitectSensing.quantize(block, 2), 2, .55, air.cost(), now(), null));
        }
    }
    public BlindVibrationPropagation.Result vibrate(BlindVibrationPropagation.Impulse impulse) {
        if (!actor.isBlindArchitect() || actor.isNoAi() || !actor.isAlive() || erased()
                || now() < deferredUntil || now() < fleeUntil) return null;
        // Reserve only the air share still unspent this tick. Reserving another128
        // after a scan already consumed it stranded usable capacity in live steps.
        int available = remaining();
        var result = BlindVibrationPropagation.receive(level(), impulse, actor,
                Math.max(0, available - Math.max(0, 128 - airWork)));
        spend(result.work()); structuralWork += result.work(); lastVibration = result;
        if (result.unknown()) unknownReturns++;
        actor.recordDecision("BLIND_VIBRATION", null, "kind=" + impulse.kind() + " emitted=" + impulse.emitted()
                + " outcome=" + result.outcome() + " strength=" + result.received() + " length=" + result.length()
                + " loss=" + result.loss() + " work=" + result.work() + " sourceContacts=" + result.sourceContacts()
                + " receiverContacts=" + result.receiverContacts() + " path=" + result.path());
        if (result.detected()) {
            // A scalar substrate measurement says something happened on this structure.
            // No source coordinate, route or identity becomes a navigation destination.
            memory.add(new Signal(Sense.VIBRATION, actor.blockPosition(), BlindVibrationPropagation.RANGE,
                    .45, 0, now(), null));
        }
        return result;
    }
    private void listen(Signal signal) {
        locked = null; MaeveDirector.releaseBlind(actor); breaker.clearTarget();
        actor.getNavigation().stop(); actor.blindAction(ArchitectEntity.ACTION_OBSERVE); state = "LISTEN";
        long age = now() - signal.observed();
        if (age > 40) { memory.miss(signal); selected = null; listenStarted = Long.MIN_VALUE; wander(); return; }
        if (listenStarted == Long.MIN_VALUE) {
            listenStarted = now(); listenYaw = actor.getYRot(); nextClick = now(); lastVocal = now();
            level().playSound(null, actor.blockPosition(), ModSounds.BLIND_ARCHITECT_SEARCH.get(), SoundSource.HOSTILE, .65F, 1F);
        }
        // The episode keeps one phase through refreshed footsteps. Its orientation
        // comes only from the actor's entry pose, never the impulse's source.
        long elapsed = now()-listenStarted;
        float bodyYaw = listenYaw + (float)(60*Math.sin(elapsed*Math.PI/40));
        actor.setYRot(bodyYaw); actor.setYBodyRot(bodyYaw);
        double yaw = Math.toRadians(bodyYaw + 25*Math.sin(elapsed*Math.PI/20));
        actor.getLookControl().setLookAt(actor.getX() - Math.sin(yaw) * 4,
                actor.getEyeY(), actor.getZ() + Math.cos(yaw) * 4);
        click(true);
    }
    public BlindVibrationPropagation.Result lastVibration() { return lastVibration; }
    public BlindAcousticPropagation.Result lastEcho() { return lastEcho; }
    private void admit(Signal signal) {
        missed.entrySet().removeIf(e -> e.getValue() <= now());
        if (missed.keySet().stream().anyMatch(p -> p.distSqr(signal.region()) <= 16)) return;
        memory.add(signal);
    }
    private void acquire(Signal signal) {
        locked = signal.subject();
        MaeveDirector.reportBlind(actor, signal);
        var entity = level().getEntity(locked);
        if (entity instanceof ServerPlayer player && now() >= nextMessage) {
            player.displayClientMessage(Component.translatable("message.frozendawn.blind_architect.found"), true);
            nextMessage = now() + 200;
        }
        level().playSound(null, actor.blockPosition(), ModSounds.BLIND_ARCHITECT_LOCK.get(), SoundSource.HOSTILE, 1F, 1F);
        lastVocal = now(); actor.recordDecision("BLIND_LOCK", null, "region=" + signal.region() + " uncertainty=" + signal.uncertainty());
    }
    private void melee() {
        if (selected == null || selected.travel() > 8 || now() - selected.observed() > 25 || now() < nextAttack) return;
        // Current geometry is allowed solely for an actual local strike, never for navigation.
        for (var player : level().getEntitiesOfClass(ServerPlayer.class, actor.getBoundingBox().inflate(2.0),
                p -> p.getUUID().equals(selected.subject()) && p.isAlive() && !p.isCreative() && !p.isSpectator())) {
            if (actor.distanceToSqr(player) > 2.8 * 2.8 || !actor.hasLineOfSight(player)) continue;
            actor.blindAction(ArchitectEntity.ACTION_ATTACK_MELEE); actor.swing(InteractionHand.MAIN_HAND);
            actor.doHurtTarget(player); nextAttack = now() + 20; break;
        }
    }
    private void navigate(Signal signal) {
        actor.blindAction(ArchitectEntity.ACTION_APPROACH);
        if (breaker.hasTarget()) {
            if (digSignal != null && digSignal.region().distSqr(signal.region()) > 16
                    || ArchitectBlockEnvironment.breakRejection(level(), breaker.getTarget(), List.of()) != null) { breaker.clearTarget(); return; }
            if (now() < digTellUntil) { actor.getNavigation().stop(); state = "DIG_TELL"; return; }
            int remainingTicks = breaker.debugRequiredTicks() - breaker.debugProgressTicks();
            if (!level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                    || !signal.fresh(now()) && (remainingTicks > signal.observed() + BlindArchitectPolicy.SEARCH_TICKS - now())) {
                breaker.clearTarget(); return;
            }
            actor.getNavigation().stop(); if (breaker.tick()) nextClick = now(); state = "DIG"; return;
        }
        BlockPos goal = groundGoal(signal);
        // Uncertainty describes the estimate, not an arrival radius. Otherwise a
        // muffled return lets the actor stop on the wrong side of a wall.
        if (actor.position().distanceToSqr(Vec3.atBottomCenterOf(goal)) <= 2) {
            actor.getNavigation().stop();
            if (now() - signal.observed() > 60 && signal.sense() != Sense.ECHO) {
                memory.miss(signal); if (missed.size() >= 8) missed.remove(missed.keySet().iterator().next()); missed.put(signal.region(), now() + 200);
            }
            return;
        }
        if (now() >= nextRoute) {
            nextRoute = now() + 20;
            if (level().hasChunkAt(goal)) actor.getNavigation().moveTo(goal.getX() + .5, goal.getY(), goal.getZ() + .5, 1.05);
        }
        if (!signal.fresh(now()) || !level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        actor.getLookControl().setLookAt(signal.region().getX() + .5, signal.region().getY() + 1.5, signal.region().getZ() + .5);
        Vec3 delta = signal.region().getCenter().subtract(actor.position());
        var direction = Math.abs(delta.x) >= Math.abs(delta.z)
                ? (delta.x >= 0 ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST)
                : (delta.z >= 0 ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH);
        BlockPos adjacent = actor.blockPosition().relative(direction);
        if (!actor.getNavigation().isDone() && !actor.horizontalCollision) return;
        for (BlockPos block : List.of(adjacent, adjacent.above(), adjacent.relative(direction), adjacent.relative(direction).above())) {
            if (!level().hasChunkAt(block)) continue;
            if (actor.position().distanceToSqr(Vec3.atBottomCenterOf(block.atY(actor.blockPosition().getY()))) > 3.25) continue;
            if (ArchitectBlockEnvironment.isOpenablePassage(level().getBlockState(block))) {
                ArchitectBlockEnvironment.keepPassageOpenNear(actor, block); nextClick = now(); return;
            }
            if (ArchitectBlockEnvironment.breakRejection(level(), block, List.of()) != null) continue;
            actor.getNavigation().stop(); breaker.setTarget(block); digSignal = signal; digTellUntil = now() + 12;
            level().playSound(null, actor.blockPosition(), ModSounds.ARCHITECT_MINE.get(), SoundSource.HOSTILE, 1F, .7F); actor.recordDecision("BLIND_DIG", null, "snapshot=" + signal.region()); return;
        }
    }
    private BlockPos groundGoal(Signal signal) {
        // Resolve only the estimate's vertical quantization against loaded local
        // terrain; no player position participates in route selection.
        BlockPos best = signal.region(); double distance = Double.MAX_VALUE;
        for (int y = -signal.uncertainty(); y <= signal.uncertainty(); y++) {
            BlockPos candidate = signal.region().offset(0, y, 0);
            if (!level().hasChunkAt(candidate) || !level().hasChunkAt(candidate.below())) continue;
            if (!level().getBlockState(candidate).getCollisionShape(level(), candidate).isEmpty()
                    || !level().getBlockState(candidate.above()).getCollisionShape(level(), candidate.above()).isEmpty()
                    || !level().getBlockState(candidate.below()).isFaceSturdy(level(), candidate.below(), net.minecraft.core.Direction.UP)) continue;
            double score = Math.abs(y) + Math.abs(candidate.getY() - actor.getY()) * .25;
            if (score < distance) { best = candidate; distance = score; }
        }
        return best;
    }
    private void wander() {
        state = "WANDER";
        actor.blindAction(ArchitectEntity.ACTION_OBSERVE);
        if (actor.getNavigation().isDone() && now() >= nextRoute) {
            nextRoute = now() + 40;
            Vec3 point = DefaultRandomPos.getPos(actor, 6, 3);
            if (point != null && level().hasChunkAt(BlockPos.containing(point))) actor.getNavigation().moveTo(point.x, point.y, point.z, .65);
        }
        if(!actor.getNavigation().isDone()) { surveyStarted=Long.MIN_VALUE;return; }
        if(surveyStarted==Long.MIN_VALUE) { surveyStarted=now();surveyYaw=actor.getYHeadRot(); }
        // A stationary blind animal keeps surveying even after the last memory
        // expires. This sweep is anchored only to its own entry pose.
        scan(surveyYaw+(float)(80*Math.sin((now()-surveyStarted)*Math.PI/80)),0);
    }
    private void heat() {
        var hot = java.util.stream.Stream.concat(HeaterRegistry.nearby(level(), actor.blockPosition(), 4, 16).stream(),
                GeothermalCoreRegistry.nearby(level(), actor.blockPosition(), 4, 16).stream())
                .filter(p -> actor.position().distanceToSqr(p.getCenter()) < 16).findFirst();
        if (hot.isPresent()) {
            actor.hurt(actor.damageSources().onFire(), 3); actor.setRemainingFireTicks(40);
            fleeOrigin = hot.get().getCenter(); fleeUntil = Math.max(fleeUntil, now() + 80); release();
        }
    }
    private void noise(Vec3 position, boolean explosion) {
        if (now() < noiseUntil || erased()) return;
        noiseUntil = now() + BlindArchitectPolicy.NOISE_COOLDOWN;
        if (!explosion || physicalExplosion == Long.MIN_VALUE || now() - physicalExplosion > 2) actor.hurt(actor.damageSources().magic(), 2);
        stunUntil = now() + BlindArchitectPolicy.STUN_TICKS;
        fleeUntil = stunUntil + BlindArchitectPolicy.FLEE_TICKS;
        fleeOrigin = position; nextRetreatRoute = 0; memory.clear(); pending.clear(); release();
        actor.recordDecision("BLIND_NOISE_PAIN", null, "origin=" + position + " explosion=" + explosion);
    }
    public void hurt(DamageSource source) {
        if (erased()) return;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            physicalExplosion = now(); noise(source.getSourcePosition() == null ? actor.position() : source.getSourcePosition(), true); return;
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            if (fleeOrigin == null) fleeOrigin = actor.position().subtract(actor.getLookAngle());
            fleeUntil = Math.max(fleeUntil, now() + 80); release(); return;
        }
        if (source.getDirectEntity() instanceof ServerPlayer player && !player.isCreative() && !player.isSpectator()
                && actor.distanceToSqr(player) <= 9 && actor.hasLineOfSight(player))
            memory.add(new Signal(Sense.ECHO, player.blockPosition(), 1, .85, actor.distanceTo(player), now(), player.getUUID()));
    }
    private void retreat() {
        state = "FLEE"; actor.blindAction(ArchitectEntity.ACTION_RETREAT); breaker.clearTarget();
        var navigation = actor.getNavigation();
        if (fleeOrigin == null) { navigation.stop(); return; }
        if (!navigation.isDone() && !BlindRetreatNavigation.isEscape(actor, navigation.getPath(), fleeOrigin)) {
            navigation.stop();
            actor.setDeltaMovement(0, actor.getDeltaMovement().y, 0);
        }
        if (now() >= nextRetreatRoute) {
            nextRetreatRoute = now() + 20;
            var path = BlindRetreatNavigation.find(actor, fleeOrigin);
            if (path != null) navigation.moveTo(path, 1.6);
            else {
                navigation.stop();
                actor.setDeltaMovement(0, actor.getDeltaMovement().y, 0);
            }
        }
    }
    public void evicted(BlockPos region) {
        memory.clear(); pending.clear(); release(); deferredUntil = now() + 200;
        fleeOrigin = region.getCenter(); nextRetreatRoute = 0; fleeUntil = now() + 40;
        actor.recordDecision("BLIND_DISENGAGE", null, "ATTENTION_DEFERRED region=" + region);
    }
    private void release() { locked = null; selected = null; listenStarted = Long.MIN_VALUE; breaker.clearTarget(); MaeveDirector.releaseBlind(actor); }
    public void clearLocal() { nextRetreatRoute = 0; actor.blindNoiseStunned(false); lastVibration = null; lastEcho = null; memory.clear(); pending.clear(); missed.clear(); locked = null; selected = null; listenStarted = Long.MIN_VALUE; surveyStarted = Long.MIN_VALUE; breaker.clearTarget(); actor.setTarget(null); actor.getNavigation().stop(); }
    public void clear() { clearLocal(); MaeveDirector.releaseBlind(actor); }
    public void save(CompoundTag tag) { tag.putLong("BlindNoiseUntil", noiseUntil); }
    public void load(CompoundTag tag) { clear(); noiseUntil = Math.min(tag.getLong("BlindNoiseUntil"), now() + BlindArchitectPolicy.NOISE_COOLDOWN); nextClick = now() + 40; }
    public BlindSignalMemory.Signal signal() { return selected; }
    public List<Signal> tracks() { return memory.snapshot(); }
    public String diagnostics() { return "blind=" + state + " signal=" + selected + " pending=" + pending.size() + " unknown=" + unknownReturns + " noiseUntil=" + noiseUntil + " vibration=" + lastVibration; }
}
