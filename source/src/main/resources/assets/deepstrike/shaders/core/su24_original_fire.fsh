#version 150
in vec4 tint;
in vec3 eye;
in vec3 normal;
in vec2 coord;
uniform float DepthOnly;
out vec4 fragColor;
void main(){
 float facing=clamp(abs(dot(normalize(normal),normalize(-eye))),0.0,1.0);
 float radial=sqrt(max(0.0,1.0-facing*facing));
 float heat=pow(max(0.0,1.0-radial*radial),.72)*mix(1.0,.82,coord.x);
 float ends=smoothstep(0.0,.018,coord.x)*(1.0-smoothstep(.92,1.0,coord.x));
 float alpha=tint.a;
 if(alpha<(DepthOnly>.5?.9:.003))discard;
 vec3 color=mix(vec3(1.0,.28,.045),vec3(1.22,1.15,.94),heat);
 fragColor=vec4(color*tint.rgb,alpha);
}
