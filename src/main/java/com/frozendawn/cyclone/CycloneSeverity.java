package com.frozendawn.cyclone;

/** Shared cyclone severity values for presentation and thermal exposure. */
public enum CycloneSeverity {
    MODERATE(-8, 8, 20, 968, .5f,1.4f,.4f), SEVERE(-19, 4, 12, 933, .8f,2.1f,.7f), CATASTROPHIC(-30, 2, 8, 895, 1f,3.2f,1f);
    public final float peakOffset, visibility, gogglesVisibility, pressure, darkness, windChill, windStrength;
    public float intoWindSpeed(){return switch(this){case MODERATE->.70f;case SEVERE->.45f;case CATASTROPHIC->.25f;};}
    public boolean sprintIntoWind(){return this==MODERATE;}
    CycloneSeverity(float offset, float visibility, float goggles, float pressure, float darkness,float windChill,float windStrength) {
        this.peakOffset=offset; this.visibility=visibility; this.gogglesVisibility=goggles;
        this.pressure=pressure; this.darkness=darkness;this.windChill=windChill;this.windStrength=windStrength;
    }
}
