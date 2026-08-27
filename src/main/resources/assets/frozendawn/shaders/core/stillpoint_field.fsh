#version 150

uniform sampler2D uScene;
uniform sampler2D uDepth;
uniform mat4 uInverseProjection;
uniform mat4 uCameraWorld;
uniform vec3 uCameraPosition;
uniform float uFar;
uniform vec3 uCenter;
uniform float uRadius;
uniform float uTime;
uniform float uIntensity;
uniform vec4 uRipple0;
uniform vec4 uRipple1;
uniform vec4 uRipple2;
uniform vec4 uRipple3;
uniform vec4 uRippleAges;

in vec2 vUv;
out vec4 fragColor;

vec3 worldPosition(float depth) {
    vec4 clip = vec4(vUv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 view = uInverseProjection * clip;
    view /= max(abs(view.w), 0.000001) * sign(view.w);
    return (uCameraWorld * vec4(view.xyz, 1.0)).xyz;
}

float ripple(vec3 boundary, vec4 source, float age) {
    if (source.w <= 0.0 || age > 2.6) return 0.0;
    vec3 sourceOffset = source.xyz - uCenter;
    if (dot(sourceOffset, sourceOffset) < 0.01) {
        return source.w * (1.0 - smoothstep(0.0, 1.65, age));
    }
    float arc = distance(normalize(boundary - uCenter), normalize(sourceOffset));
    float wave = age * 0.72;
    float ring = 1.0 - smoothstep(0.025, 0.085, abs(arc - wave));
    return ring * source.w * (1.0 - age / 2.6);
}

void main() {
    vec4 base = texture(uScene, vUv);
    float depth = texture(uDepth, vUv).r;
    vec3 visible = worldPosition(min(depth, 0.999999));
    vec3 ray = normalize(visible - uCameraPosition);
    float visibleDistance = depth >= 0.999999
            ? uFar : length(visible - uCameraPosition);

    vec3 offset = uCameraPosition - uCenter;
    float b = dot(offset, ray);
    float c = dot(offset, offset) - uRadius * uRadius;
    float discriminant = b * b - c;
    if (discriminant <= 0.0) {
        fragColor = base;
        return;
    }

    float root = sqrt(discriminant);
    float nearHit = -b - root;
    float farHit = -b + root;
    bool cameraInside = c < 0.0;
    float boundaryDistance = cameraInside ? farHit : nearHit;
    bool crosses = boundaryDistance > 0.0 && boundaryDistance < visibleDistance;
    if (!crosses) {
        fragColor = base;
        return;
    }

    vec3 boundary = uCameraPosition + ray * boundaryDistance;
    vec3 normal = normalize(boundary - uCenter);
    float fresnel = pow(1.0 - abs(dot(normal, ray)), 3.0);
    float idle = sin(uTime * 0.26 + dot(normal, vec3(2.1, 3.7, 1.4))) * 0.5 + 0.5;
    float rippleAmount = ripple(boundary, uRipple0, uRippleAges.x)
            + ripple(boundary, uRipple1, uRippleAges.y)
            + ripple(boundary, uRipple2, uRippleAges.z)
            + ripple(boundary, uRipple3, uRippleAges.w);
    float currentA = sin(boundary.y * 0.105 + boundary.x * 0.043
            + uTime * 1.05);
    float currentB = cos(boundary.z * 0.087 - boundary.y * 0.061
            - uTime * 0.81);
    float currentC = sin((boundary.x - boundary.z) * 0.052
            + currentA * 2.1 + uTime * 0.48);
    float currentD = cos((boundary.x + boundary.z) * 0.024
            - currentB * 1.8 - uTime * 0.33);
    vec2 flow = vec2(currentA + currentC * 0.92 + currentD * 0.38,
            currentB - currentC * 0.82 + currentD * 0.46);
    float sideStrength = cameraInside ? 5.6 : 4.35;
    vec3 cameraRight = normalize(uCameraWorld[0].xyz);
    vec3 cameraUp = normalize(uCameraWorld[1].xyz);
    vec2 boundaryNormal = vec2(dot(normal, cameraRight), dot(normal, cameraUp));
    float refraction = (0.0032 + fresnel * 0.0115
            + rippleAmount * 0.018 + abs(currentD) * 0.0018)
            * uIntensity * sideStrength;
    vec2 warp = (flow * 0.72
            + boundaryNormal * (0.92 + currentC * 0.38 + currentD * 0.22))
            * refraction;
    warp += vec2(sin(vUv.y * 61.0 + uTime * 2.2 + currentB),
            cos(vUv.x * 47.0 - uTime * 1.75 + currentA))
            * refraction * 0.24;
    vec2 warpedUv = clamp(vUv + warp, vec2(0.001), vec2(0.999));
    vec3 shifted;
    shifted.r = texture(uScene, clamp(warpedUv + warp * 0.24,
            vec2(0.001), vec2(0.999))).r;
    shifted.g = texture(uScene, warpedUv).g;
    shifted.b = texture(uScene, clamp(warpedUv - warp * 0.20,
            vec2(0.001), vec2(0.999))).b;
    vec3 negative = vec3(1.0) - shifted;
    float inversion = (0.105 + idle * 0.030 + rippleAmount * 0.040)
            * uIntensity;
    inversion = min(inversion, cameraInside ? 0.18 : 0.13);
    vec3 color = mix(shifted, negative, inversion);
    float oilPhase = currentA * 0.37 + currentB * 0.29 + currentC * 0.43
            + fresnel * 1.8 + uTime * 0.08;
    vec3 oilFilm = 0.5 + 0.5 * cos(6.28318
            * (vec3(0.02, 0.35, 0.69) + oilPhase * 0.115));
    float oilAmount = (0.045 + fresnel * 0.13 + rippleAmount * 0.07)
            * uIntensity;
    vec3 spectralScale = mix(vec3(1.0), 0.72 + oilFilm * 0.52,
            min(oilAmount, 0.24));
    color *= spectralScale;
    fragColor = vec4(color, base.a);
}
