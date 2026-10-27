#version 150
uniform sampler2D Sampler0;
in vec2 texCoord;
out vec4 fragColor;
// A camera captures an opaque world image, regardless of framebuffer alpha.
void main(){fragColor=vec4(texture(Sampler0,texCoord).rgb,1.0);}
