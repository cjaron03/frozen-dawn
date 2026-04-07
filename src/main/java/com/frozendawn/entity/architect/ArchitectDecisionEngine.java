package com.frozendawn.entity.architect;

import com.frozendawn.entity.ArchitectEntity;
import net.minecraft.util.RandomSource;

public final class ArchitectDecisionEngine {

    public record Context(
            int currentAction,
            boolean hasTarget,
            float distanceToTarget,
            boolean hasObserved,
            boolean observeDirty,
            float health,
            float maxHealth,
            boolean shouldPreferMeleeOverApproach,
            boolean targetHasLineOfSight,
            boolean canStartMelee,
            int rangedHitsReceived,
            int healCooldown,
            float recentDamage,
            int tacticalIceCount,
            int maxTacticalIce,
            int trapCooldown,
            boolean hasEntrances,
            boolean playerInsideBase,
            boolean nearCorner
    ) {
    }

    public record Decision(int bestAction, float[] scores) {
    }

    public Decision evaluate(Context context, RandomSource random) {
        float[] scores = new float[7];
        scores[ArchitectEntity.ACTION_OBSERVE] = scoreObserve(context);
        scores[ArchitectEntity.ACTION_APPROACH] = scoreApproach(context);
        scores[ArchitectEntity.ACTION_ATTACK_MELEE] = scoreAttackMelee(context);
        scores[ArchitectEntity.ACTION_RETREAT] = scoreRetreat(context);
        scores[ArchitectEntity.ACTION_FORTIFY] = scoreFortify(context);
        scores[ArchitectEntity.ACTION_TRAP_SET] = scoreTrapSet(context);
        scores[ArchitectEntity.ACTION_PEEK] = scorePeek(context);

        float bestScore = -1.0f;
        int bestAction = ArchitectEntity.ACTION_OBSERVE;

        for (int i = 0; i < scores.length; i++) {
            scores[i] *= 0.95f + random.nextFloat() * 0.1f;
            if (i == context.currentAction()) {
                scores[i] *= 1.2f;
            }
            if (scores[i] > bestScore) {
                bestScore = scores[i];
                bestAction = i;
            }
        }

        return new Decision(bestAction, scores);
    }

    private float scoreObserve(Context context) {
        if (!context.hasTarget()) {
            return 0.1f;
        }
        if (context.hasObserved() && !context.observeDirty()) {
            return 0f;
        }
        float score = 0.9f;
        if (!context.hasObserved()) {
            score *= 2.0f;
        }
        if (context.health() < context.maxHealth() * 0.7f) {
            return 0f;
        }
        if (context.observeDirty()) {
            score *= 0.7f;
        }
        if (context.distanceToTarget() < 16.0f) {
            return 0f;
        }
        return score;
    }

    private float scoreApproach(Context context) {
        if (!context.hasTarget()) {
            return 0.1f;
        }
        float score = 0.6f;
        if (context.distanceToTarget() > 16.0f) {
            score *= 1.2f;
        }
        if (context.shouldPreferMeleeOverApproach()) {
            score *= 0.3f;
        }
        if (context.health() < context.maxHealth() * 0.5f) {
            score *= 0.5f;
        }
        if (context.targetHasLineOfSight()) {
            score *= 0.8f;
        }
        if (context.hasObserved()) {
            score *= 1.2f;
        }
        if (context.rangedHitsReceived() > 3) {
            score *= 1.3f;
        }
        return score;
    }

    private float scoreAttackMelee(Context context) {
        if (!context.hasTarget() || !context.canStartMelee()) {
            return 0f;
        }
        float score = 0.8f;
        if (context.distanceToTarget() < 3.0f) {
            score *= 1.5f;
        } else if (context.distanceToTarget() < 4.75f) {
            score *= 1.0f;
        } else {
            score *= 0.2f;
        }
        if (context.health() < context.maxHealth() * 0.5f) {
            score *= 0.6f;
        }
        if (context.health() < context.maxHealth() * 0.3f) {
            score *= 0.6f;
        }
        return score;
    }

    private float scoreRetreat(Context context) {
        if (!context.hasTarget()) {
            return 0f;
        }
        if (context.healCooldown() > 0) {
            return 0f;
        }
        float score = 0.2f;
        float healthPct = context.health() / context.maxHealth();
        if (healthPct < 0.5f) {
            score *= 2.0f;
        }
        if (healthPct < 0.3f) {
            score *= 1.5f;
        }
        if (healthPct < 0.6f) {
            score *= 1.3f;
        }
        if (context.recentDamage() > context.maxHealth() * 0.3f) {
            score *= 1.5f;
        }
        return score;
    }

    private float scoreFortify(Context context) {
        float score = 0.15f;
        if (context.rangedHitsReceived() > 3) {
            score *= 1.5f;
        }
        if (context.tacticalIceCount() >= context.maxTacticalIce()) {
            score *= 0.3f;
        }
        if (context.hasTarget()
                && context.targetHasLineOfSight()
                && context.distanceToTarget() > 8.0f) {
            score *= 1.3f;
        }
        return score;
    }

    private float scoreTrapSet(Context context) {
        if (!context.hasTarget() || context.trapCooldown() > 0 || !context.hasEntrances()) {
            return 0f;
        }
        if (context.tacticalIceCount() >= context.maxTacticalIce()) {
            return 0f;
        }
        float score = 0.35f;
        if (context.playerInsideBase()) {
            score *= 1.3f;
        }
        return score;
    }

    private float scorePeek(Context context) {
        if (!context.hasTarget() || !context.nearCorner()) {
            return 0f;
        }
        return 0.3f * 1.5f;
    }
}
