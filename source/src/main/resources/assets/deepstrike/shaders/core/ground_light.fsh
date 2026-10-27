#version 150
in vec3 point;
in vec3 surfaceNormal;
in float shadow;
uniform vec3 Source;
uniform vec3 EmitterEnd;
uniform float LightRadius;
uniform float Strength;
uniform vec3 LightColor;
out vec4 fragColor;
void main(){
 vec3 segment=EmitterEnd-Source;
 float t=clamp(dot(point-Source,segment)/max(.0001,dot(segment,segment)),0.0,1.0);
 vec3 delta=Source+t*segment-point;
 float distance=length(delta);
 float u=distance/max(.01,LightRadius);
 if(u>=1.0||shadow<=.001)discard;
 float falloff=exp(-3.0*u*u)*(1.0-smoothstep(.55,1.0,u));
 float diffuse=.20+.80*max(0.0,dot(normalize(surfaceNormal),delta/max(.03,distance)));
 float alpha=clamp(Strength*falloff*diffuse*shadow,0.0,1.0);
 fragColor=vec4(LightColor,alpha);
}
