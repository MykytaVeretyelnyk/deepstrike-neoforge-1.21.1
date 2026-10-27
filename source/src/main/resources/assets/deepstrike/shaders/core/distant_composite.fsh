#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec2 LocalDepth;
uniform vec2 WorldDepth;
uniform float DistanceScale;
uniform float Visibility;
in vec2 texCoord;
out vec4 fragColor;
void main(){
    vec4 color=texture(Sampler0,texCoord);
    float depth=texture(Sampler1,texCoord).r;
    if(color.a<0.001 || depth>=1.0)discard;
    if(any(isnan(color)) || any(isinf(color)) || isnan(depth) || DistanceScale<=0.0)discard;
    float denominator=depth*2.0-1.0+LocalDepth.x;
    if(abs(denominator)<0.0000001)discard;
    float localDistance=LocalDepth.y/denominator;
    if(localDistance<=0.0 || isnan(localDistance) || isinf(localDistance))discard;
    float worldDistance=localDistance/DistanceScale;
    float worldDepth=(-WorldDepth.x+WorldDepth.y/worldDistance)*0.5+0.5;
    // Beyond the world's far plane only clear sky can accept the mesh.
    gl_FragDepth=min(worldDepth,0.99999994);
    fragColor=color*Visibility;
}
