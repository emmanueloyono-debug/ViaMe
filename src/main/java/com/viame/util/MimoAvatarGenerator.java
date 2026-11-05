package com.viame.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * 100% Offline Pure Java Native Port of Mimo Avatar System (Kevin Kenfack).
 *
 * <p>Generates deterministic, customizable, animated SVG avatars directly in memory
 * without any external HTTP calls, Node/npm dependencies, or third-party libraries.
 * 145,152 unique combinations derived from string hashing.</p>
 *
 * <p>Air-gapped and defense-ready for Project 5 (ViaMe Transport Booking System).</p>
 */
public final class MimoAvatarGenerator {

    private MimoAvatarGenerator() {}

    // ── Shapes ────────────────────────────────────────────────────────────────
    public enum Shape {
        CIRCLE, SQUIRCLE, SQUARE
    }

    public enum Mood {
        HAPPY, SURPRISED, SLEEPY, COOL, CHEEKY
    }

    // ── Apple-style squircle path for 100x100 viewBox ────────────────────────
    private static final String SQUIRCLE_PATH =
            "M 0 44 C 0 5.86 5.86 0 44 0 L 56 0 C 94.14 0 100 5.86 100 44 L 100 56 C 100 94.14 94.14 100 56 100 L 44 100 C 5.86 100 0 94.14 0 56 Z";

    // ── 16 Default Gradient Palettes ──────────────────────────────────────────
    private static final String[][] GRADIENTS = {
            {"#E8D5F5", "#C7A4E0"},
            {"#FFE0D0", "#FFB899"},
            {"#D5F5E3", "#A8E6CF"},
            {"#D6EEFF", "#9DD1F5"},
            {"#FFD4D4", "#FF9E9E"},
            {"#FFF5CC", "#FFE580"},
            {"#E0EFD9", "#B8D4A8"},
            {"#FFE4EE", "#FFB6D0"},
            {"#F5E6D0", "#E8C9A0"},
            {"#D0F0F5", "#A0DDE8"},
            {"#F0D5E8", "#E0A4CC"},
            {"#D5E8F5", "#A4C7E0"},
            {"#F5F0D5", "#E8DDA0"},
            {"#E0D5F0", "#BCA4E0"},
            {"#F5D5D0", "#E8A4A0"},
            {"#D0F5E0", "#A0E8BF"}
    };

    // ── Feature Enums ─────────────────────────────────────────────────────────
    private enum EyeType { ROUND, CROSS, LINE, CURVED, WINK, HEART }
    private enum EyebrowType { ARCHED, FLAT, RAISED, NONE }
    private enum MouthType { SMILE, GRIN, O, CAT, TONGUE, SMIRK, FLAT }
    private enum CheekType { BLUSH, NONE }
    private enum AccessoryType { NONE, GLASSES, SUNGLASSES }

    private static final int[][] SPHERE_POSITIONS = {
            {-1, 1}, {1, 1}, {1, 0}, {0, 1}, {-1, 0}, {0, 0}, {0, -1}, {-1, -1}, {1, -1}
    };

    // ── CSS Keyframe Animations ───────────────────────────────────────────────
    private static final String KEYFRAMES =
            "@keyframes mimo-blink {\n" +
            "  0%, 92%, 100% { transform: scaleY(1); }\n" +
            "  96% { transform: scaleY(0.05); }\n" +
            "}\n" +
            "@keyframes mimo-eyebrow-bounce {\n" +
            "  0%, 88%, 100% { transform: translateY(0); }\n" +
            "  94% { transform: translateY(-2px); }\n" +
            "}\n" +
            "@keyframes mimo-float {\n" +
            "  0%, 100% { transform: translateY(0); }\n" +
            "  50% { transform: translateY(-3px); }\n" +
            "}\n" +
            "@keyframes mimo-sway {\n" +
            "  0%, 100% { transform: translateX(0); }\n" +
            "  25% { transform: translateX(-2px); }\n" +
            "  75% { transform: translateX(2px); }\n" +
            "}\n";

