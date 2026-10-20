package com.frozendawn.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;

/** Bounded, two-voice rolling overlap per bed; source changes retire the old voices together. */
final class WindBedLayer {
    private record Voice(WindBedSound sound,SoundEvent event) {}
    private final List<Voice> voices=new ArrayList<>();
    private SoundEvent current;
    private int untilNext;
    void tick(Minecraft mc,SoundEvent event,float gain,int duration,int overlap,boolean shaped,float fadeRate) {
        voices.removeIf(v->v.sound.isStopped());
        // Retiring source/overlap tails must duck with the active voices during attack cues.
        for(var v:voices)v.sound.fadeRate(fadeRate);
        if(event!=current) {
            for(var v:voices)v.sound.retire();current=event;untilNext=0;
        }
        for(var v:voices)if(v.event==event) {
            if(gain>.0001f)v.sound.resume();
            if(!v.sound.retired())v.sound.target(gain);
        }
        if(gain<=.0001f) {
            for(var v:voices)v.sound.retire();
            if(untilNext>0)untilNext--;return;
        }
        // An aborted/stopped voice must not leave a silent timer until the old clip's scheduled end.
        boolean live=voices.stream().anyMatch(v->v.event==event&&!v.sound.retired());
        if(untilNext>0 && live){untilNext--;return;}
        // Fast debug-stage changes may leave tails. Bound the live voice count without letting a
        // cancelled source keep playing untracked after logout or a dimension transition.
        if(voices.size()>=4) {var old=voices.removeFirst();mc.getSoundManager().stop(old.sound);old.sound.stopNow();}
        var sound=new WindBedSound(event,duration,shaped,overlap,gain);sound.target(gain);sound.fadeRate(fadeRate);
        voices.add(new Voice(sound,event));mc.getSoundManager().play(sound);untilNext=duration-overlap-1;
    }
    void stop(Minecraft mc) {
        for(var v:voices){v.sound.stopNow();mc.getSoundManager().stop(v.sound);}voices.clear();current=null;untilNext=0;
    }
}
