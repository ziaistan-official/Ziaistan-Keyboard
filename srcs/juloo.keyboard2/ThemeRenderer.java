package juloo.keyboard2;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

public class ThemeRenderer {

    public static boolean drawKeyBackground(Canvas canvas, float x, float y, float w, float h,
                                            Paint bgPaint, float radius, String themeName,
                                            boolean isPressed, float distortionX, float distortionY) {
        if (themeName == null) return false;

        switch (themeName.toLowerCase()) {
            case "waterdrop": drawWaterDropKey(canvas, x, y, w, h, bgPaint, radius, isPressed, distortionX, distortionY); return true;
            case "sponge": drawSpongeKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "metal": drawMetalKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "wood": drawWoodKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "glass": drawGlassKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "plastic": drawPlasticKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "leather": drawLeatherKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "denim": drawDenimKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "stone": drawStoneKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "brick": drawBrickKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "marble": drawMarbleKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "carbonfiber": drawCarbonFiberKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "circuit": drawCircuitKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "grid": drawGridKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "paper": drawPaperKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "cork": drawCorkKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "fabric": drawFabricKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "knitted": drawKnittedKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "ice": drawIceKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "fire": drawFireKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "sky": drawSkyKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "sand": drawSandKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "forestcamo": drawForestCamoKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "chalkboard": drawChalkboardKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "retro": drawRetroKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;


            case "cyberpunk": drawCyberpunkKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "liquid_glass": drawLiquidGlassKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "mechanical_rgb": drawMechanicalRGBKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "magma_ember": drawMagmaEmberKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "ink_parchment": drawInkParchmentKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "cosmic_nebula": drawCosmicNebulaKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "sakura_garden": drawSakuraGardenKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "retro_8bit": drawRetro8BitKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "golden_era": drawGoldenEraKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "deep_ocean": drawDeepOceanKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "neon_rain": drawNeonRainKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "candy_crush": drawCandyCrushKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "steampunk": drawSteampunkKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "holographic": drawHolographicKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "spirit_realm": drawSpiritRealmKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "golden_luxury": drawGoldenLuxuryKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "sakura_breeze": drawSakuraBreezeKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "bioluminescence": drawBioluminescenceKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "retro_arcade": drawRetroArcadeKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "crystal_prism": drawCrystalPrismKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "vaporwave": drawVaporwaveKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "noir_rain": drawNoirRainKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "paper_cutout": drawPaperCutoutKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "star_field": drawStarFieldKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
            case "gears": drawGearsKey(canvas, x, y, w, h, bgPaint, radius, isPressed); return true;
        }
        return false;
    }


