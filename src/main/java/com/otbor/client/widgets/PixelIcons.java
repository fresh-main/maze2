package com.otbor.client.widgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public final class PixelIcons {

    private PixelIcons() {}

    private static final String[] DOOR_0 = {
            "................",
            "...##########...",
            "...#pppppppp#...",
            "...#p++++++p#...",
            "...#p+pppp+p#...",
            "...#p+pppp+p#...",
            "...#p+pppp+p#...",
            "...#p++++++p#...",
            "...#pppppprp#...",
            "...#p++++++p#...",
            "...#p+pppp+p#...",
            "...#p+pppp+p#...",
            "...#p++++++p#...",
            "...#pppppppp#...",
            "..############..",
            "................"
    };

    private static final String[] DOOR_1 = {
            "................",
            "...##########...",
            "...#ppppp+YY#...",
            "...#p+++++YY#...",
            "...#p+pp++YY#...",
            "...#p+pp++YY#...",
            "...#p+pp++yy#...",
            "...#p+++++yy#...",
            "...#ppprp+yy#...",
            "...#p+++++yy#...",
            "...#p+pp++yy#...",
            "...#p+pp++yy#...",
            "...#p+++++yy#...",
            "...#ppppp+yy#...",
            "..############..",
            "................"
    };

    private static final String[] DOOR_2 = {
            "................",
            "...##########...",
            "...#ppp+YYYY#...",
            "...#ppp+YYYY#...",
            "...#pdp+YYYY#...",
            "...#pdp+YYYY#...",
            "...#pdp+yyyy#...",
            "...#pdp+yyyy#...",
            "...#prp+yyyy#...",
            "...#pdp+yyyy#...",
            "...#pdp+yyyy#...",
            "...#pdp+yyyy#...",
            "...#pdp+yyyy#...",
            "...#ppp+yyyy#...",
            "..############..",
            "................"
    };

    private static final String[] DOOR_3 = {
            "................",
            "...##########...",
            "...#p+YYYYYY#...",
            "...#p+YYYYYY#...",
            "...#p+YYYYYY#...",
            "...#p+YYYYYY#...",
            "...#p+yyyyyy#...",
            "...#p+yyyyyy#...",
            "...#p+yyyyyy#...",
            "...#p+yyyyyy#...",
            "...#p+yyyyyy#...",
            "...#p+yyyyyy#...",
            "...#p+yyyyyy#...",
            "...#p+yyyyyy#...",
            "..############..",
            "................"
    };

    private static final String[] COMPASS_DIAL = {
            ".....######.....",
            "...##llllll##...",
            "..#llll++llll#..",
            ".#llllllllllll#.",
            ".#llllllllllll#.",
            "#llllllllllllll#",
            "#llllllllllllll#",
            "#l+llllllllll+l#",
            "#l+llllllllll+l#",
            "#llllllllllllll#",
            "#llllllllllllll#",
            ".#llllllllllll#.",
            ".#llllllllllll#.",
            "..#llll++llll#..",
            "...##llllll##...",
            ".....######....."
    };

    private static final String[] NEEDLE_0 = {
            "................",
            "................",
            "................",
            "....rr..........",
            ".....rr.........",
            ".....rr.........",
            "......rr........",
            ".......p#.......",
            ".......###......",
            ".........##.....",
            ".........##.....",
            "..........##....",
            "................",
            "................",
            "................",
            "................"
    };

    private static final String[] NEEDLE_1 = {
            "................",
            "................",
            ".....rr.........",
            ".....rr.........",
            "......rr........",
            "......rr........",
            ".......rr.......",
            ".......p#.......",
            ".......##.......",
            "........##......",
            "........##......",
            ".........##.....",
            ".........##.....",
            "................",
            "................",
            "................"
    };

    private static final String[] NEEDLE_2 = {
            "................",
            "................",
            ".......rr.......",
            ".......rr.......",
            ".......rr.......",
            ".......rr.......",
            ".......rr.......",
            ".......p#.......",
            ".......##.......",
            ".......##.......",
            ".......##.......",
            ".......##.......",
            ".......##.......",
            "................",
            "................",
            "................"
    };

    private static final String[] NEEDLE_3 = {
            "................",
            "................",
            ".........rr.....",
            ".........rr.....",
            "........rr......",
            "........rr......",
            ".......rr.......",
            ".......p#.......",
            ".......##.......",
            "......##........",
            "......##........",
            ".....##.........",
            ".....##.........",
            "................",
            "................",
            "................"
    };

    private static final String[] NEEDLE_4 = {
            "................",
            "................",
            "................",
            "..........rr....",
            ".........rr.....",
            ".........rr.....",
            "........rr......",
            ".......p#.......",
            "......###.......",
            ".....##.........",
            ".....##.........",
            "....##..........",
            "................",
            "................",
            "................",
            "................"
    };

    private static final String[] EYE_BASE = {
            "................",
            "................",
            "................",
            ".....######.....",
            "...##wwwwww##...",
            "..#wwwwwwwwww#..",
            ".#wwwwwwwwwwww#.",
            "#wwwwwwwwwwwwww#",
            "#wwwwwwwwwwwwww#",
            ".#wwwwwwwwwwww#.",
            "..#wwwwwwwwww#..",
            "...##wwwwww##...",
            ".....######.....",
            "................",
            "................",
            "................"
    };

    private static final String[] EYE_BLINK = {
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "################",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................"
    };

    private static final String[] EYE_IRIS = {
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            ".......Rr.......",
            "......r##r......",
            "......r##r......",
            ".......rr.......",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................"
    };

    private static final String[] GEAR_A = {
            ".......##.......",
            "......#dd#......",
            "...##.#dd#.##...",
            "..#dd######dd#..",
            "..#dd#dddd#dd#..",
            "...##dddddd##...",
            ".###dddrrddd###.",
            "#dd#ddrrrrdd#dd#",
            "#dd#ddrrrrdd#dd#",
            ".###dddrrddd###.",
            "...##dddddd##...",
            "..#dd#dddd#dd#..",
            "..#dd######dd#..",
            "...##.#dd#.##...",
            "......#dd#......",
            ".......##......."
    };

    private static final String[] GEAR_B = {
            "................",
            ".....##..##.....",
            "....#dd##dd#....",
            "....#dd##dd#....",
            "..##.#dddd#.##..",
            ".#dd#dddddd#dd#.",
            ".#dddddrrddddd#.",
            "..##ddrrrrdd##..",
            "..##ddrrrrdd##..",
            ".#dddddrrddddd#.",
            ".#dd#dddddd#dd#.",
            "..##.#dddd#.##..",
            "....#dd##dd#....",
            "....#dd##dd#....",
            ".....##..##.....",
            "................"
    };

    private static final String[] POWER = {
            "................",
            ".......##.......",
            ".......##.......",
            "....##.##.##....",
            "...###.##.###...",
            "..####.##.####..",
            "..###..##..###..",
            "..##...##...##..",
            ".###...##...###.",
            ".###........###.",
            "..##........##..",
            "..###......###..",
            "..####....####..",
            "...##########...",
            "....########....",
            ".......##......."
    };

    private static final String[] GLOBE = {
            "................",
            "....########....",
            "...####..####...",
            "..##.#....#.##..",
            ".##.#......#.##.",
            ".#..#......#..#.",
            ".#..#......#..#.",
            ".##############.",
            ".#..#......#..#.",
            ".#..#......#..#.",
            ".#..#......#..#.",
            ".##.#......#.##.",
            "..##.#....#.##..",
            "...####..####...",
            "....########....",
            "................"
    };

    private static final String[][] DOORS = { DOOR_0, DOOR_1, DOOR_2, DOOR_3 };
    private static final String[][] NEEDLES = { NEEDLE_0, NEEDLE_1, NEEDLE_2, NEEDLE_3, NEEDLE_4 };

    public static int scaleFor(int size) {
        return Math.max(1, Math.round(size / 16f));
    }

    public static void drawCardIcon(GuiGraphics gfx, String key, int cx, int cy, int size,
                                    float time, float hover, float spin, float lookX, float lookY) {
        int scale = scaleFor(size);
        int px = cx - 8 * scale;
        int py = cy - 8 * scale - Math.round(hover * 2f);
        switch (key) {
            case "door" -> draw(gfx, DOORS[Math.min(3, (int) (hover * 4f))], px, py, scale, 0);
            case "compass" -> {
                draw(gfx, COMPASS_DIAL, px, py, scale, 0);
                float s = Mth.sin(time * (1.2f + 4f * hover));
                int f = Mth.clamp(Math.round(2f + s * (1.2f + 0.8f * hover)), 0, 4);
                draw(gfx, NEEDLES[f], px, py, scale, 0);
            }
            case "eye" -> {
                if ((time % 5.2f) < 0.11f) {
                    draw(gfx, EYE_BLINK, px, py, scale, 0);
                } else {
                    draw(gfx, EYE_BASE, px, py, scale, 0);
                    int ox = Math.round(lookX * 2f);
                    int oy = Math.round(lookY);
                    draw(gfx, EYE_IRIS, px + ox * scale, py + oy * scale, scale, 0);
                }
            }
            case "gear" -> {
                int frame = Math.floorMod((int) (spin / 22.5f), 2);
                draw(gfx, frame == 0 ? GEAR_A : GEAR_B, px, py, scale, 0);
            }
            default -> {
            }
        }
    }

    public static void drawButtonIcon(GuiGraphics gfx, String key, int cx, int cy, int size, float hover) {
        int scale = scaleFor(size);
        int color = PaperRender.mix(PaperRender.INK, PaperRender.INK_RED, hover);
        draw(gfx, "power".equals(key) ? POWER : GLOBE,
                cx - 8 * scale, cy - 8 * scale - Math.round(hover * 2f), scale, color);
    }

    private static void draw(GuiGraphics gfx, String[] bmp, int x, int y, int scale, int ink) {
        for (int row = 0; row < bmp.length; row++) {
            String line = bmp[row];
            int col = 0;
            while (col < line.length()) {
                char ch = line.charAt(col);
                if (ch == '.') {
                    col++;
                    continue;
                }
                int end = col + 1;
                while (end < line.length() && line.charAt(end) == ch) end++;
                int c = (ch == '#' && ink != 0) ? ink : color(ch);
                gfx.fill(x + col * scale, y + row * scale, x + end * scale, y + (row + 1) * scale, c);
                col = end;
            }
        }
    }

    private static int color(char c) {
        return switch (c) {
            case '#' -> PaperRender.INK;
            case '+' -> PaperRender.INK_FADED;
            case 's' -> PaperRender.INK_SOFT;
            case 'r' -> PaperRender.INK_RED;
            case 'R' -> PaperRender.INK_RED_DIM;
            case 'l' -> PaperRender.PAPER_LIGHT;
            case 'p' -> PaperRender.PAPER_BASE;
            case 'd' -> PaperRender.PAPER_DARK;
            case 'y' -> 0xFFE8C880;
            case 'Y' -> 0xFFFAEAB0;
            case 'w' -> 0xFFF6ECCB;
            default -> 0;
        };
    }
}