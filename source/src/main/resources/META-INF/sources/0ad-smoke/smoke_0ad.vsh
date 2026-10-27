#version 150
// Forge geometry adapter for 0 A.D. particle.vs (GPL-2.0-or-later).
// Original v_color is the particle's albedo, not an already lit colour.
in vec3 Position;
in vec4 Color;
in vec2 UV0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec4 tint;
out vec2 coord;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    tint = Color;
    coord = UV0;
}