    /**
     * Compute a deterministic 32-bit positive hash from the seed string.
     */
    public static int hash(String input) {
        if (input == null || input.isEmpty()) {
            return 42;
        }
        int h = 0;
        for (int i = 0; i < input.length(); i++) {
            h = (h << 5) - h + input.charAt(i);
        }
        return h & 0x7FFFFFFF;
    }

    /**
     * Generate an animated SVG avatar using default circular shape and happy mood.
     */
    public static String generateSvg(String seed) {
        return generateSvg(seed, 128, Shape.CIRCLE, null, true);
    }

    /**
     * Generate an animated SVG avatar with customizable size, shape, mood, and animation flag.
     *
     * @param seed input string (e.g. user email, driver name, ride id)
     * @param size pixel dimensions for SVG attribute (e.g. 64, 80, 128)
     * @param shape clipping shape (CIRCLE, SQUIRCLE, SQUARE)
     * @param mood optional mood override (HAPPY, SURPRISED, SLEEPY, COOL, CHEEKY) or null for random deterministic
     * @param animated whether to embed CSS keyframe animations
     * @return clean, valid SVG XML string
     */
    public static String generateSvg(String seed, int size, Shape shape, Mood mood, boolean animated) {
        if (seed == null || seed.isBlank()) {
            seed = "viame";
        }
        if (size <= 0) size = 128;
        if (shape == null) shape = Shape.CIRCLE;

        long lh = hash(seed.trim()) & 0x7FFFFFFFL;
        String[] gradient = GRADIENTS[(int) (lh % GRADIENTS.length)];

        // Slot selections (64-bit math prevents overflow so indices remain strictly non-negative)
        EyeType eye = EyeType.values()[(int) (lh % EyeType.values().length)];
        EyebrowType eyebrow = EyebrowType.values()[(int) ((lh * 7) % EyebrowType.values().length)];
        MouthType mouth = MouthType.values()[(int) ((lh * 13) % MouthType.values().length)];
        CheekType cheek = CheekType.values()[(int) ((lh * 19) % CheekType.values().length)];
        AccessoryType accessory = AccessoryType.values()[(int) ((lh * 31) % AccessoryType.values().length)];

        // Apply Mood override if requested
        if (mood != null) {
            switch (mood) {
                case HAPPY -> { eye = EyeType.CURVED; mouth = MouthType.GRIN; eyebrow = EyebrowType.ARCHED; }
                case SURPRISED -> { eye = EyeType.ROUND; mouth = MouthType.O; eyebrow = EyebrowType.RAISED; }
                case SLEEPY -> { eye = EyeType.LINE; mouth = MouthType.FLAT; eyebrow = EyebrowType.FLAT; }
                case COOL -> { eye = EyeType.ROUND; accessory = AccessoryType.SUNGLASSES; mouth = MouthType.SMIRK; }
                case CHEEKY -> { eye = EyeType.WINK; mouth = MouthType.TONGUE; cheek = CheekType.BLUSH; }
            }
        }

        boolean hasGlasses = (accessory == AccessoryType.GLASSES || accessory == AccessoryType.SUNGLASSES);
        boolean showEyes = !hasGlasses;

        String clipId = "mimo-c-" + Long.toHexString(lh);
        String gradId = "mimo-g-" + Long.toHexString(lh);

        StringBuilder sb = new StringBuilder(4096);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\" width=\"")
          .append(size).append("\" height=\"").append(size).append("\">\n");

        if (animated) {
            sb.append("<style>\n").append(KEYFRAMES).append("</style>\n");
        }

        // Defs (ClipPath + Radial Gradient)
        sb.append("<defs>\n");
        sb.append("  <clipPath id=\"").append(clipId).append("\">\n");
        appendShapePath(sb, shape);
        sb.append("  </clipPath>\n");
        sb.append("  <radialGradient id=\"").append(gradId).append("\" cx=\"40%\" cy=\"40%\" r=\"85%\">\n");
        sb.append("    <stop offset=\"0%\" stop-color=\"").append(gradient[0]).append("\"/>\n");
        sb.append("    <stop offset=\"100%\" stop-color=\"").append(gradient[1]).append("\"/>\n");
        sb.append("  </radialGradient>\n");
        sb.append("  <radialGradient id=\"").append(gradId).append("-s\" cx=\"50%\" cy=\"50%\" r=\"100%\">\n");
        sb.append("    <stop offset=\"0%\" stop-color=\"rgba(255,255,255,0.18)\"/>\n");
        sb.append("    <stop offset=\"60%\" stop-color=\"rgba(255,255,255,0)\"/>\n");
        sb.append("  </radialGradient>\n");
        sb.append("</defs>\n");

