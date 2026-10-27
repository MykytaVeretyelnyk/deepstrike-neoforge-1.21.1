#version 150
uniform sampler2D Sampler0;
in vec2 texCoord;
out vec4 fragColor;
void main(){
    vec3 c = texture(Sampler0, texCoord).rgb;
    float lum = dot(c, vec3(0.299, 0.587, 0.114));
    lum = clamp(lum * 1.65 + 0.08, 0.0, 1.0);
    vec2 uv = texCoord * 2.0 - 1.0;
    float vig = clamp(1.0 - dot(uv, uv) * 0.25, 0.0, 1.0);
    vec3 nvg = vec3(lum * 0.15, lum * 1.20, lum * 0.28) * vig;
    fragColor = vec4(nvg, 1.0);
}
