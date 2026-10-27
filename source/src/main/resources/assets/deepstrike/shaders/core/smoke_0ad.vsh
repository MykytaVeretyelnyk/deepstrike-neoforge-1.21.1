#version 150
// Forge geometry adapter for 0 A.D. particle.vs (GPL-2.0-or-later).
// Original v_color is the particle's albedo, not an already lit colour.
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec4 tint;
out vec2 coord;
out float exhaustHeat;
out float compactGlow;
out float blastWave;
out vec3 worldPos;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    exhaustHeat=UV1.x<0?clamp(-float(UV1.x)/32767.0,0.0,1.0):0.0;
    blastWave=UV1.y < -22000?2.0:UV1.y < -16000?1.0:0.0;
    compactGlow=UV1.y<0 && UV1.y>=-16000?1.0:0.0;
    tint = Color;
    coord = UV0;
    worldPos = Position;
}
