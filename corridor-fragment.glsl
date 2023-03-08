#version 430
layout (binding=0) uniform sampler2D sampler0;

in vec2 fragmentST;
out vec4 color; // Output final color

void main(void)
{
    // color = vColor; // Same color input as output
    color = texture(sampler0, fragmentST); // Texture output
}