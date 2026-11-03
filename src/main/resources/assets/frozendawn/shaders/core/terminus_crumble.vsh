#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out float vertexDistance;
out vec4 vertexColor;
out vec4 crumble;
out vec4 lightMapColor;
out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexDistance = fog_distance(Position, FogShape);
    // The vertex color carries the crumble, not a tint: R is the height down from the top of the body, G how far
    // the crumble has come (over 1.25), B how grey the body is. Only its alpha is the body's own.
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, vec4(1.0, 1.0, 1.0, Color.a));
    crumble = Color;
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
}
