#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    // Никакого lightmap, никакого fog — силуэт всегда виден ярко-красным даже под
    // blindness и в полной темноте. Цвет полностью определяется vertexColor
    // (текстура — белая 1×1, поэтому работает как color×1×1).
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.01) discard;
    fragColor = color;
}
