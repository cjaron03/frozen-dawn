#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;

uniform vec2 InSize;
uniform vec2 ScreenSize;
uniform float ThermalAmount;
uniform float AmbientBaseline;
uniform float HeatField0X;
uniform float HeatField0Y;
uniform float HeatField0Radius;
uniform float HeatField0Intensity;
uniform float HeatField1X;
uniform float HeatField1Y;
uniform float HeatField1Radius;
uniform float HeatField1Intensity;
uniform float HeatField2X;
uniform float HeatField2Y;
uniform float HeatField2Radius;
uniform float HeatField2Intensity;
uniform float HeatField3X;
uniform float HeatField3Y;
uniform float HeatField3Radius;
uniform float HeatField3Intensity;
uniform float HeatField4X;
uniform float HeatField4Y;
uniform float HeatField4Radius;
uniform float HeatField4Intensity;
uniform float HeatField5X;
uniform float HeatField5Y;
uniform float HeatField5Radius;
uniform float HeatField5Intensity;
uniform float ColdField0X;
uniform float ColdField0Y;
uniform float ColdField0Radius;
uniform float ColdField0Intensity;
uniform float ColdField1X;
uniform float ColdField1Y;
uniform float ColdField1Radius;
uniform float ColdField1Intensity;
uniform float ColdField2X;
uniform float ColdField2Y;
uniform float ColdField2Radius;
uniform float ColdField2Intensity;
uniform float ColdField3X;
uniform float ColdField3Y;
uniform float ColdField3Radius;
uniform float ColdField3Intensity;
uniform float ColdField4X;
uniform float ColdField4Y;
uniform float ColdField4Radius;
uniform float ColdField4Intensity;
uniform float ColdField5X;
uniform float ColdField5Y;
uniform float ColdField5Radius;
uniform float ColdField5Intensity;

out vec4 fragColor;

float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

vec3 thermalPalette(float t) {
    vec3 c0 = vec3(0.01, 0.02, 0.05);
    vec3 c1 = vec3(0.07, 0.08, 0.22);
    vec3 c2 = vec3(0.22, 0.08, 0.42);
    vec3 c3 = vec3(0.56, 0.09, 0.58);
    vec3 c4 = vec3(0.92, 0.25, 0.34);
    vec3 c5 = vec3(0.99, 0.55, 0.08);
    vec3 c6 = vec3(1.00, 0.86, 0.20);
    vec3 c7 = vec3(1.00, 1.00, 0.96);

    if (t < 0.12) return mix(c0, c1, smoothstep(0.00, 0.12, t));
    if (t < 0.28) return mix(c1, c2, smoothstep(0.12, 0.28, t));
    if (t < 0.46) return mix(c2, c3, smoothstep(0.28, 0.46, t));
    if (t < 0.64) return mix(c3, c4, smoothstep(0.46, 0.64, t));
    if (t < 0.80) return mix(c4, c5, smoothstep(0.64, 0.80, t));
    if (t < 0.92) return mix(c5, c6, smoothstep(0.80, 0.92, t));
    return mix(c6, c7, smoothstep(0.92, 1.00, t));
}

float heatFieldContribution(vec2 uv, float x, float y, float radius, float intensity) {
    if (radius <= 0.0 || intensity <= 0.0) {
        return 0.0;
    }

    vec2 delta = uv - vec2(x, y);
    delta.x *= ScreenSize.x / max(ScreenSize.y, 1.0);
    float dist = length(delta);
    float normalized = dist / max(radius, 0.0001);
    float falloff = exp(-(normalized * normalized) * 1.35);
    float centerSoftener = 0.78 + 0.22 * smoothstep(0.0, 0.60, normalized);
    return intensity * falloff * centerSoftener;
}

