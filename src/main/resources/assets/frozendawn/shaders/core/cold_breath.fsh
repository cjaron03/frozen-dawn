#version 150
#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec4 wisp = vertexColor * ColorModulator * texture(Sampler0, texCoord0);
    // Below one alpha step is invisible; retain the rest of the gradual dissolution.
    if (wisp.a < (1.0 / 255.0)) discard;
    fragColor = linear_fog(wisp, vertexDistance, FogStart, FogEnd, FogColor);
}
