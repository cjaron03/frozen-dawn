#version 150

uniform sampler2D CloudSampler;
uniform vec3 CloudOrigin;
uniform float CloudHeight;
uniform float CloudMask;
uniform float Intensity;
uniform float Time;
uniform float EventPhase;
in vec3 skyDirection;
in vec2 curtainUv;
in vec4 vertexColor;
out vec4 fragColor;

float cloudAt(vec3 direction, float height) {
    float distance = (height - CloudOrigin.y) / max(direction.y, 0.001);
    if (distance <= 0.0) return 0.0;
    vec2 uv = (CloudOrigin.xz + direction.xz * distance) / 3072.0;
    return texture(CloudSampler, fract(uv)).a;
}

float hash1(float x) {
    return fract(sin(x * 127.1 + 311.7) * 43758.5453);
}

float noise1(float x) {
    float cell = floor(x);
    float blend = fract(x);
    blend = blend * blend * (3.0 - 2.0 * blend);
    return mix(hash1(cell), hash1(cell + 1.0), blend);
}

void main() {
    vec3 direction = normalize(skyDirection);
    float cloud = 0.0;
    if (CloudMask > 0.5) {
        // Sample the four-block cloud slab, including its vertical faces.
        cloud = max(cloudAt(direction, CloudHeight), cloudAt(direction, CloudHeight + 2.0));
        cloud = max(cloud, cloudAt(direction, CloudHeight + 4.0));
    }
    float transmission = 1.0 - smoothstep(0.02, 0.30, cloud);
    float u = curtainUv.x;
    float v = curtainUv.y;
    float along = fract(u);
    float edge = smoothstep(0.0, 0.10, along) * (1.0 - smoothstep(0.86, 1.0, along));

    // Narrow, uneven rays remain coherent through the height of the folded mesh.
    float rayU = u + 0.0017 * sin(v * 5.0 + u * 19.0 + Time * 0.025 + EventPhase);
    float rayPhase = rayU * 137.0 + noise1(u * 31.0 + EventPhase) * 0.70;
    float width = mix(0.045, 0.14, hash1(floor(rayPhase) + EventPhase));
    float rayDistance = abs(fract(rayPhase) - 0.5);
    float footprint = max(fwidth(rayPhase), 0.008);
    float ray = 1.0 - smoothstep(width, width + min(footprint, 0.25), rayDistance);
    // Blend unresolved rays into their mean coverage instead of letting them shimmer.
    ray = mix(ray, width * 2.0, smoothstep(0.20, 0.65, footprint));
    float reach = 0.35 + 0.45 * noise1(u * 24.0 + EventPhase * 2.0);
    float rayFade = 1.0 - smoothstep(reach - 0.15, reach + 0.12, v);
    float rayVariation = 0.62 + 0.38 * noise1(u * 74.0 + Time * 0.018 + EventPhase);
    float rays = 0.48 * ray * rayVariation * exp(-v * 2.1) * rayFade;

    float veil = 0.25 * exp(-v * 3.0);
    float lowerEdge = (v - 0.075) / 0.045;
    float lowerGlow = 0.16 * exp(-lowerEdge * lowerEdge);
    float vertical = smoothstep(0.0, 0.035, v) * (1.0 - smoothstep(0.70, 1.0, v));

    // A broad field drifts along each ribbon: individual patches develop and dissolve slowly.
    float movingField = noise1(u * 5.0 - Time * 0.055 + EventPhase * 3.0);
    float travelingLight = 0.50 + 0.72 * smoothstep(0.30, 0.86, movingField);
    float texture = 0.74 + 0.26 * noise1(u * 9.0 + EventPhase * 0.5);
    float haze = smoothstep(0.06, 0.24, direction.y);
    float alpha = Intensity * vertexColor.a * edge * (veil + rays + lowerGlow) * vertical
            * travelingLight * texture * haze * transmission;
    if (alpha < 0.001) discard;
    vec3 green = vec3(0.13, 0.82, 0.38);
    vec3 violet = vec3(0.37, 0.19, 0.52);
    vec3 color = mix(green, violet, smoothstep(0.28, 0.90, v) * 0.65);
    fragColor = vec4(color, alpha);
}