        // Group clipped to shape
        sb.append("<g clip-path=\"url(#").append(clipId).append(")\">\n");
        // Background fill
        appendShapeFill(sb, shape, "url(#" + gradId + ")");
        // Soft shine overlay
        appendShapeFill(sb, shape, "url(#" + gradId + "-s)");

        // ForeignObject HTML layout for smooth CSS keyframe animations
        sb.append("  <foreignObject x=\"0\" y=\"0\" width=\"100\" height=\"100\">\n");
        sb.append("    <div xmlns=\"http://www.w3.org/1999/xhtml\" style=\"width:100%;height:100%;position:relative;overflow:hidden;color:#000;\">\n");

        // Sway wrapper
        String swayAnim = animated ? "animation:mimo-sway 5s ease-in-out infinite;" : "";
        sb.append("      <div style=\"position:absolute;inset:0;").append(swayAnim).append("\">\n");

        // Face div with float animation
        String floatAnim = animated ? "animation:mimo-float 3s ease-in-out infinite;" : "";
        sb.append("        <div style=\"position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;z-index:2;").append(floatAnim).append("\">\n");

        // 1. Eyebrows
        if (eyebrow != EyebrowType.NONE) {
            String mb = hasGlasses ? "6%" : "2%";
            String bounceAnim = animated ? "animation:mimo-eyebrow-bounce 4s ease-in-out infinite;" : "";
            sb.append("          <div style=\"width:55%;margin-bottom:").append(mb).append(";").append(bounceAnim).append("\">\n");
            appendEyebrowSvg(sb, eyebrow);
            sb.append("          </div>\n");
        }

        // 2. Eyes & Accessories
        sb.append("          <div style=\"width:60%;position:relative;display:flex;align-items:center;justify-content:center;\">\n");
        if (showEyes) {
            String blinkAnim = animated ? "style=\"animation:mimo-blink 3s ease-in-out infinite;transform-origin:center center;\"" : "";
            sb.append("            <div style=\"display:contents;\">\n");
            appendEyeSvg(sb, eye, blinkAnim);
            sb.append("            </div>\n");
        }
        if (accessory != AccessoryType.NONE) {
            sb.append("            <div style=\"position:absolute;inset:0;display:flex;align-items:center;justify-content:center;pointer-events:none;\">\n");
            appendAccessorySvg(sb, accessory);
            sb.append("            </div>\n");
        }
        sb.append("          </div>\n");

        // 3. Mouth
        String mt = hasGlasses ? "8%" : "4%";
        sb.append("          <div style=\"margin-top:").append(mt).append(";width:35%;display:flex;align-items:center;justify-content:center;\">\n");
        appendMouthSvg(sb, mouth);
        sb.append("          </div>\n");

        // 4. Cheeks
        if (cheek == CheekType.BLUSH) {
            sb.append("          <div style=\"position:absolute;bottom:18%;left:5%;right:5%;pointer-events:none;\">\n");
            sb.append("            <svg xmlns=\"http://www.w3.org/2000/svg\" fill=\"none\" viewBox=\"0 0 70 12\" style=\"width:100%;height:auto;\">\n");
            sb.append("              <circle cx=\"16\" cy=\"6\" r=\"6\" fill=\"#FF9EBB\" opacity=\"0.3\"/>\n");
            sb.append("              <circle cx=\"54\" cy=\"6\" r=\"6\" fill=\"#FF9EBB\" opacity=\"0.3\"/>\n");
            sb.append("            </svg>\n");
            sb.append("          </div>\n");
        }

        sb.append("        </div>\n");
        sb.append("      </div>\n");
        sb.append("    </div>\n");
        sb.append("  </foreignObject>\n");
        sb.append("</g>\n");
        sb.append("</svg>");

