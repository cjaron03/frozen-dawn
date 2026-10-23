#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 SkyView;
uniform mat4 ProjMat;
out vec3 skyDirection;
out vec2 curtainUv;
out vec4 vertexColor;

void main() {
    skyDirection = Position;
    curtainUv = UV0;
    vertexColor = Color;
    gl_Position = ProjMat * SkyView * vec4(Position, 1.0);
    gl_Position.z = gl_Position.w;
}
