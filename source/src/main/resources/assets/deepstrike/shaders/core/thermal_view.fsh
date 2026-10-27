#version 150
uniform sampler2D Sampler0;
in vec2 texCoord;
out vec4 fragColor;
void main(){
    vec3 c=texture(Sampler0,texCoord).rgb;
    float luminance=dot(c,vec3(0.2126,0.7152,0.0722));
    float low=min(c.r,min(c.g,c.b)),high=max(c.r,max(c.g,c.b));
    // Keep scenery visible but cold; the unlit sensor silhouette remains hot.
    float heat=smoothstep(0.88,0.995,low)*(1.0-smoothstep(0.025,0.10,high-low));
    float intensity=mix(0.04+0.32*luminance,1.0,heat);
    fragColor=vec4(vec3(intensity),1.0);
}
