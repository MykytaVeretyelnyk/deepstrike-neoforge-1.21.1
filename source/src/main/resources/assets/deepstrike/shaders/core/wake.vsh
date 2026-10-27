#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform sampler2D Sampler2;
out vec4 tint;
out vec2 coord;
out float age;
out float seed;
out float exhaustHeat;
out vec3 worldPos;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vec3 ambient = texelFetch(Sampler2, clamp(UV2 / 16, ivec2(0), ivec2(15)), 0).rgb;
    // Unlit aerosol follows ambient light; it does not glow across the sky at night.
    float illumination = dot(ambient, vec3(.2126,.7152,.0722));
    tint = vec4(Color.rgb * mix(.055,1.0,pow(clamp(illumination,0.0,1.0),1.35)), Color.a);
    coord = UV0;
    exhaustHeat = UV1.x < 0 ? clamp(-float(UV1.x) / 32767.0, 0.0, 1.0) : 0.0;
    seed = float(max(0, UV1.x)) * 0.013;
    age = float(UV1.y) * 0.05;
    worldPos = Position;
}