    private static void drawWaterDropKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed, float dX, float dY) {
        float cx = x + w/2 + dX; float cy = y + h/2 + dY;
        RectF rect = new RectF(x + dX, y + dY, x + w + dX, y + h + dY);
        Paint paint = new Paint(bgPaint); paint.setAntiAlias(true);
        int color = paint.getColor();
        int lighter = Color.argb(220, Math.min(255, Color.red(color) + 60), Math.min(255, Color.green(color) + 60), Math.min(255, Color.blue(color) + 60));
        paint.setShader(new RadialGradient(cx - w/4, cy - h/4, w, lighter, color, Shader.TileMode.CLAMP));
        paint.setShadowLayer(8, 0, 4, 0x40000000);
        canvas.drawRoundRect(rect, Math.min(w, h)/2f, Math.min(w, h)/2f, paint);
        paint.clearShadowLayer();
        Paint highlight = new Paint(); highlight.setColor(0xA0FFFFFF); highlight.setStyle(Paint.Style.FILL); highlight.setAntiAlias(true);
        canvas.drawCircle(cx - w/3, cy - h/3, w/5, highlight);
    }
    private static void drawSpongeKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); p.setStyle(Paint.Style.FILL); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); Paint dot = new Paint(); dot.setStyle(Paint.Style.FILL); dot.setColor(0x15000000); dot.setAntiAlias(true); java.util.Random rand = new java.util.Random((long)(x * y)); for (int i=0; i<8; i++) { canvas.drawCircle(x + rand.nextFloat()*w, y + rand.nextFloat()*h, rand.nextFloat() * (w/6), dot); } }
    private static void drawMetalKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); int c = p.getColor(); int light = Color.argb(255, Math.min(255, Color.red(c)+40), Math.min(255, Color.green(c)+40), Math.min(255, Color.blue(c)+40)); int dark = Color.argb(255, Math.max(0, Color.red(c)-40), Math.max(0, Color.green(c)-40), Math.max(0, Color.blue(c)-40)); p.setShader(new LinearGradient(x, y, x + w, y + h, new int[]{light, c, dark}, null, Shader.TileMode.CLAMP)); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); Paint border = new Paint(); border.setStyle(Paint.Style.STROKE); border.setStrokeWidth(2f); border.setColor(0x80000000); canvas.drawRoundRect(x, y, x+w, y+h, r, r, border); }
    private static void drawWoodKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); int c = p.getColor(); int grain = Color.argb(255, Math.max(0, Color.red(c)-30), Math.max(0, Color.green(c)-30), Math.max(0, Color.blue(c)-30)); p.setShader(new LinearGradient(x, y, x+w, y+h, new int[]{c, grain, c, grain}, new float[]{0, 0.3f, 0.6f, 1}, Shader.TileMode.REPEAT)); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); Paint border = new Paint(); border.setStyle(Paint.Style.STROKE); border.setStrokeWidth(2f); border.setColor(0xFF3E2723); canvas.drawRoundRect(x, y, x+w, y+h, r, r, border); }
    private static void drawGlassKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); p.setColor(0x20FFFFFF); p.setStyle(Paint.Style.FILL); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); Paint highlight = new Paint(); highlight.setShader(new LinearGradient(x, y, x, y+h/2, 0x40FFFFFF, 0x05FFFFFF, Shader.TileMode.CLAMP)); canvas.drawRoundRect(x, y, x+w, y+h/2, r, r, highlight); Paint border = new Paint(); border.setStyle(Paint.Style.STROKE); border.setStrokeWidth(1.5f); border.setColor(0x50FFFFFF); canvas.drawRoundRect(x, y, x+w, y+h, r, r, border); }
    private static void drawPlasticKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); Paint highlight = new Paint(); highlight.setColor(0x60FFFFFF); RectF oval = new RectF(x + 5, y + 5, x + w - 5, y + h/2); canvas.drawOval(oval, highlight); }
    private static void drawLeatherKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); Paint texture = new Paint(); texture.setColor(0x10000000); java.util.Random rand = new java.util.Random((long)(x*y)); for(int i=0; i<30; i++) { canvas.drawCircle(x + rand.nextFloat()*w, y + rand.nextFloat()*h, 1 + rand.nextFloat(), texture); } Paint stitch = new Paint(); stitch.setColor(0x80FFFFFF); stitch.setStyle(Paint.Style.STROKE); stitch.setStrokeWidth(2f); stitch.setPathEffect(new DashPathEffect(new float[]{5, 5}, 0)); float inset = 5f; canvas.drawRoundRect(x+inset, y+inset, x+w-inset, y+h-inset, r, r, stitch); }
    private static void drawDenimKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); Paint thread = new Paint(); thread.setColor(0x15FFFFFF); thread.setStrokeWidth(1f); for(float i=x; i<x+w; i+=4) canvas.drawLine(i, y, i-3, y+h, thread); for(float i=y; i<y+h; i+=4) canvas.drawLine(x, i, x+w, i, thread); }
    private static void drawStoneKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); Paint speckle = new Paint(); java.util.Random rand = new java.util.Random((long)(x*y)); for(int i=0; i<40; i++) { speckle.setColor(rand.nextBoolean() ? 0x20FFFFFF : 0x20000000); canvas.drawCircle(x + rand.nextFloat()*w, y + rand.nextFloat()*h, 1.5f, speckle); } }
    private static void drawBrickKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); Paint mortar = new Paint(); mortar.setColor(0x20000000); mortar.setStrokeWidth(2f); canvas.drawLine(x, y + h/3, x+w, y + h/3, mortar); canvas.drawLine(x, y + 2*h/3, x+w, y + 2*h/3, mortar); canvas.drawLine(x + w/2, y, x + w/2, y + h/3, mortar); canvas.drawLine(x + w/4, y + h/3, x + w/4, y + 2*h/3, mortar); canvas.drawLine(x + 3*w/4, y + h/3, x + 3*w/4, y + 2*h/3, mortar); canvas.drawLine(x + w/2, y + 2*h/3, x + w/2, y + h, mortar); }
    private static void drawMarbleKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); Paint vein = new Paint(); vein.setColor(0x15000000); vein.setStyle(Paint.Style.STROKE); vein.setStrokeWidth(2f); vein.setAntiAlias(true); java.util.Random rand = new java.util.Random((long)(x*y)); Path path = new Path(); path.moveTo(x + rand.nextFloat()*w, y); path.cubicTo(x + rand.nextFloat()*w, y + h/3, x + rand.nextFloat()*w, y + 2*h/3, x + rand.nextFloat()*w, y + h); canvas.drawPath(path, vein); }
    private static void drawCarbonFiberKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); Paint fiber = new Paint(); fiber.setColor(0x10FFFFFF); fiber.setStrokeWidth(3f); for(float i=x-h; i<x+w; i+=8) { canvas.drawLine(i, y, i+h, y+h, fiber); } fiber.setColor(0x10000000); for(float i=x; i<x+w+h; i+=8) { canvas.drawLine(i, y, i-h, y+h, fiber); } }
    private static void drawCircuitKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); Paint trace = new Paint(); trace.setColor(0x4000FF00); trace.setStrokeWidth(2f); trace.setStyle(Paint.Style.STROKE); java.util.Random rand = new java.util.Random((long)(x*y)); for(int i=0; i<3; i++) { float sx = x + rand.nextFloat()*w; float sy = y + rand.nextFloat()*h; float ex = x + rand.nextFloat()*w; float ey = y + rand.nextFloat()*h; Path path = new Path(); path.moveTo(sx, sy); if (rand.nextBoolean()) { path.lineTo(ex, sy); } else { path.lineTo(sx, ey); } path.lineTo(ex, ey); canvas.drawPath(path, trace); trace.setStyle(Paint.Style.FILL); canvas.drawCircle(ex, ey, 3f, trace); trace.setStyle(Paint.Style.STROKE); } }
    private static void drawGridKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); Paint grid = new Paint(); grid.setColor(0x20BB86FC); grid.setStrokeWidth(1f); float step = 15f; for(float i=x; i<x+w; i+=step) canvas.drawLine(i, y, i, y+h, grid); for(float i=y; i<y+h; i+=step) canvas.drawLine(x, i, x+w, i, grid); }
    private static void drawPaperKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); Paint line = new Paint(); line.setColor(0x20000000); line.setStrokeWidth(1f); float step = h / 4; for(int i=1; i<=3; i++) { canvas.drawLine(x, y + i*step, x+w, y + i*step, line); } line.setColor(0x20FF0000); canvas.drawLine(x + w/5, y, x + w/5, y+h, line); }
    private static void drawCorkKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); Paint hole = new Paint(); hole.setColor(0x303E2723); java.util.Random rand = new java.util.Random((long)(x*y)); for(int i=0; i<20; i++) { canvas.drawCircle(x + rand.nextFloat()*w, y + rand.nextFloat()*h, 1 + rand.nextFloat()*2, hole); } }
    private static void drawFabricKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); Paint weave = new Paint(); weave.setColor(0x10000000); weave.setStrokeWidth(1f); float step = 3f; for(float i=x; i<x+w; i+=step) canvas.drawLine(i, y, i, y+h, weave); for(float i=y; i<y+h; i+=step) canvas.drawLine(x, i, x+w, i, weave); }
    private static void drawKnittedKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); Paint knit = new Paint(); knit.setColor(0x15FFFFFF); knit.setStyle(Paint.Style.STROKE); knit.setStrokeWidth(1.5f); float stepX = 10f; float stepY = 8f; for(float py=y; py<y+h; py+=stepY) { for(float px=x; px<x+w; px+=stepX) { canvas.drawLine(px, py, px+stepX/2, py+stepY, knit); canvas.drawLine(px+stepX/2, py+stepY, px+stepX, py, knit); } } }
    private static void drawIceKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); int c = p.getColor(); p.setShader(new LinearGradient(x, y, x, y+h, c, Color.rgb(220, 240, 255), Shader.TileMode.CLAMP)); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); p.setShader(null); Paint crack = new Paint(); crack.setColor(0x60FFFFFF); crack.setStyle(Paint.Style.STROKE); crack.setStrokeWidth(1f); java.util.Random rand = new java.util.Random((long)(x*y)); if (rand.nextFloat() > 0.5f) { Path path = new Path(); path.moveTo(x + rand.nextFloat()*w, y); path.lineTo(x + rand.nextFloat()*w, y + h/2); path.lineTo(x + rand.nextFloat()*w, y + h); canvas.drawPath(path, crack); } }
    private static void drawFireKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); p.setShader(new LinearGradient(x, y+h, x, y, new int[]{0xFFFF0000, 0xFFFF8800, 0xFFFFFF00}, null, Shader.TileMode.CLAMP)); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); }
    private static void drawSkyKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); p.setShader(new LinearGradient(x, y, x, y+h, 0xFF2196F3, 0xFFBBDEFB, Shader.TileMode.CLAMP)); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); Paint cloud = new Paint(); cloud.setColor(0x80FFFFFF); java.util.Random rand = new java.util.Random((long)(x*y)); canvas.drawCircle(x + rand.nextFloat()*w, y + rand.nextFloat()*h, w/3, cloud); canvas.drawCircle(x + rand.nextFloat()*w, y + rand.nextFloat()*h, w/4, cloud); }
    private static void drawSandKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); Paint grain = new Paint(); java.util.Random rand = new java.util.Random((long)(x*y)); for(int i=0; i<50; i++) { grain.setColor(rand.nextBoolean() ? 0x20FFFFFF : 0x208B4513); canvas.drawCircle(x + rand.nextFloat()*w, y + rand.nextFloat()*h, 1f, grain); } Paint ripple = new Paint(); ripple.setColor(0x10000000); ripple.setStyle(Paint.Style.STROKE); ripple.setStrokeWidth(2f); Path path = new Path(); path.moveTo(x, y + h/2); path.quadTo(x+w/2, y+h/2 - 5, x+w, y+h/2); canvas.drawPath(path, ripple); }
    private static void drawForestCamoKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); p.setColor(0xFF4E6F45); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); Paint blob = new Paint(); java.util.Random rand = new java.util.Random((long)(x*y)); for(int i=0; i<3; i++) { blob.setColor(rand.nextBoolean() ? 0x803E2723 : 0x802E7D32); float bx = x + rand.nextFloat()*w; float by = y + rand.nextFloat()*h; float br = w/3 + rand.nextFloat()*(w/4); canvas.drawCircle(bx, by, br, blob); } }
    private static void drawChalkboardKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); Paint dust = new Paint(); dust.setColor(0x10FFFFFF); java.util.Random rand = new java.util.Random((long)(x*y)); for(int i=0; i<100; i++) { canvas.drawPoint(x + rand.nextFloat()*w, y + rand.nextFloat()*h, dust); } Paint smudge = new Paint(); smudge.setColor(0x05FFFFFF); smudge.setStrokeWidth(10f); canvas.drawLine(x, y+h/2, x+w, y+h/2, smudge); }
    private static void drawRetroKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { Paint p = new Paint(bgPaint); canvas.drawRoundRect(x, y, x+w, y+h, r, r, p); Paint scanline = new Paint(); scanline.setColor(0x10000000); scanline.setStrokeWidth(1f); for(float i=y; i<y+h; i+=3) { canvas.drawLine(x, i, x+w, i, scanline); } Paint vignette = new Paint(); vignette.setShader(new RadialGradient(x+w/2, y+h/2, w, 0x00000000, 0x40000000, Shader.TileMode.CLAMP)); canvas.drawRoundRect(x, y, x+w, y+h, r, r, vignette); }



    private static void drawCyberpunkKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) {
        Paint p = new Paint(bgPaint);
        p.setColor(0xFF0D0D0D);
        canvas.drawRoundRect(x, y, x+w, y+h, 4, 4, p);

        Paint neon = new Paint();
        neon.setStyle(Paint.Style.STROKE);
        neon.setStrokeWidth(isPressed ? 4 : 2);
        neon.setColor(isPressed ? 0xFF00FF88 : 0xFF008844);
        neon.setShadowLayer(isPressed ? 10 : 5, 0, 0, neon.getColor());
        canvas.drawRoundRect(x, y, x+w, y+h, 4, 4, neon);


        if (isPressed) {
            Paint glitch = new Paint();
            glitch.setColor(0xFFFF0055);
            glitch.setAlpha(128);
            canvas.drawRect(x-2, y+2, x+w+2, y+h-2, glitch);
        }
    }

    private static void drawLiquidGlassKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) {
        Paint p = new Paint(bgPaint);
        p.setColor(0x20FFFFFF);
        canvas.drawRoundRect(x, y, x+w, y+h, 15, 15, p);

        Paint highlight = new Paint();
        highlight.setShader(new LinearGradient(x, y, x, y+h/2, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(x, y, x+w, y+h/2, 15, 15, highlight);

        Paint border = new Paint();
        border.setStyle(Paint.Style.STROKE);
        border.setColor(0x50FFFFFF);
        border.setStrokeWidth(1);
        canvas.drawRoundRect(x, y, x+w, y+h, 15, 15, border);

        if (isPressed) {
            Paint ripple = new Paint();
            ripple.setColor(0x30FFFFFF);
            canvas.drawCircle(x+w/2, y+h/2, w/2, ripple);
        }
    }

    private static void drawMechanicalRGBKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) {
        Paint p = new Paint(bgPaint);
        p.setColor(0xFF1A1A1A);
        canvas.drawRect(x, y, x+w, y+h, p);


        Paint glow = new Paint();
        int tick = (int)(System.currentTimeMillis() / 10);
        int color = Color.HSVToColor(new float[]{(tick + x)%360, 1f, 1f});
        glow.setColor(color);
        glow.setStrokeWidth(3);
        glow.setStyle(Paint.Style.STROKE);
        glow.setShadowLayer(10, 0, 0, color);
        canvas.drawLine(x, y+h, x+w, y+h, glow);

        if (isPressed) {
            glow.setStyle(Paint.Style.FILL);
            glow.setAlpha(50);
            canvas.drawRect(x, y, x+w, y+h, glow);
        }
    }

    private static void drawMagmaEmberKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) {
        Paint p = new Paint(bgPaint);
        p.setShader(new LinearGradient(x, y, x, y+h, 0xFF2D1B1B, 0xFF422222, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(x, y, x+w, y+h, 6, 6, p);

        Paint crack = new Paint();
        crack.setColor(isPressed ? 0xFFFF5500 : 0xFF550000);
        crack.setStyle(Paint.Style.STROKE);
        crack.setStrokeWidth(2);
        if (isPressed) crack.setShadowLayer(10, 0, 0, 0xFFFF0000);

        canvas.drawLine(x+w/2, y+h/2, x+w/2 + 5, y+h/2 + 10, crack);
        canvas.drawLine(x+w/2, y+h/2, x+w/2 - 5, y+h/2 - 5, crack);
    }

    private static void drawInkParchmentKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) {
        Paint p = new Paint(bgPaint);
        p.setColor(0x00FFFFFF);
        canvas.drawRect(x, y, x+w, y+h, p);

        if (isPressed) {
            Paint ink = new Paint();
            ink.setColor(0xFF111111);
            ink.setMaskFilter(new android.graphics.BlurMaskFilter(4, android.graphics.BlurMaskFilter.Blur.NORMAL));
            canvas.drawCircle(x+w/2, y+h/2, w/2, ink);
        }
    }




    private static void drawCosmicNebulaKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) {
        Paint p = new Paint(bgPaint);
        p.setShader(new RadialGradient(x+w/2, y+h/2, w, 0xFF28143C, 0xFF0B001A, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(x, y, x+w, y+h, 12, 12, p);
        if (isPressed) {
            Paint star = new Paint(); star.setColor(0xFFFFFFFF);
            canvas.drawCircle(x+w/2, y+h/2, 2, star);
            canvas.drawCircle(x+w/4, y+h/4, 1, star);
        }
    }

    private static void drawSakuraGardenKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) {
        Paint p = new Paint(bgPaint);
        p.setColor(0xFFFFFFFF);
        canvas.drawRoundRect(x, y, x+w, y+h, 12, 12, p);
        Paint border = new Paint();
        border.setStyle(Paint.Style.STROKE);
        border.setColor(0xFFFFB7C5);
        border.setStrokeWidth(2);
        canvas.drawRoundRect(x, y, x+w, y+h, 12, 12, border);
    }

    private static void drawRetro8BitKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) {
        Paint p = new Paint(bgPaint);
        p.setColor(0xFF444444);
        canvas.drawRect(x, y, x+w, y+h, p);
        Paint shadow = new Paint();
        shadow.setColor(0xFF000000);
        canvas.drawRect(x+2, y+h-4, x+w-2, y+h, shadow);
    }

    private static void drawGoldenEraKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) {
        Paint p = new Paint(bgPaint);
        p.setColor(0xFF111111);
        canvas.drawRoundRect(x, y, x+w, y+h, 4, 4, p);
        Paint border = new Paint();
        border.setStyle(Paint.Style.STROKE);
        border.setColor(0xFFD4AF37);
        canvas.drawRoundRect(x, y, x+w, y+h, 4, 4, border);
    }

    private static void drawDeepOceanKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) {
        Paint p = new Paint(bgPaint);
        p.setColor(0x80001E3C);
        canvas.drawRoundRect(x, y, x+w, y+h, 8, 8, p);
        Paint border = new Paint();
        border.setStyle(Paint.Style.STROKE);
        border.setColor(0xFF00FFFF);
        border.setShadowLayer(isPressed?10:0, 0, 0, 0xFF00FFFF);
        canvas.drawRoundRect(x, y, x+w, y+h, 8, 8, border);
    }

    private static void drawNeonRainKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) {
        Paint p = new Paint(bgPaint);
        p.setShader(new LinearGradient(x, y, x, y+h, 0xFF222222, 0xFF111111, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(x, y, x+w, y+h, 4, 4, p);
        Paint border = new Paint();
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(1);
        border.setColor(0xFF555555);
        canvas.drawLine(x, y, x+w, y, border);
    }

    private static void drawCandyCrushKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) {
        Paint p = new Paint(bgPaint);
        p.setShader(new LinearGradient(x, y, x+w, y+h, 0xFFFF9A9E, 0xFFFECFEF, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(x, y, x+w, y+h, h/2, h/2, p);
        Paint border = new Paint();
        border.setStyle(Paint.Style.STROKE);
        border.setColor(0xFFFFFFFF);
        border.setStrokeWidth(2);
        canvas.drawRoundRect(x, y, x+w, y+h, h/2, h/2, border);
    }

    private static void drawSteampunkKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) {
        Paint p = new Paint(bgPaint);
        p.setShader(new RadialGradient(x+w/2, y+h/2, w/2, 0xFF4E342E, 0xFF3E2723, Shader.TileMode.CLAMP));
        canvas.drawOval(x, y, x+w, y+h, p);
        Paint border = new Paint();
        border.setStyle(Paint.Style.STROKE);
        border.setColor(0xFF5D4037);
        border.setStrokeWidth(4);
        canvas.drawOval(x, y, x+w, y+h, border);
    }

    private static void drawHolographicKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) {
        Paint p = new Paint(bgPaint);
        p.setColor(0x0DFFFFFF);
        canvas.drawRoundRect(x, y, x+w, y+h, 4, 4, p);
        Paint border = new Paint();
        border.setStyle(Paint.Style.STROKE);
        border.setColor(0x33FFFFFF);
        canvas.drawRoundRect(x, y, x+w, y+h, 4, 4, border);
        if (isPressed) {
            p.setShader(new LinearGradient(x, y, x+w, y+h, 0x33FF0000, 0x330000FF, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(x, y, x+w, y+h, 4, 4, p);
        }
    }

    private static void drawSpiritRealmKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) {
        Paint p = new Paint(bgPaint);
        p.setColor(0xFF101818);
        canvas.drawRoundRect(x, y, x+w, y+h, 4, 4, p);
        Paint glow = new Paint();
        glow.setShadowLayer(15, 0, 0, 0x0D00FFFF);
        canvas.drawRoundRect(x, y, x+w, y+h, 4, 4, glow);
    }

    private static void drawGoldenLuxuryKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { drawGoldenEraKey(canvas, x, y, w, h, bgPaint, r, isPressed); }
    private static void drawSakuraBreezeKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { drawSakuraGardenKey(canvas, x, y, w, h, bgPaint, r, isPressed); }
    private static void drawBioluminescenceKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { drawDeepOceanKey(canvas, x, y, w, h, bgPaint, r, isPressed); }
    private static void drawRetroArcadeKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { drawRetro8BitKey(canvas, x, y, w, h, bgPaint, r, isPressed); }
    private static void drawCrystalPrismKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { drawHolographicKey(canvas, x, y, w, h, bgPaint, r, isPressed); }
    private static void drawVaporwaveKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { drawSynthwaveKey(canvas, x, y, w, h, bgPaint, r, isPressed); }
    private static void drawNoirRainKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { drawNeonRainKey(canvas, x, y, w, h, bgPaint, r, isPressed); }
    private static void drawPaperCutoutKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { drawPaperKey(canvas, x, y, w, h, bgPaint, r, isPressed); }
    private static void drawStarFieldKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { drawCosmicNebulaKey(canvas, x, y, w, h, bgPaint, r, isPressed); }
    private static void drawGearsKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) { drawSteampunkKey(canvas, x, y, w, h, bgPaint, r, isPressed); }


    private static void drawSynthwaveKey(Canvas canvas, float x, float y, float w, float h, Paint bgPaint, float r, boolean isPressed) {
        Paint p = new Paint(bgPaint);

        p.setShader(new LinearGradient(x, y, x, y+h, 0xFF2B002B, 0xFF550055, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(x, y, x+w, y+h, 6, 6, p);


        Paint line = new Paint();
        line.setColor(0xFF00FFFF);
        line.setStrokeWidth(2);
        line.setAlpha(100);
        for(float i=y; i<y+h; i+=8) {
            canvas.drawLine(x, i, x+w, i, line);
        }
    }
}
