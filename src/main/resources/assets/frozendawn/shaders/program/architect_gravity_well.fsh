#version 150

// Felled Architect death: space pinches and twists toward the falling body,
// then one ripple ring snaps outward on impact. Distances are in screen-height units.

uniform sampler2D DiffuseSampler;

in vec2 texCoord;

uniform vec2 InSize;
uniform float CenterX;
uniform float CenterY;
uniform float Radius;
uniform float Pinch;
uniform float RippleProgress;
uniform float RippleAmount;

out vec4 fragColor;

void main() {
    vec2 Center = vec2(CenterX, CenterY);
    vec2 aspect = vec2(InSize.x / InSize.y, 1.0);
    vec2 d = (texCoord - Center) * aspect;
    float r = length(d);
    vec2 dir = r > 1e-5 ? d / r : vec2(0.0);

    vec2 warped = d;
    float well = Radius > 0.0 ? clamp(1.0 - r / Radius, 0.0, 1.0) : 0.0;
    well *= well;
    if (Pinch > 0.0 && well > 0.0) {
        // Sample from farther out so the scene inside the well shrinks toward its core.
        warped *= 1.0 + Pinch * 0.9 * well;
        float twist = Pinch * 0.55 * well;
        float c = cos(twist);
        float s = sin(twist);
        warped = vec2(c * warped.x - s * warped.y, s * warped.x + c * warped.y);
    }

    float split = 0.0;
    if (RippleAmount > 0.0 && Radius > 0.0) {
        float ringRadius = RippleProgress * Radius * 2.4;
        float width = Radius * 0.22 + 0.01;
        float x = (r - ringRadius) / width;
        float band = exp(-x * x);
        // Push in front of the ring, pull behind it: a single pressure wave.
        float push = -x * band * RippleAmount * Radius * 0.35;
        warped -= dir * push;
        split = band * RippleAmount * 0.004;
    }

    vec2 uv = Center + warped / aspect;
    vec2 off = dir / aspect * split;
    vec3 color;
    color.r = texture(DiffuseSampler, uv + off).r;
    color.g = texture(DiffuseSampler, uv).g;
    color.b = texture(DiffuseSampler, uv - off).b;

    // A faint cold shadow at the core while space is drawn in.
    color *= 1.0 - Pinch * 0.18 * well;

    fragColor = vec4(color, 1.0);
}