        return sb.toString();
    }

    /**
     * Converts the generated SVG into a Data URI ({@code data:image/svg+xml;base64,...})
     * suitable for immediate display in WebViews, HTML pages, or direct CSS background images.
     */
    public static String generateDataUri(String seed, int size, Mood mood) {
        String svg = generateSvg(seed, size, Shape.CIRCLE, mood, true);
        String base64 = Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return "data:image/svg+xml;base64," + base64;
    }

    // ── Internal SVG Drawing Routines ─────────────────────────────────────────

    private static void appendShapePath(StringBuilder sb, Shape shape) {
        switch (shape) {
            case CIRCLE -> sb.append("    <circle cx=\"50\" cy=\"50\" r=\"50\"/>\n");
            case SQUIRCLE -> sb.append("    <path d=\"").append(SQUIRCLE_PATH).append("\"/>\n");
            case SQUARE -> sb.append("    <rect x=\"0\" y=\"0\" width=\"100\" height=\"100\"/>\n");
        }
    }

    private static void appendShapeFill(StringBuilder sb, Shape shape, String fill) {
        switch (shape) {
            case CIRCLE -> sb.append("  <circle cx=\"50\" cy=\"50\" r=\"50\" fill=\"").append(fill).append("\"/>\n");
            case SQUIRCLE -> sb.append("  <path d=\"").append(SQUIRCLE_PATH).append("\" fill=\"").append(fill).append("\"/>\n");
            case SQUARE -> sb.append("  <rect x=\"0\" y=\"0\" width=\"100\" height=\"100\" fill=\"").append(fill).append("\"/>\n");
        }
    }

    private static void appendEyeSvg(StringBuilder sb, EyeType eye, String groupStyle) {
        switch (eye) {
            case ROUND -> {
                sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 63 15\" style=\"width:100%;height:auto;max-width:90%;max-height:40%;\">\n");
                sb.append("  <g ").append(groupStyle).append(">\n");
                sb.append("    <circle cx=\"7.2\" cy=\"7.2\" r=\"7.2\" fill=\"currentColor\"/>\n");
                sb.append("    <circle cx=\"55.2\" cy=\"7.2\" r=\"7.2\" fill=\"currentColor\"/>\n");
                sb.append("  </g>\n</svg>\n");
            }
            case CROSS -> {
                sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 71 23\" style=\"width:100%;height:auto;max-width:90%;max-height:40%;\">\n");
                sb.append("  <g ").append(groupStyle).append(">\n");
                sb.append("    <path d=\"M11.5 0C12.94 0 14.17 0.35 14.68 0.86C15.04 1.38 15.04 2.1 15.04 3.54V7.96H19.46C20.9 7.96 22.14 8.32 22.65 8.83C23 9.34 23 10.06 23 11.5C23 12.94 22.65 14.17 22.14 14.68C21.62 15.04 20.9 15.04 19.46 15.04H15.04V19.46C15.04 20.9 14.68 22.14 14.17 22.65C13.66 23 12.94 23 11.5 23C10.06 23 8.83 22.65 8.32 22.14C7.96 21.62 7.96 20.9 7.96 19.46V15.04H3.54C2.1 15.04 0.86 14.68 0.35 14.17C0 13.66 0 12.94 0 11.5C0 10.06 0.35 8.83 0.86 8.32C1.38 7.96 2.1 7.96 3.54 7.96H7.96V3.54C7.96 2.1 8.32 0.86 8.83 0.35C9.34 0 10.06 0 11.5 0Z\" fill=\"currentColor\"/>\n");
                sb.append("    <path d=\"M58.77 0C60.21 0 61.44 0.35 61.95 0.86C62.31 1.38 62.31 2.1 62.31 3.54V7.96H66.73C68.17 7.96 69.41 8.32 69.92 8.83C70.27 9.34 70.27 10.06 70.27 11.5C70.27 12.94 69.92 14.17 69.41 14.68C68.89 15.04 68.17 15.04 66.73 15.04H62.31V19.46C62.31 20.9 61.95 22.14 61.44 22.65C60.93 23 60.21 23 58.77 23C57.33 23 56.1 22.65 55.59 22.14C55.23 21.62 55.23 20.9 55.23 19.46V15.04H50.81C49.37 15.04 48.13 14.68 47.62 14.17C47.27 13.66 47.27 12.94 47.27 11.5C47.27 10.06 47.27 9.34 47.62 8.83C48.13 7.96 49.37 7.96 50.81 7.96H55.23V3.54C55.23 2.1 55.59 0.86 56.1 0.35C56.61 0 57.33 0 58.77 0Z\" fill=\"currentColor\"/>\n");
                sb.append("  </g>\n</svg>\n");
            }
            case LINE -> {
                sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 70 10\" style=\"width:100%;height:auto;max-width:90%;max-height:40%;\">\n");
                sb.append("  <g ").append(groupStyle).append(">\n");
                sb.append("    <circle cx=\"4\" cy=\"5\" r=\"3.5\" fill=\"currentColor\"/>\n");
                sb.append("    <rect x=\"12\" y=\"1.5\" width=\"16\" height=\"7\" rx=\"3.5\" fill=\"currentColor\"/>\n");
                sb.append("    <rect x=\"42\" y=\"1.5\" width=\"16\" height=\"7\" rx=\"3.5\" fill=\"currentColor\"/>\n");
                sb.append("    <circle cx=\"66\" cy=\"5\" r=\"3.5\" fill=\"currentColor\"/>\n");
                sb.append("  </g>\n</svg>\n");
            }
            case CURVED -> {
                sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 63 9\" style=\"width:100%;height:auto;max-width:90%;max-height:40%;\">\n");
                sb.append("  <g ").append(groupStyle).append(">\n");
                sb.append("    <path d=\"M0.84 3.35C2.41 2.21 6.04 0 10.5 0C14.96 0 18.59 2.21 20.16 3.35C20.66 3.76 20.95 4.34 20.99 4.8C21 6.02 20.97 6.68 20.97 6.68C20.72 7.96 19.8 8.56 18.53 8.25C16.76 7.49 13.98 6 10.5 6C7.02 6 4.24 7.49 2.47 8.25C1.2 8.56 0.28 7.96 0.03 6.68C0 6.49 0 6.02 0 5.07Z\" fill=\"currentColor\"/>\n");
                sb.append("    <path d=\"M42.84 3.35C44.41 2.21 48.04 0 52.5 0C56.96 0 60.59 2.21 62.16 3.35C62.66 3.76 62.95 4.34 62.99 4.8C63 6.02 62.97 6.68 62.97 6.68C62.72 7.96 61.8 8.56 60.53 8.25C58.76 7.49 55.98 6 52.5 6C49.02 6 46.24 7.49 44.47 8.25C43.2 8.56 42.28 7.96 42.03 6.68C42 6.49 42 6.02 42 5.07Z\" fill=\"currentColor\"/>\n");
                sb.append("  </g>\n</svg>\n");
            }
            case WINK -> {
                sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 63 15\" style=\"width:100%;height:auto;max-width:90%;max-height:40%;\">\n");
                sb.append("  <g ").append(groupStyle).append(">\n");
                sb.append("    <path d=\"M1 9C3 4 5 3 7.5 3C10 3 12 4 14 9\" stroke=\"currentColor\" stroke-width=\"3.5\" stroke-linecap=\"round\" fill=\"none\"/>\n");
                sb.append("    <circle cx=\"55.2\" cy=\"7.2\" r=\"7.2\" fill=\"currentColor\"/>\n");
                sb.append("  </g>\n</svg>\n");
            }
            case HEART -> {
                sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 63 15\" style=\"width:100%;height:auto;max-width:90%;max-height:40%;\">\n");
                sb.append("  <g ").append(groupStyle).append(">\n");
                sb.append("    <path d=\"M7.5 14C5 11 0 8.5 0 5C0 2.2 2.2 0 5 0C6.5 0 7.5 1.2 7.5 1.2C7.5 1.2 8.5 0 10 0C12.8 0 15 2.2 15 5C15 8.5 10 11 7.5 14Z\" fill=\"currentColor\"/>\n");
                sb.append("    <path d=\"M55.5 14C53 11 48 8.5 48 5C48 2.2 50.2 0 53 0C54.5 0 55.5 1.2 55.5 1.2C55.5 1.2 56.5 0 58 0C60.8 0 63 2.2 63 5C63 8.5 58 11 55.5 14Z\" fill=\"currentColor\"/>\n");
                sb.append("  </g>\n</svg>\n");
            }
        }
    }

    private static void appendEyebrowSvg(StringBuilder sb, EyebrowType eyebrow) {
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 63 8\" style=\"width:100%;height:auto;\">\n");
        switch (eyebrow) {
            case ARCHED -> {
                sb.append("  <path d=\"M2 7C4 2.5 9 0.5 14 2\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"3\" stroke-linecap=\"round\"/>\n");
                sb.append("  <path d=\"M49 2C54 0.5 59 2.5 61 7\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"3\" stroke-linecap=\"round\"/>\n");
            }
            case FLAT -> {
                sb.append("  <path d=\"M2 4H14\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"3\" stroke-linecap=\"round\"/>\n");
                sb.append("  <path d=\"M49 4H61\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"3\" stroke-linecap=\"round\"/>\n");
            }
            case RAISED -> {
                sb.append("  <path d=\"M2 6C5 1 11 0 14 1.5\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"3\" stroke-linecap=\"round\"/>\n");
                sb.append("  <path d=\"M49 1.5C52 0 58 1 61 6\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"3\" stroke-linecap=\"round\"/>\n");
            }
            case NONE -> {}
        }
        sb.append("</svg>\n");
    }

    private static void appendMouthSvg(StringBuilder sb, MouthType mouth) {
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 30 16\" style=\"width:100%;height:auto;\">\n");
        switch (mouth) {
            case SMILE -> sb.append("  <path d=\"M4 5C8 14 22 14 26 5\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"3\" stroke-linecap=\"round\"/>\n");
            case GRIN -> sb.append("  <path d=\"M3 5C5 14 10 16 15 16C20 16 25 14 27 5\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"3\" stroke-linecap=\"round\"/>\n");
            case O -> sb.append("  <ellipse cx=\"15\" cy=\"8\" rx=\"5\" ry=\"6\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"3\"/>\n");
            case CAT -> sb.append("  <path d=\"M5 5C7 12 13 12 15 5C17 12 23 12 25 5\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"3\" stroke-linecap=\"round\"/>\n");
            case TONGUE -> {
                sb.append("  <path d=\"M10 9C10 12.5 11 14.5 13 14.5C15 14.5 16 12.5 16 9\" fill=\"#FF6B8A\" opacity=\"0.7\"/>\n");
                sb.append("  <path d=\"M5 4C9 13 21 13 25 4\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"3\" stroke-linecap=\"round\"/>\n");
            }
            case SMIRK -> sb.append("  <path d=\"M8 8C12 10 18 6 22 4\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"3\" stroke-linecap=\"round\"/>\n");
            case FLAT -> sb.append("  <path d=\"M8 8H22\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"3\" stroke-linecap=\"round\"/>\n");
        }
        sb.append("</svg>\n");
    }

    private static void appendAccessorySvg(StringBuilder sb, AccessoryType accessory) {
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 63 20\" style=\"width:110%;height:auto;\">\n");
        switch (accessory) {
            case GLASSES -> {
                sb.append("  <circle cx=\"11\" cy=\"11\" r=\"8\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\"/>\n");
                sb.append("  <circle cx=\"52\" cy=\"11\" r=\"8\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\"/>\n");
                sb.append("  <path d=\"M19 11C19 6 44 6 44 11\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\"/>\n");
            }
            case SUNGLASSES -> {
                sb.append("  <rect x=\"1\" y=\"4\" width=\"20\" height=\"14\" rx=\"4\" fill=\"currentColor\" opacity=\"0.8\"/>\n");
                sb.append("  <rect x=\"42\" y=\"4\" width=\"20\" height=\"14\" rx=\"4\" fill=\"currentColor\" opacity=\"0.8\"/>\n");
                sb.append("  <path d=\"M21 10C21 5 42 5 42 10\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"3\" stroke-linecap=\"round\"/>\n");
            }
            case NONE -> {}
        }
        sb.append("</svg>\n");
    }
}
