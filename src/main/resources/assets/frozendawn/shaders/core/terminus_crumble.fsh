#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec4 crumble;
in vec4 lightMapColor;
in vec2 texCoord0;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.1) {
        discard;
    }
    // A texel goes when the crumble passes its height plus up to a quarter of the body, its own amount, so the edge
    // runs head first and is never a straight line.
    vec2 texel = floor(texCoord0 * vec2(textureSize(Sampler0, 0)));
    if (crumble.r + hash(texel) * 0.25 < crumble.g * 1.25) {
        discard;
    }
    float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    color.rgb = mix(color.rgb, (0.42 + 0.5 * luma) * vec3(0.95, 0.99, 1.02), crumble.b);
    color *= vertexColor * ColorModulator;
    color *= lightMapColor;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
