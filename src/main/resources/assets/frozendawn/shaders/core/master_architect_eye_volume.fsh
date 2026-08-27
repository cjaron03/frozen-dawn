#version 150

uniform sampler2D uScene;
uniform sampler2D uDepth;
uniform mat4 uInverseProjection;
uniform mat4 uCameraWorld;
uniform vec3 uCameraPosition;
uniform float uFar;
uniform vec3 uEyeCenter;
uniform float uInnerRadius;
uniform float uOuterRadius;
uniform float uMinimumY;
uniform float uMaximumY;
uniform float uTime;
uniform vec2 uWind;
uniform float uDensity;
uniform vec3 uStormColor;

in vec2 vUv;

out vec4 fragColor;

#moj_import <frozendawn:master_architect_eye_volume.glsl>

vec3 reconstructWorldPosition(float depth) {
    vec4 clip = vec4(vUv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 view = uInverseProjection * clip;
    view /= max(abs(view.w), 0.000001) * sign(view.w);
    return (uCameraWorld * vec4(view.xyz, 1.0)).xyz;
}

void main() {
    vec4 sceneColor = texture(uScene, vUv);
    float depth = texture(uDepth, vUv).r;
    vec3 worldPosition = reconstructWorldPosition(min(depth, 0.999999));
    vec3 rayDirection = normalize(worldPosition - uCameraPosition);
    float sceneDistance = depth >= 0.999999
            ? uFar
            : length(worldPosition - uCameraPosition);
    float jitter = fdEyeHash31(vec3(gl_FragCoord.xy, 0.0));
    vec2 storm = fdIntegrateEyeStorm(
        uCameraPosition,
        rayDirection,
        sceneDistance,
        uEyeCenter,
        uInnerRadius,
        uOuterRadius,
        uMinimumY,
        uMaximumY,
        uTime,
        uWind,
        uDensity,
        jitter);
    vec3 stormColor = mix(
            uStormColor, vec3(0.84, 0.91, 0.95), storm.y);
    fragColor = vec4(mix(sceneColor.rgb, stormColor, storm.x), sceneColor.a);
}
