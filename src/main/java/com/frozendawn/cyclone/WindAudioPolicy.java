package com.frozendawn.cyclone;

/** Tick-based gain policy, shared by recorded beds and independent of sound-engine state. */
public final class WindAudioPolicy {
    public static final int CROSSFADE_TICKS=120;
    private WindAudioPolicy() {}
    public static float approach(float current,float target,float step) {
        return current<target?Math.min(target,current+step):Math.max(target,current-step);
    }
    public static float normalWeight(float blend) {return (float)Math.cos(Math.clamp(blend,0,1)*Math.PI/2);}
    public static float stormWeight(float blend) {return (float)Math.sin(Math.clamp(blend,0,1)*Math.PI/2);}
    public static float envelope(int age,int duration,int overlap) {
        if(age<0 || age>=duration)return 0;
        return (float)Math.min(1,Math.min(Math.sin(Math.min(1,age/(double)overlap)*Math.PI/2),
                Math.sin(Math.min(1,(duration-age)/(double)overlap)*Math.PI/2)));
    }
    /** The roar masks distant creatures most; a nearby creature remains faintly audible. */
    public static float mobGain(float maskingStrength,double distance) {
        if(!Float.isFinite(maskingStrength) || !Double.isFinite(distance))return 1;
        double distanceBlend=Math.clamp((distance-2)/10,0,1);
        return (float)(1-Math.clamp(maskingStrength,0,1)*(.55+.35*distanceBlend));
    }
    public static final class Mix {
        private float blend,calm,exposure=1,strength=.75f;
        public void tick(CycloneSample sample,CycloneSeverity severity,float exposed) {
            float target=sample.intensity();
            if(sample.stage()==CycloneStage.BOMBOGENESIS)target=Math.max(target,.18f*(1-sample.calm()));
            blend=approach(blend,target,1f/CROSSFADE_TICKS);
            calm=approach(calm,sample.calm(),1f/CROSSFADE_TICKS);
            exposure=approach(exposure,Math.clamp(exposed,0,1),1f/60);
            float requested=switch(severity){case MODERATE->.75f;case SEVERE->.95f;case CATASTROPHIC->1.1f;};
            strength=approach(strength,requested,1f/CROSSFADE_TICKS);
        }
        public float normal(){return normalWeight(blend)*(1-.9f*calm);}
        public float snow(){return stormWeight(blend)*strength*.28f*(float)Math.sqrt(exposure);}
        public float roar(){return (stormWeight(blend)*strength*.82f+.025f*calm)*(float)Math.sqrt(.12f+.88f*exposure);}
        public float room(){return stormWeight(blend)*strength*.55f*(float)Math.sqrt(1-exposure);}
        public float buffet(){return stormWeight(blend)*strength*.22f*(float)Math.sqrt(1-exposure);}
        public float blend(){return blend;}
    }
}
