#version 150
uniform sampler2D Sampler0;
in vec2 texCoord;
out vec4 fragColor;
// Sensor target heat is independent of sun direction; retain silhouette alpha.
void main(){float alpha=texture(Sampler0,texCoord).a;if(alpha<0.1)discard;fragColor=vec4(1.0,1.0,1.0,alpha);}
