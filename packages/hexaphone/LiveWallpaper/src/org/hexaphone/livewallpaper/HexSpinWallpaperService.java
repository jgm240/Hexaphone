package org.hexaphone.livewallpaper;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

/**
 * The boot animation's hex mark, spinning continuously as a live wallpaper:
 * same two-ring-opposite-directions motif as
 * vendor/hexaphone/art/gen_bootanim.py, just driven by wall-clock time on
 * a Canvas instead of pre-rendered frames. Deliberately simple --
 * everything is stroked/filled Path drawing (no Bitmap.Config.HARDWARE
 * decode anywhere in this file, so this doesn't touch the same broken-HWC
 * hardware-bitmap path that made static wallpapers go blank; see
 * scripts/patch-imagewallpaper.sh for that bug).
 */
public class HexSpinWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new HexEngine();
    }

    private class HexEngine extends Engine {

        private static final long FRAME_INTERVAL_MS = 1000L / 24L;
        private static final float OUTER_DEG_PER_SEC = 12f;  // one turn per 30s
        private static final float INNER_DEG_PER_SEC = -24f; // opposite direction, 2x rate

        private final Handler handler = new Handler();
        private final Runnable drawRunnable = new Runnable() {
            @Override
            public void run() {
                drawFrame();
            }
        };

        private final Path outerPath = new Path();
        private final Path innerPath = new Path();
        private final Path centerPath = new Path();
        private final Paint outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private boolean visible = true;
        private float outerRotDeg = 0f;
        private float innerRotDeg = 30f;
        private long lastFrameNanos = 0;
        private int glowW = -1;
        private int glowH = -1;

        HexEngine() {
            outerPaint.setStyle(Paint.Style.STROKE);
            outerPaint.setStrokeWidth(5f);
            outerPaint.setColor(0xFFF2B705);

            innerPaint.setStyle(Paint.Style.STROKE);
            innerPaint.setStrokeWidth(2.5f);
            innerPaint.setColor(0x8AF2B705);

            centerPaint.setStyle(Paint.Style.FILL);
            centerPaint.setColor(0xFFF2B705);

            dotPaint.setStyle(Paint.Style.FILL);
            dotPaint.setColor(0xD8FFD54A);

            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(1.5f);
            linePaint.setColor(0x59F2B705);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            handler.removeCallbacks(drawRunnable);
            if (visible) {
                lastFrameNanos = 0; // don't jump-spin by the time we were hidden
                handler.post(drawRunnable);
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            visible = false;
            handler.removeCallbacks(drawRunnable);
        }

        private void drawFrame() {
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) {
                    render(canvas);
                }
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas);
                }
            }
            handler.removeCallbacks(drawRunnable);
            if (visible) {
                handler.postDelayed(drawRunnable, FRAME_INTERVAL_MS);
            }
        }

        private void render(Canvas canvas) {
            int w = canvas.getWidth();
            int h = canvas.getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }

            long now = System.nanoTime();
            if (lastFrameNanos != 0) {
                float dt = (now - lastFrameNanos) / 1_000_000_000f;
                outerRotDeg = (outerRotDeg + OUTER_DEG_PER_SEC * dt) % 360f;
                innerRotDeg = (innerRotDeg + INNER_DEG_PER_SEC * dt) % 360f;
            }
            lastFrameNanos = now;

            canvas.drawColor(Color.BLACK);

            float cx = w / 2f;
            float cy = h * 0.46f;
            float outerR = Math.min(w, h) * 0.22f;
            float innerR = outerR * 0.62f;
            float centerR = outerR * 0.14f;

            if (w != glowW || h != glowH) {
                glowW = w;
                glowH = h;
                float glowR = outerR * 3.2f;
                glowPaint.setShader(new RadialGradient(cx, cy, glowR,
                        new int[] {0x3AF2B705, 0x14F2B705, 0x00F2B705},
                        new float[] {0f, 0.45f, 1f},
                        Shader.TileMode.CLAMP));
            }
            canvas.drawCircle(cx, cy, outerR * 3.2f, glowPaint);

            buildHex(outerPath, cx, cy, outerR, outerRotDeg);
            buildHex(innerPath, cx, cy, innerR, innerRotDeg);
            buildHex(centerPath, cx, cy, centerR, 0f);

            for (int i = 0; i < 6; i++) {
                double angle = Math.toRadians(60 * i + outerRotDeg);
                float dotX = (float) (cx + outerR * Math.cos(angle));
                float dotY = (float) (cy + outerR * Math.sin(angle));
                float lineStartX = (float) (cx + (centerR + 6) * Math.cos(angle));
                float lineStartY = (float) (cy + (centerR + 6) * Math.sin(angle));
                float lineEndX = (float) (cx + (outerR - 5) * Math.cos(angle));
                float lineEndY = (float) (cy + (outerR - 5) * Math.sin(angle));
                canvas.drawLine(lineStartX, lineStartY, lineEndX, lineEndY, linePaint);
                canvas.drawCircle(dotX, dotY, 4.5f, dotPaint);
            }

            canvas.drawPath(outerPath, outerPaint);
            canvas.drawPath(innerPath, innerPaint);
            canvas.drawPath(centerPath, centerPaint);
        }

        private void buildHex(Path path, float cx, float cy, float r, float rotDeg) {
            path.rewind();
            for (int i = 0; i < 6; i++) {
                double angle = Math.toRadians(60 * i + rotDeg);
                float x = (float) (cx + r * Math.cos(angle));
                float y = (float) (cy + r * Math.sin(angle));
                if (i == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            path.close();
        }
    }
}