void main() {
    vec4 scene = texture(DiffuseSampler, texCoord);
    vec2 texel = 1.0 / InSize;
    float base = luminance(scene.rgb);
    float north = luminance(texture(DiffuseSampler, texCoord + vec2(0.0, -texel.y)).rgb);
    float south = luminance(texture(DiffuseSampler, texCoord + vec2(0.0, texel.y)).rgb);
    float east = luminance(texture(DiffuseSampler, texCoord + vec2(texel.x, 0.0)).rgb);
    float west = luminance(texture(DiffuseSampler, texCoord + vec2(-texel.x, 0.0)).rgb);
    float northEast = luminance(texture(DiffuseSampler, texCoord + vec2(texel.x, -texel.y)).rgb);
    float northWest = luminance(texture(DiffuseSampler, texCoord + vec2(-texel.x, -texel.y)).rgb);
    float southEast = luminance(texture(DiffuseSampler, texCoord + vec2(texel.x, texel.y)).rgb);
    float southWest = luminance(texture(DiffuseSampler, texCoord + vec2(-texel.x, texel.y)).rgb);
    float localMean = (base + north + south + east + west + northEast + northWest + southEast + southWest) / 9.0;
    float detailOutlier = abs(base - localMean);
    float detailSuppression = 1.0 - smoothstep(0.035, 0.18, detailOutlier);
    float stableBase = mix(base, localMean, 0.82);
    float edge = clamp(
        abs(localMean - north) + abs(localMean - south) + abs(localMean - east) + abs(localMean - west),
        0.0,
        1.0
    ) * detailSuppression;

    float blueLift = max(scene.b - max(scene.r, scene.g), 0.0);
    float cyanLift = max(min(scene.g, scene.b) - scene.r, 0.0);
    float warmBias = max(scene.r - max(scene.g, scene.b), 0.0);
    float coldBias = max(scene.b - scene.r, 0.0);
    float coldGlow = max(blueLift, cyanLift * 1.10);
    float cryoMaterial = smoothstep(0.14, 0.42, coldGlow + coldBias * 0.75);
    cryoMaterial *= smoothstep(0.08, 0.34, stableBase + localMean * 0.35);
    float splatHeat = max(scene.r - scene.b * 0.45, 0.0);
    splatHeat += max(scene.g - scene.b * 0.70, 0.0) * 0.40;
    splatHeat = clamp(splatHeat, 0.0, 1.0);

    float structureHeat = pow(stableBase, 1.02);
    structureHeat += edge * 0.08;
    structureHeat += warmBias * 0.01;
    structureHeat -= coldBias * 0.58;
    structureHeat -= coldGlow * 0.62;
    structureHeat -= cryoMaterial * 0.72;
    structureHeat -= detailOutlier * 0.26;
    structureHeat = clamp(structureHeat, 0.0, 1.0);

    // Keep the readable scene backbone, but keep the no-anchor world in the
    // cold end of the palette. Warm bands should come primarily from actual
    // thermal splats rather than from bright/luminous scene detail.
    float sceneHeat = 0.012 + structureHeat * 0.052 + AmbientBaseline * 0.070;
    sceneHeat += edge * 0.010;
    sceneHeat -= coldGlow * 0.14;
    sceneHeat -= coldBias * 0.08;
    sceneHeat -= cryoMaterial * 0.18;
    sceneHeat = clamp(sceneHeat, 0.0, 0.11);

    float sourceBias = 0.0;
    sourceBias += heatFieldContribution(texCoord, HeatField0X, HeatField0Y, HeatField0Radius, HeatField0Intensity);
    sourceBias += heatFieldContribution(texCoord, HeatField1X, HeatField1Y, HeatField1Radius, HeatField1Intensity);
    sourceBias += heatFieldContribution(texCoord, HeatField2X, HeatField2Y, HeatField2Radius, HeatField2Intensity);
    sourceBias += heatFieldContribution(texCoord, HeatField3X, HeatField3Y, HeatField3Radius, HeatField3Intensity);
    sourceBias += heatFieldContribution(texCoord, HeatField4X, HeatField4Y, HeatField4Radius, HeatField4Intensity);
    sourceBias += heatFieldContribution(texCoord, HeatField5X, HeatField5Y, HeatField5Radius, HeatField5Intensity);

    float coldBiasField = 0.0;
    coldBiasField += heatFieldContribution(texCoord, ColdField0X, ColdField0Y, ColdField0Radius, ColdField0Intensity);
    coldBiasField += heatFieldContribution(texCoord, ColdField1X, ColdField1Y, ColdField1Radius, ColdField1Intensity);
    coldBiasField += heatFieldContribution(texCoord, ColdField2X, ColdField2Y, ColdField2Radius, ColdField2Intensity);
    coldBiasField += heatFieldContribution(texCoord, ColdField3X, ColdField3Y, ColdField3Radius, ColdField3Intensity);
    coldBiasField += heatFieldContribution(texCoord, ColdField4X, ColdField4Y, ColdField4Radius, ColdField4Intensity);
    coldBiasField += heatFieldContribution(texCoord, ColdField5X, ColdField5Y, ColdField5Radius, ColdField5Intensity);

    float hotLift = smoothstep(0.10, 0.34, sourceBias) * 0.16;
    sceneHeat = clamp(sceneHeat + clamp(sourceBias, 0.0, 0.38) + hotLift - clamp(coldBiasField, 0.0, 0.28), 0.0, 1.0);
    sceneHeat += smoothstep(0.30, 0.82, splatHeat) * 0.56;
    sceneHeat += smoothstep(0.58, 0.96, splatHeat) * 0.18;
    sceneHeat = clamp(sceneHeat, 0.0, 1.0);

    vec3 thermal = thermalPalette(sceneHeat);
    float structure = clamp(stableBase * 0.45 + edge * 0.45, 0.0, 1.0);
    float shade = mix(0.70, 1.10, structure);
    thermal *= shade;
    thermal += vec3(edge * 0.04, edge * 0.015, edge * 0.06);
    thermal = clamp(thermal, 0.0, 1.0);
    vec3 neutralScene = vec3(stableBase * 0.07);
    vec3 finalColor = mix(neutralScene, thermal, clamp(ThermalAmount, 0.0, 1.0));
    fragColor = vec4(finalColor, 1.0);
}
