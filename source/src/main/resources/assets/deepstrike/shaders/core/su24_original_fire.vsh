#version 150
in vec3 Position;
in vec4 Color;
in vec3 Normal;
in vec2 UV0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec4 tint;
out vec3 eye;
out vec3 normal;
out vec2 coord;
void main(){vec4 p=ModelViewMat*vec4(Position,1);gl_Position=ProjMat*p;eye=p.xyz;normal=normalize(mat3(ModelViewMat)*Normal);tint=Color;coord=UV0;}
