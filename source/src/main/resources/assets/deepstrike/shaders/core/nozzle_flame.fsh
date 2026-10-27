#version 150
#moj_import <deepstrike:wake_noise.glsl>
uniform float GameTime;
in vec2 coord;
in vec4 tint;
out vec4 fragColor;
vec4 candidateFlame(int style,int model) {
    float t=GameTime*1200.0;
    float burnStage=clamp(tint.b,0.0,1.0);
    if(coord.x<-.5) {
        bool halo=coord.x< -3.5;
        float r=length(vec2(coord.x+(halo?5.0:2.0),coord.y));
        float body=exp(-r*r*(halo?4.2:6.4));
        float edge=exp(-r*r*(halo?1.9:2.5));
        float a=mix(edge,body,.45)*(1.0-smoothstep(.64,.96,r))*tint.a;
        // Hybrid mix: our hot core + AMCW dual-Gaussian incandescent core & fiery orange rim
        vec3 ourHot=mix(vec3(1.0,.34,.07),vec3(1.0,.98,.87),exp(-r*r*11.0));
        vec3 amcwJet=vec3(1.0,.98,.91)*body*1.85
                    +vec3(1.0,.47,.12)*max(edge-body*.65,0.0)*1.35
                    +vec3(1.0,.59,.23)*edge*.38;
        vec3 hot=mix(ourHot,amcwJet,.58);
        if(halo) {
            hot=mix(vec3(1.6,.50,.10),vec3(5.2,4.5,3.3),exp(-r*r*10.0));
            a*=1.25;
        }
        return vec4(hot,a);
    }
    float u=clamp(coord.x,0.0,1.0);
    float radial=clamp(abs(coord.y),0.0,1.0);
    float flow=0.5;
    // The mesh already defines the jet silhouette. Its radial coordinate is
    // shared by temperature and opacity; no second, narrow Gaussian hull.
    float heat=pow(max(0.0,1.0-radial*radial),.72);
    heat*=mix(1.0,.88,burnStage)*mix(1.0,.82,u);
    heat=clamp(heat+(flow-.5)*.045,0.0,1.0);
    vec3 rim=vec3(1.0,.28,.045);
    vec3 hot=vec3(1.22,1.15,.94);
    vec3 color=mix(rim,hot,heat);
    float edgeFade=1.0-smoothstep(.92,1.0,radial);
    float ends=smoothstep(0.0,.016,u)*(1.0-smoothstep(.92,1.0,u));
    float alpha=tint.a;
    return vec4(color*clamp(tint.g,.0,1.0),alpha);
}
void main() {
    if(tint.r<.5) {
        fragColor=candidateFlame(int(round(tint.r*16.0)),int(round(tint.g*3.0)));
        if(fragColor.a<.003)discard;
        return;
    }
    float alpha;
    vec3 color;
    if (coord.x < -0.5) {
        float r = length(vec2(coord.x+2.0,coord.y));
        float body = exp(-r*r*6.0);
        float edge = exp(-r*r*2.4);
        alpha = mix(edge,body,.45)*(1.0-smoothstep(.64,.96,r));
        vec3 ourCol = mix(vec3(1.0,.32,.065),vec3(1.0,.98,.82),exp(-r*r*11.0));
        vec3 amcwCol = vec3(1.0,.98,.91)*body*1.75 + vec3(1.0,.47,.12)*max(edge-body*.65,0.0)*1.3 + vec3(1.0,.59,.23)*edge*.35;
        color = mix(ourCol, amcwCol, 0.58);
    } else {
        float u=coord.x, v=coord.y;
        float t=GameTime*1200.0;
        float noise = wakeFbm(vec3(u*8.5-t*11.0,v*4.8,t*.7));
        float warp = (wakeNoise(vec3(u*11.0-t*8.0,t*.55,1.3))-.5)*.14*u;
        float ourWidth = .58 * pow(max(0.0,1.0-u),.72);
        float amcwTail = 1.0 - smoothstep(.62, 1.0, u + (noise-.5)*.15);
        float amcwWidth = (.28 + .44 * u) * amcwTail;
        float width = mix(ourWidth, amcwWidth, 0.56);
        float edge = max(.022,width+(noise-.5)*.08*smoothstep(.02,.3,u));
        float r = abs(v-warp);
        float body = exp(-r*r/max(.0006,edge*edge*.55)) * amcwTail;
        float core = exp(-r*r/max(.0005,width*width*.14))*(1.0-smoothstep(.28,.82,u));
        alpha = (1.0-exp(-4.0*body))*smoothstep(0.0,.020,u)*(1.0-smoothstep(.75,.98,abs(v)));
        color = mix(vec3(1.0,.28,.05),vec3(1.0,.58,.16),clamp(body*.65+noise*.35,0.0,1.0));
        color = mix(color,vec3(1.0,.98,.89),clamp(core*1.3,0.0,1.0));
    }
    alpha *= tint.a;
    if (alpha < .005) discard;
    fragColor = vec4(color*tint.rgb,alpha);
}
