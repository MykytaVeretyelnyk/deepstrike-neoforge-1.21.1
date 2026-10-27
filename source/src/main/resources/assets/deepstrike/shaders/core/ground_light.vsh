#version 150
in vec3 Position;
in vec4 Color;
in vec3 Normal;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec3 point;
out vec3 surfaceNormal;
out float shadow;
void main(){
 point=Position;
 surfaceNormal=Normal;
 shadow=Color.a;
 gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0);
}
