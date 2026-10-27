#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec3 volumePoint;
out float strength;
out float opticalStep;
out float flashMix;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    volumePoint = vec3(UV0, (Color.r - 0.5) * 4.0);
    strength = Color.g;
    opticalStep = Color.b * 8.0;
    flashMix = Color.a;
}
