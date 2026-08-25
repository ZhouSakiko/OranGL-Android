package com.zhousaki.orangl;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import java.util.Arrays;

public class OranGL {

    // ---------- 帧缓冲 ----------
    private int[] framebuffer;
    private int width, height;
    private int currentColor = 0xFFFFFFFF;
    private int clearColor = 0xFF000000;

    // ---------- 模型矩阵 ----------
    private float[] matrix = new float[9];
    private float[] matrixStack = new float[9 * 16];
    private int stackPointer = -1;

    // ---------- 投影矩阵 ----------
    private float[] projMatrix = new float[9];
    private float[] projMatrixStack = new float[9 * 16];
    private int projStackPointer = -1;

    // ---------- 视口 ----------
    private int vpX, vpY, vpW, vpH;

    // ---------- 裁剪 ----------
    private int clipX1, clipY1, clipX2, clipY2;

    // ---------- 纹理 ----------
    private Bitmap currentTexture;
    private int texWidth, texHeight;
    public static final int GL_REPEAT = 0x2901;
    public static final int GL_CLAMP_TO_EDGE = 0x812F;
    private int wrapS = GL_CLAMP_TO_EDGE;
    private int wrapT = GL_CLAMP_TO_EDGE;

    // ---------- 混合 ----------
    private boolean blendEnabled = false;
    private int srcBlend = GL_SRC_ALPHA;
    private int dstBlend = GL_ONE_MINUS_SRC_ALPHA;

    public static final int GL_ZERO = 0;
    public static final int GL_ONE = 1;
    public static final int GL_SRC_COLOR = 2;
    public static final int GL_ONE_MINUS_SRC_COLOR = 3;
    public static final int GL_DST_COLOR = 4;
    public static final int GL_ONE_MINUS_DST_COLOR = 5;
    public static final int GL_SRC_ALPHA = 6;
    public static final int GL_ONE_MINUS_SRC_ALPHA = 7;
    public static final int GL_DST_ALPHA = 8;
    public static final int GL_ONE_MINUS_DST_ALPHA = 9;

    // ---------- 临时数组 ----------
    private float[] tempPoint = new float[2];
    private int[] tempXs = new int[3];
    private int[] tempYs = new int[3];
    private float[] verts = new float[12];

    // ---------- 文字渲染 ----------
    private Paint textPaint;
    private String cachedText = null;
    private Bitmap cachedTextBitmap = null;
    private int cachedTextColor = 0;

    // ---------- 构造函数 ----------
    public OranGL(int width, int height) {
        this.width = width;
        this.height = height;
        framebuffer = new int[width * height];
        setIdentity();
        resetProjection();
        setClip(0, 0, width - 1, height - 1);
        clear();
        currentTexture = null;
        textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setTextSize(24);
        textPaint.setColor(Color.WHITE);
    }

    // ---------- 矩阵操作（模型） ----------
    private void setIdentity() {
        Arrays.fill(matrix, 0f);
        matrix[0] = 1f; matrix[4] = 1f; matrix[8] = 1f;
    }

    public void translate(float tx, float ty) {
        float[] m = new float[9];
        m[0] = 1f; m[4] = 1f; m[8] = 1f;
        m[6] = tx; m[7] = ty;
        multiplyMatrix(m);
    }

    public void rotate(float angleDeg) {
        float rad = (float) Math.toRadians(angleDeg);
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        float[] m = new float[9];
        m[0] = c;  m[1] = s;
        m[3] = -s; m[4] = c;
        m[8] = 1f;
        multiplyMatrix(m);
    }

    public void scale(float sx, float sy) {
        float[] m = new float[9];
        m[0] = sx; m[4] = sy; m[8] = 1f;
        multiplyMatrix(m);
    }

    private void multiplyMatrix(float[] m) {
        float[] result = new float[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                float sum = 0f;
                for (int k = 0; k < 3; k++) {
                    sum += m[i * 3 + k] * matrix[k * 3 + j];
                }
                result[i * 3 + j] = sum;
            }
        }
        System.arraycopy(result, 0, matrix, 0, 9);
    }

    public void pushMatrix() {
        if (stackPointer < 15) {
            stackPointer++;
            System.arraycopy(matrix, 0, matrixStack, stackPointer * 9, 9);
        }
    }

    public void popMatrix() {
        if (stackPointer >= 0) {
            System.arraycopy(matrixStack, stackPointer * 9, matrix, 0, 9);
            stackPointer--;
        }
    }

    // ---------- 投影与视口 ----------
    public void setViewport(int x, int y, int w, int h) {
        vpX = Math.max(0, Math.min(width - 1, x));
        vpY = Math.max(0, Math.min(height - 1, y));
        vpW = Math.min(width - vpX, Math.max(1, w));
        vpH = Math.min(height - vpY, Math.max(1, h));
    }

    public void setOrtho(float left, float right, float bottom, float top) {
        if (right - left == 0 || top - bottom == 0) return;
        float scaleX = vpW / (right - left);
        float scaleY = -vpH / (top - bottom);
        float transX = vpX - left * scaleX;
        float transY = (vpY + vpH) - bottom * scaleY;
        projMatrix[0] = scaleX; projMatrix[3] = 0;      projMatrix[6] = transX;
        projMatrix[1] = 0;      projMatrix[4] = scaleY; projMatrix[7] = transY;
        projMatrix[2] = 0;      projMatrix[5] = 0;      projMatrix[8] = 1;
    }

    public void resetProjection() {
        vpX = 0; vpY = 0; vpW = width; vpH = height;
        setIdentityProj();
    }

    private void setIdentityProj() {
        Arrays.fill(projMatrix, 0f);
        projMatrix[0] = 1f; projMatrix[4] = 1f; projMatrix[8] = 1f;
    }

    public void pushProjection() {
        if (projStackPointer < 15) {
            projStackPointer++;
            System.arraycopy(projMatrix, 0, projMatrixStack, projStackPointer * 9, 9);
        }
    }

    public void popProjection() {
        if (projStackPointer >= 0) {
            System.arraycopy(projMatrixStack, projStackPointer * 9, projMatrix, 0, 9);
            projStackPointer--;
        }
    }

    private void transform(float x, float y, float[] out) {
        float wx = matrix[0] * x + matrix[3] * y + matrix[6];
        float wy = matrix[1] * x + matrix[4] * y + matrix[7];
        out[0] = projMatrix[0] * wx + projMatrix[3] * wy + projMatrix[6];
        out[1] = projMatrix[1] * wx + projMatrix[4] * wy + projMatrix[7];
    }

    // ---------- 裁剪 ----------
    public void setClip(int x1, int y1, int x2, int y2) {
        clipX1 = Math.max(0, Math.min(x1, x2));
        clipY1 = Math.max(0, Math.min(y1, y2));
        clipX2 = Math.min(width - 1, Math.max(x1, x2));
        clipY2 = Math.min(height - 1, Math.max(y1, y2));
    }

    // ---------- 混合控制 ----------
    public void enableBlend() { blendEnabled = true; }
    public void disableBlend() { blendEnabled = false; }
    public void setBlendFunc(int src, int dst) { srcBlend = src; dstBlend = dst; }

    // ---------- 像素写入 ----------
    private void writePixelRaw(int x, int y, int color) {
        if (x < clipX1 || x > clipX2 || y < clipY1 || y > clipY2) return;
        int idx = y * width + x;
        if (idx < 0 || idx >= framebuffer.length) return;

        if (!blendEnabled) {
            framebuffer[idx] = color;
            return;
        }

        int srcA = (color >> 24) & 0xFF;
        int srcR = (color >> 16) & 0xFF;
        int srcG = (color >> 8) & 0xFF;
        int srcB = color & 0xFF;

        if (srcA == 255) { framebuffer[idx] = color; return; }
        if (srcA == 0) return;

        int dst = framebuffer[idx];
        int dstA = (dst >> 24) & 0xFF;
        int dstR = (dst >> 16) & 0xFF;
        int dstG = (dst >> 8) & 0xFF;
        int dstB = dst & 0xFF;

        float sFactor = 1.0f, dFactor = 0.0f;
        switch (srcBlend) {
            case GL_SRC_ALPHA: sFactor = srcA / 255.0f; break;
            case GL_ONE:       sFactor = 1.0f; break;
            case GL_ZERO:      sFactor = 0.0f; break;
            default:           sFactor = srcA / 255.0f;
        }
        switch (dstBlend) {
            case GL_ONE_MINUS_SRC_ALPHA: dFactor = 1.0f - srcA / 255.0f; break;
            case GL_ONE:                 dFactor = 1.0f; break;
            case GL_ZERO:                dFactor = 0.0f; break;
            default:                     dFactor = 1.0f - srcA / 255.0f;
        }

        int outR = (int) (srcR * sFactor + dstR * dFactor);
        int outG = (int) (srcG * sFactor + dstG * dFactor);
        int outB = (int) (srcB * sFactor + dstB * dFactor);
        int outA = (int) (srcA * sFactor + dstA * dFactor);
        outR = Math.min(255, Math.max(0, outR));
        outG = Math.min(255, Math.max(0, outG));
        outB = Math.min(255, Math.max(0, outB));
        outA = Math.min(255, Math.max(0, outA));

        framebuffer[idx] = (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }

    public void putPixel(int x, int y) {
        transform(x, y, tempPoint);
        writePixelRaw(Math.round(tempPoint[0]), Math.round(tempPoint[1]), currentColor);
    }

    // ---------- 清屏 ----------
    public void clear() { Arrays.fill(framebuffer, clearColor); }
    public void setClearColor(int color) { clearColor = color; }
    public void setColor(int color) { currentColor = color; }

    // ---------- 纹理管理 ----------
    public void bindTexture(Bitmap texture) {
        if (texture == null) { currentTexture = null; return; }
        if (texture.getConfig() != Bitmap.Config.ARGB_8888) {
            Bitmap copy = texture.copy(Bitmap.Config.ARGB_8888, false);
            currentTexture = copy;
        } else {
            currentTexture = texture;
        }
        texWidth = currentTexture.getWidth();
        texHeight = currentTexture.getHeight();
    }

    public void setTextureWrap(int mode) {
        wrapS = mode;
        wrapT = mode;
    }
    public void setTextureWrapS(int mode) { wrapS = mode; }
    public void setTextureWrapT(int mode) { wrapT = mode; }

    private int sampleTexture(float u, float v) {
        if (currentTexture == null) return currentColor;
        float su = u, sv = v;
        if (wrapS == GL_REPEAT) {
            su = u - (float) Math.floor(u);
        } else {
            su = Math.max(0f, Math.min(1f, u));
        }
        if (wrapT == GL_REPEAT) {
            sv = v - (float) Math.floor(v);
        } else {
            sv = Math.max(0f, Math.min(1f, v));
        }
        int px = (int) (su * texWidth);
        int py = (int) (sv * texHeight);
        px = Math.max(0, Math.min(texWidth - 1, px));
        py = Math.max(0, Math.min(texHeight - 1, py));
        return currentTexture.getPixel(px, py);
    }

    // ---------- 基础图元 ----------
    public void drawLine(int x1, int y1, int x2, int y2) {
        transform(x1, y1, tempPoint);
        float fx1 = tempPoint[0], fy1 = tempPoint[1];
        transform(x2, y2, tempPoint);
        float fx2 = tempPoint[0], fy2 = tempPoint[1];
        wuLine(fx1, fy1, fx2, fy2);
    }

    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3) {
        transform(x1, y1, tempPoint);
        int sx1 = Math.round(tempPoint[0]), sy1 = Math.round(tempPoint[1]);
        transform(x2, y2, tempPoint);
        int sx2 = Math.round(tempPoint[0]), sy2 = Math.round(tempPoint[1]);
        transform(x3, y3, tempPoint);
        int sx3 = Math.round(tempPoint[0]), sy3 = Math.round(tempPoint[1]);

        int[] xs = tempXs; int[] ys = tempYs;
        xs[0] = sx1; xs[1] = sx2; xs[2] = sx3;
        ys[0] = sy1; ys[1] = sy2; ys[2] = sy3;
        sortPointsByY(xs, ys);

        int yStart = ys[0], yEnd = ys[2];
        for (int y = yStart; y <= yEnd; y++) {
            int xLeft = Integer.MAX_VALUE, xRight = Integer.MIN_VALUE;
            for (int i = 0; i < 3; i++) {
                int i2 = (i + 1) % 3;
                int y1i = ys[i], y2i = ys[i2];
                if ((y >= y1i && y <= y2i) || (y >= y2i && y <= y1i)) {
                    if (y1i == y2i) continue;
                    float t = (float) (y - y1i) / (y2i - y1i);
                    int x = Math.round(xs[i] + t * (xs[i2] - xs[i]));
                    if (x < xLeft) xLeft = x;
                    if (x > xRight) xRight = x;
                }
            }
            if (xLeft <= xRight) {
                for (int x = xLeft; x <= xRight; x++) {
                    writePixelRaw(x, y, currentColor);
                }
            }
        }
    }

    public void fillGouraudTriangle(int x1, int y1, int color1,
                                   int x2, int y2, int color2,
                                   int x3, int y3, int color3) {
        transform(x1, y1, tempPoint);
        float sx1 = tempPoint[0], sy1 = tempPoint[1];
        transform(x2, y2, tempPoint);
        float sx2 = tempPoint[0], sy2 = tempPoint[1];
        transform(x3, y3, tempPoint);
        float sx3 = tempPoint[0], sy3 = tempPoint[1];

        int a1 = (color1 >> 24) & 0xFF, r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
        int a2 = (color2 >> 24) & 0xFF, r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;
        int a3 = (color3 >> 24) & 0xFF, r3 = (color3 >> 16) & 0xFF, g3 = (color3 >> 8) & 0xFF, b3 = color3 & 0xFF;

        float[] v0 = {sx1, sy1, a1, r1, g1, b1};
        float[] v1 = {sx2, sy2, a2, r2, g2, b2};
        float[] v2 = {sx3, sy3, a3, r3, g3, b3};

        if (v0[1] > v1[1]) { float[] tmp = v0; v0 = v1; v1 = tmp; }
        if (v1[1] > v2[1]) { float[] tmp = v1; v1 = v2; v2 = tmp; }
        if (v0[1] > v1[1]) { float[] tmp = v0; v0 = v1; v1 = tmp; }

        float px0 = v0[0], py0 = v0[1];
        float px1 = v1[0], py1 = v1[1];
        float px2 = v2[0], py2 = v2[1];
        float pa0 = v0[2], pr0 = v0[3], pg0 = v0[4], pb0 = v0[5];
        float pa1 = v1[2], pr1 = v1[3], pg1 = v1[4], pb1 = v1[5];
        float pa2 = v2[2], pr2 = v2[3], pg2 = v2[4], pb2 = v2[5];

        int yStart = (int) Math.ceil(py0);
        int yEnd = (int) Math.floor(py2);
        if (yStart < clipY1) yStart = clipY1;
        if (yEnd > clipY2) yEnd = clipY2;

        float dy1 = py1 - py0;
        float dy2 = py2 - py0;

        for (int y = yStart; y <= yEnd; y++) {
            float fy = y;
            float leftX, rightX;
            float leftA, leftR, leftG, leftB;
            float rightA, rightR, rightG, rightB;

            if (fy <= py1) {
                float tLeft = (fy - py0) / dy1;
                float tRight = (fy - py0) / dy2;
                leftX = px0 + (px1 - px0) * tLeft;
                rightX = px0 + (px2 - px0) * tRight;
                leftA = pa0 + (pa1 - pa0) * tLeft;
                leftR = pr0 + (pr1 - pr0) * tLeft;
                leftG = pg0 + (pg1 - pg0) * tLeft;
                leftB = pb0 + (pb1 - pb0) * tLeft;
                rightA = pa0 + (pa2 - pa0) * tRight;
                rightR = pr0 + (pr2 - pr0) * tRight;
                rightG = pg0 + (pg2 - pg0) * tRight;
                rightB = pb0 + (pb2 - pb0) * tRight;
            } else {
                float dy1_2 = py2 - py1;
                float tLeft = (fy - py1) / dy1_2;
                float tRight = (fy - py0) / dy2;
                leftX = px1 + (px2 - px1) * tLeft;
                rightX = px0 + (px2 - px0) * tRight;
                leftA = pa1 + (pa2 - pa1) * tLeft;
                leftR = pr1 + (pr2 - pr1) * tLeft;
                leftG = pg1 + (pg2 - pg1) * tLeft;
                leftB = pb1 + (pb2 - pb1) * tLeft;
                rightA = pa0 + (pa2 - pa0) * tRight;
                rightR = pr0 + (pr2 - pr0) * tRight;
                rightG = pg0 + (pg2 - pg0) * tRight;
                rightB = pb0 + (pb2 - pb0) * tRight;
            }

            if (leftX > rightX) {
                float tmp = leftX; leftX = rightX; rightX = tmp;
                tmp = leftA; leftA = rightA; rightA = tmp;
                tmp = leftR; leftR = rightR; rightR = tmp;
                tmp = leftG; leftG = rightG; rightG = tmp;
                tmp = leftB; leftB = rightB; rightB = tmp;
            }

            int xStart = (int) Math.ceil(leftX);
            int xEnd = (int) Math.floor(rightX);
            if (xStart < clipX1) xStart = clipX1;
            if (xEnd > clipX2) xEnd = clipX2;
            if (xStart > xEnd) continue;

            float span = xEnd - xStart;
            if (span == 0) {
                int midA = (int) ((leftA + rightA) / 2f);
                int midR = (int) ((leftR + rightR) / 2f);
                int midG = (int) ((leftG + rightG) / 2f);
                int midB = (int) ((leftB + rightB) / 2f);
                int color = (clampByte(midA) << 24) | (clampByte(midR) << 16) | (clampByte(midG) << 8) | clampByte(midB);
                writePixelRaw(xStart, y, color);
                continue;
            }

            float stepA = (rightA - leftA) / span;
            float stepR = (rightR - leftR) / span;
            float stepG = (rightG - leftG) / span;
            float stepB = (rightB - leftB) / span;
            float curA = leftA, curR = leftR, curG = leftG, curB = leftB;
            for (int x = xStart; x <= xEnd; x++) {
                int ia = Math.round(curA);
                int ir = Math.round(curR);
                int ig = Math.round(curG);
                int ib = Math.round(curB);
                int color = (clampByte(ia) << 24) | (clampByte(ir) << 16) | (clampByte(ig) << 8) | clampByte(ib);
                writePixelRaw(x, y, color);
                curA += stepA;
                curR += stepR;
                curG += stepG;
                curB += stepB;
            }
        }
    }

    private int clampByte(float val) {
        int v = Math.round(val);
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    private void sortPointsByY(int[] xs, int[] ys) {
        for (int i = 0; i < 2; i++) {
            for (int j = i + 1; j < 3; j++) {
                if (ys[i] > ys[j]) {
                    int tmpY = ys[i]; ys[i] = ys[j]; ys[j] = tmpY;
                    int tmpX = xs[i]; xs[i] = xs[j]; xs[j] = tmpX;
                }
            }
        }
    }

    public void drawRect(int x, int y, int w, int h) {
        drawLine(x, y, x + w, y);
        drawLine(x + w, y, x + w, y + h);
        drawLine(x + w, y + h, x, y + h);
        drawLine(x, y + h, x, y);
    }

    public void fillRect(int x, int y, int w, int h) {
        transform(x, y, tempPoint);
        int sx1 = Math.round(tempPoint[0]), sy1 = Math.round(tempPoint[1]);
        transform(x + w, y + h, tempPoint);
        int sx2 = Math.round(tempPoint[0]), sy2 = Math.round(tempPoint[1]);
        if (sx1 > sx2) { int tmp = sx1; sx1 = sx2; sx2 = tmp; }
        if (sy1 > sy2) { int tmp = sy1; sy1 = sy2; sy2 = tmp; }
        for (int row = sy1; row <= sy2; row++) {
            for (int col = sx1; col <= sx2; col++) {
                writePixelRaw(col, row, currentColor);
            }
        }
    }

    public void drawCircle(int cx, int cy, int radius) {
        if (radius <= 0) return;
        transform(cx, cy, tempPoint);
        int ox = Math.round(tempPoint[0]);
        int oy = Math.round(tempPoint[1]);
        int r = radius;
        int x = 0, y = r;
        int d = 1 - r;
        while (x <= y) {
            drawCirclePoints(ox, oy, x, y);
            x++;
            if (d < 0) {
                d += 2 * x + 1;
            } else {
                y--;
                d += 2 * (x - y) + 1;
            }
        }
    }

    private void drawCirclePoints(int cx, int cy, int x, int y) {
        writePixelRaw(cx + x, cy + y, currentColor);
        writePixelRaw(cx - x, cy + y, currentColor);
        writePixelRaw(cx + x, cy - y, currentColor);
        writePixelRaw(cx - x, cy - y, currentColor);
        writePixelRaw(cx + y, cy + x, currentColor);
        writePixelRaw(cx - y, cy + x, currentColor);
        writePixelRaw(cx + y, cy - x, currentColor);
        writePixelRaw(cx - y, cy - x, currentColor);
    }

    public void fillCircle(int cx, int cy, int radius) {
        if (radius <= 0) return;
        transform(cx, cy, tempPoint);
        int ox = Math.round(tempPoint[0]);
        int oy = Math.round(tempPoint[1]);
        int r = radius;
        for (int y = -r; y <= r; y++) {
            int dy = Math.abs(y);
            int halfWidth = (int) Math.sqrt(r * r - dy * dy);
            int x1 = ox - halfWidth;
            int x2 = ox + halfWidth;
            for (int x = x1; x <= x2; x++) {
                writePixelRaw(x, oy + y, currentColor);
            }
        }
    }

    public void drawEllipse(int cx, int cy, int rx, int ry) {
        if (rx <= 0 || ry <= 0) return;
        transform(cx, cy, tempPoint);
        int ox = Math.round(tempPoint[0]);
        int oy = Math.round(tempPoint[1]);
        int a = rx, b = ry;
        int a2 = a * a, b2 = b * b;
        int twoA2 = 2 * a2, twoB2 = 2 * b2;
        int x = 0, y = b;
        int dx = 0, dy = twoA2 * y;
        int d = (int) (b2 + a2 * (-b + 0.25));
        while (dx < dy) {
            drawEllipsePoints(ox, oy, x, y);
            x++;
            dx += twoB2;
            if (d < 0) {
                d += b2 + dx;
            } else {
                y--;
                dy -= twoA2;
                d += b2 + dx - dy;
            }
        }
        d = (int) (b2 * (x + 0.5) * (x + 0.5) + a2 * (y - 1) * (y - 1) - a2 * b2);
        while (y > 0) {
            drawEllipsePoints(ox, oy, x, y);
            y--;
            dy -= twoA2;
            if (d > 0) {
                d += a2 - dy;
            } else {
                x++;
                dx += twoB2;
                d += a2 - dy + dx;
            }
        }
        drawEllipsePoints(ox, oy, x, y);
    }

    private void drawEllipsePoints(int cx, int cy, int x, int y) {
        writePixelRaw(cx + x, cy + y, currentColor);
        writePixelRaw(cx - x, cy + y, currentColor);
        writePixelRaw(cx + x, cy - y, currentColor);
        writePixelRaw(cx - x, cy - y, currentColor);
    }

    public void fillEllipse(int cx, int cy, int rx, int ry) {
        if (rx <= 0 || ry <= 0) return;
        transform(cx, cy, tempPoint);
        int ox = Math.round(tempPoint[0]);
        int oy = Math.round(tempPoint[1]);
        int a = rx, b = ry;
        int a2 = a * a, b2 = b * b;
        for (int y = -b; y <= b; y++) {
            int dy = Math.abs(y);
            int halfWidth = (int) Math.sqrt(a2 * (1 - (dy * dy) / (float) b2));
            int x1 = ox - halfWidth;
            int x2 = ox + halfWidth;
            for (int x = x1; x <= x2; x++) {
                writePixelRaw(x, oy + y, currentColor);
            }
        }
    }

    // ---------- 多边形填充 ----------
    public void fillPolygon(int[] xPoints, int[] yPoints, int n) {
        if (n < 3) return;
        int[] tx = new int[n];
        int[] ty = new int[n];
        for (int i = 0; i < n; i++) {
            transform(xPoints[i], yPoints[i], tempPoint);
            tx[i] = Math.round(tempPoint[0]);
            ty[i] = Math.round(tempPoint[1]);
        }

        int yMin = ty[0], yMax = ty[0];
        for (int i = 1; i < n; i++) {
            if (ty[i] < yMin) yMin = ty[i];
            if (ty[i] > yMax) yMax = ty[i];
        }
        if (yMin < clipY1) yMin = clipY1;
        if (yMax > clipY2) yMax = clipY2;
        if (yMin > yMax) return;

        int edgeCount = 0;
        float[][] edges = new float[n][4];
        for (int i = 0; i < n; i++) {
            int i2 = (i + 1) % n;
            int y1 = ty[i], y2 = ty[i2];
            if (y1 == y2) continue;
            int x1 = tx[i], x2 = tx[i2];
            if (y1 > y2) {
                int tmpY = y1; y1 = y2; y2 = tmpY;
                int tmpX = x1; x1 = x2; x2 = tmpX;
            }
            float dx = (float)(x2 - x1) / (y2 - y1);
            edges[edgeCount][0] = y1;
            edges[edgeCount][1] = y2;
            edges[edgeCount][2] = x1;
            edges[edgeCount][3] = dx;
            edgeCount++;
        }
        if (edgeCount == 0) return;

        sortEdgesByYMin(edges, edgeCount);

        float[] activeX = new float[edgeCount];
        float[] activeDx = new float[edgeCount];
        int[] activeYMax = new int[edgeCount];
        int activeCount = 0;
        int edgeIndex = 0;

        for (int y = yMin; y <= yMax; y++) {
            while (edgeIndex < edgeCount && edges[edgeIndex][0] == y) {
                activeX[activeCount] = edges[edgeIndex][2];
                activeDx[activeCount] = edges[edgeIndex][3];
                activeYMax[activeCount] = (int) edges[edgeIndex][1];
                activeCount++;
                edgeIndex++;
            }
            for (int i = 0; i < activeCount; i++) {
                if (activeYMax[i] == y) {
                    activeX[i] = activeX[activeCount - 1];
                    activeDx[i] = activeDx[activeCount - 1];
                    activeYMax[i] = activeYMax[activeCount - 1];
                    activeCount--;
                    i--;
                }
            }
            sortActiveEdges(activeX, activeDx, activeYMax, activeCount);
            for (int i = 0; i < activeCount - 1; i += 2) {
                int xLeft = Math.round(activeX[i]);
                int xRight = Math.round(activeX[i + 1]);
                if (xLeft > xRight) { int tmp = xLeft; xLeft = xRight; xRight = tmp; }
                if (xLeft < clipX1) xLeft = clipX1;
                if (xRight > clipX2) xRight = clipX2;
                if (xLeft <= xRight) {
                    for (int x = xLeft; x <= xRight; x++) {
                        writePixelRaw(x, y, currentColor);
                    }
                }
            }
            for (int i = 0; i < activeCount; i++) {
                activeX[i] += activeDx[i];
            }
        }
    }

    private void sortEdgesByYMin(float[][] edges, int count) {
        for (int i = 0; i < count - 1; i++) {
            for (int j = i + 1; j < count; j++) {
                if (edges[i][0] > edges[j][0]) {
                    float[] tmp = edges[i];
                    edges[i] = edges[j];
                    edges[j] = tmp;
                }
            }
        }
    }

    private void sortActiveEdges(float[] x, float[] dx, int[] yMax, int count) {
        for (int i = 0; i < count - 1; i++) {
            for (int j = i + 1; j < count; j++) {
                if (x[i] > x[j]) {
                    float tmpX = x[i]; x[i] = x[j]; x[j] = tmpX;
                    float tmpDx = dx[i]; dx[i] = dx[j]; dx[j] = tmpDx;
                    int tmpY = yMax[i]; yMax[i] = yMax[j]; yMax[j] = tmpY;
                }
            }
        }
    }

    public void drawPolygon(int[] xPoints, int[] yPoints, int n) {
        if (n < 3) return;
        for (int i = 0; i < n; i++) {
            int i2 = (i + 1) % n;
            drawLine(xPoints[i], yPoints[i], xPoints[i2], yPoints[i2]);
        }
    }

    // ---------- 纹理三角形 ----------
    public void drawTexturedTriangle(
            int vx1, int vy1, float u1, float v1,
            int vx2, int vy2, float u2, float v2,
            int vx3, int vy3, float u3, float v3) {

        if (currentTexture == null) {
            fillTriangle(vx1, vy1, vx2, vy2, vx3, vy3);
            return;
        }

        transform(vx1, vy1, tempPoint);
        verts[0] = tempPoint[0]; verts[1] = tempPoint[1]; verts[2] = u1; verts[3] = v1;
        transform(vx2, vy2, tempPoint);
        verts[4] = tempPoint[0]; verts[5] = tempPoint[1]; verts[6] = u2; verts[7] = v2;
        transform(vx3, vy3, tempPoint);
        verts[8] = tempPoint[0]; verts[9] = tempPoint[1]; verts[10] = u3; verts[11] = v3;

        for (int i = 0; i < 2; i++) {
            for (int j = i + 1; j < 3; j++) {
                if (verts[i*4 + 1] > verts[j*4 + 1]) {
                    for (int k = 0; k < 4; k++) {
                        float tmp = verts[i*4 + k];
                        verts[i*4 + k] = verts[j*4 + k];
                        verts[j*4 + k] = tmp;
                    }
                }
            }
        }

        float x0 = verts[0], y0 = verts[1], u0 = verts[2], v0 = verts[3];
        float x1 = verts[4], y1 = verts[5], u1_ = verts[6], v1_ = verts[7];
        float x2 = verts[8], y2 = verts[9], u2_ = verts[10], v2_ = verts[11];

        if (y0 == y2) {
            int y = (int) y0;
            int minX = (int) Math.min(x0, Math.min(x1, x2));
            int maxX = (int) Math.max(x0, Math.max(x1, x2));
            float midU = (u0 + u1_ + u2_) / 3f;
            float midV = (v0 + v1_ + v2_) / 3f;
            int color = sampleTexture(midU, midV);
            for (int x = minX; x <= maxX; x++) {
                writePixelRaw(x, y, color);
            }
            return;
        }

        float yMid = y1;

        if (y0 < yMid) {
            float lenLeft1 = y1 - y0;
            float lenRight1 = y2 - y0;
            float dxLeft1 = (x1 - x0) / lenLeft1;
            float duLeft1 = (u1_ - u0) / lenLeft1;
            float dvLeft1 = (v1_ - v0) / lenLeft1;
            float dxRight1 = (x2 - x0) / lenRight1;
            float duRight1 = (u2_ - u0) / lenRight1;
            float dvRight1 = (v2_ - v0) / lenRight1;

            for (int y = (int) y0; y <= (int) yMid; y++) {
                float t = y - y0;
                float xL = x0 + dxLeft1 * t;
                float uL = u0 + duLeft1 * t;
                float vL = v0 + dvLeft1 * t;
                float xR = x0 + dxRight1 * t;
                float uR = u0 + duRight1 * t;
                float vR = v0 + dvRight1 * t;

                if (xL > xR) {
                    float tmp = xL; xL = xR; xR = tmp;
                    tmp = uL; uL = uR; uR = tmp;
                    tmp = vL; vL = vR; vR = tmp;
                }
                int startX = Math.round(xL);
                int endX = Math.round(xR);
                float span = endX - startX;
                if (span == 0) {
                    writePixelRaw(startX, y, sampleTexture((uL + uR) / 2f, (vL + vR) / 2f));
                    continue;
                }
                float duStep = (uR - uL) / span;
                float dvStep = (vR - vL) / span;
                float u = uL, v = vL;
                for (int x = startX; x <= endX; x++) {
                    writePixelRaw(x, y, sampleTexture(u, v));
                    u += duStep;
                    v += dvStep;
                }
            }
        }

        if (yMid < y2) {
            float lenLeft2 = y2 - y1;
            float dxLeft2 = (x2 - x1) / lenLeft2;
            float duLeft2 = (u2_ - u1_) / lenLeft2;
            float dvLeft2 = (v2_ - v1_) / lenLeft2;

            float lenRight2 = y2 - y0;
            float dxRight2 = (x2 - x0) / lenRight2;
            float duRight2 = (u2_ - u0) / lenRight2;
            float dvRight2 = (v2_ - v0) / lenRight2;

            for (int y = (int) yMid; y <= (int) y2; y++) {
                float tLeft = y - y1;
                float tRight = y - y0;
                float xL = x1 + dxLeft2 * tLeft;
                float uL = u1_ + duLeft2 * tLeft;
                float vL = v1_ + dvLeft2 * tLeft;
                float xR = x0 + dxRight2 * tRight;
                float uR = u0 + duRight2 * tRight;
                float vR = v0 + dvRight2 * tRight;

                if (xL > xR) {
                    float tmp = xL; xL = xR; xR = tmp;
                    tmp = uL; uL = uR; uR = tmp;
                    tmp = vL; vL = vR; vR = tmp;
                }
                int startX = Math.round(xL);
                int endX = Math.round(xR);
                float span = endX - startX;
                if (span == 0) {
                    writePixelRaw(startX, y, sampleTexture((uL + uR) / 2f, (vL + vR) / 2f));
                    continue;
                }
                float duStep = (uR - uL) / span;
                float dvStep = (vR - vL) / span;
                float u = uL, v = vL;
                for (int x = startX; x <= endX; x++) {
                    writePixelRaw(x, y, sampleTexture(u, v));
                    u += duStep;
                    v += dvStep;
                }
            }
        }
    }

    // ---------- 纹理矩形（保留，但文字不再使用） ----------
    public void drawTexturedRect(int x, int y, int w, int h) {
        if (currentTexture == null) return;
        drawTexturedTriangle(x, y, 0, 0,
                             x + w, y, 1, 0,
                             x, y + h, 0, 1);
        drawTexturedTriangle(x + w, y, 1, 0,
                             x + w, y + h, 1, 1,
                             x, y + h, 0, 1);
    }

    // ---------- 直接像素复制位图（高性能） ----------
    public void drawBitmapDirect(Bitmap bitmap, int x, int y) {
        if (bitmap == null || bitmap.isRecycled()) return;
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w <= 0 || h <= 0) return;

        // 变换矩形的四个角，得到屏幕坐标范围
        float[] p1 = new float[2], p2 = new float[2], p3 = new float[2], p4 = new float[2];
        transform(x, y, p1);
        transform(x + w, y, p2);
        transform(x, y + h, p3);
        transform(x + w, y + h, p4);

        // 计算包围盒
        int minX = Math.round(Math.min(Math.min(p1[0], p2[0]), Math.min(p3[0], p4[0])));
        int maxX = Math.round(Math.max(Math.max(p1[0], p2[0]), Math.max(p3[0], p4[0])));
        int minY = Math.round(Math.min(Math.min(p1[1], p2[1]), Math.min(p3[1], p4[1])));
        int maxY = Math.round(Math.max(Math.max(p1[1], p2[1]), Math.max(p3[1], p4[1])));

        // 裁剪到屏幕和视口
        if (minX < clipX1) minX = clipX1;
        if (maxX > clipX2) maxX = clipX2;
        if (minY < clipY1) minY = clipY1;
        if (maxY > clipY2) maxY = clipY2;
        if (minX > maxX || minY > maxY) return;

        // 逐像素复制，并应用混合
        for (int sy = minY; sy <= maxY; sy++) {
            for (int sx = minX; sx <= maxX; sx++) {
                // 计算位图对应的像素坐标（最近邻采样，考虑缩放/旋转可能带来扭曲，但文字通常不变形）
                // 简化：使用仿射逆变换？但为了性能，我们不做逆变换，直接按包围盒映射，但会导致拉伸。
                // 更好的方法：只支持平移和缩放（无旋转），但旋转文字可能少用。为了简单，我们直接使用原始坐标映射。
                // 这里我们取矩形左上角为原点，映射到屏幕。
                // 但若发生旋转，直接映射会变形，我们允许。
                // 计算相对位置
                float rx = (sx - p1[0]) / (p2[0] - p1[0] + 0.0001f);
                float ry = (sy - p1[1]) / (p3[1] - p1[1] + 0.0001f);
                // 限制范围
                if (rx < 0) rx = 0; if (rx > 1) rx = 1;
                if (ry < 0) ry = 0; if (ry > 1) ry = 1;
                int ix = (int) (rx * (w - 1));
                int iy = (int) (ry * (h - 1));
                if (ix < 0 || ix >= w || iy < 0 || iy >= h) continue;
                int color = bitmap.getPixel(ix, iy);
                // 如果有透明度，使用混合
                writePixelRaw(sx, sy, color);
            }
        }
    }

    // ---------- 文字渲染（使用缓存 + 直接位图复制） ----------
    public void setTextPaint(Paint paint) {
        if (paint != null) {
            textPaint = paint;
        }
    }

    public void drawText(String text, int x, int y) {
        if (text == null || text.isEmpty()) return;

        int color = currentColor;
        // 检查缓存
        if (cachedText != null && cachedText.equals(text) &&
            cachedTextBitmap != null && !cachedTextBitmap.isRecycled() &&
            cachedTextColor == color) {
            drawBitmapDirect(cachedTextBitmap, x, y);
            return;
        }

        // 生成文字位图
        Rect bounds = new Rect();
        textPaint.getTextBounds(text, 0, text.length(), bounds);
        int w = bounds.width();
        int h = bounds.height();
        if (w <= 0 || h <= 0) {
            w = (int) textPaint.measureText(text);
            if (w <= 0) w = 1;
            h = (int) textPaint.getTextSize();
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
        Paint paint = new Paint(textPaint);
        paint.setColor(color);
        float baseline = -bounds.top;
        canvas.drawText(text, -bounds.left, baseline, paint);

        // 更新缓存
        if (cachedTextBitmap != null && !cachedTextBitmap.isRecycled()) {
            cachedTextBitmap.recycle();
        }
        cachedText = text;
        cachedTextBitmap = bitmap;
        cachedTextColor = color;

        drawBitmapDirect(bitmap, x, y);
    }

    // ---------- Wu 抗锯齿线条 ----------
    private void wuLine(float x0, float y0, float x1, float y1) {
        boolean steep = Math.abs(y1 - y0) > Math.abs(x1 - x0);
        if (steep) {
            float tmp = x0; x0 = y0; y0 = tmp;
            tmp = x1; x1 = y1; y1 = tmp;
        }
        if (x0 > x1) {
            float tmp = x0; x0 = x1; x1 = tmp;
            tmp = y0; y0 = y1; y1 = tmp;
        }

        float dx = x1 - x0;
        float dy = y1 - y0;
        float gradient = (dx == 0) ? 1f : dy / dx;

        float xEnd = round(x0);
        float yEnd = y0 + gradient * (xEnd - x0);
        float xGap = rfpart(x0 + 0.5f);
        int xPixel1 = (int) xEnd;
        int yPixel1 = (int) yEnd;
        if (steep) {
            plot(yPixel1, xPixel1, rfpart(yEnd) * xGap);
            plot(yPixel1 + 1, xPixel1, fpart(yEnd) * xGap);
        } else {
            plot(xPixel1, yPixel1, rfpart(yEnd) * xGap);
            plot(xPixel1, yPixel1 + 1, fpart(yEnd) * xGap);
        }
        float intery = yEnd + gradient;

        xEnd = round(x1);
        yEnd = y1 + gradient * (xEnd - x1);
        xGap = fpart(x1 + 0.5f);
        int xPixel2 = (int) xEnd;
        int yPixel2 = (int) yEnd;
        if (steep) {
            plot(yPixel2, xPixel2, rfpart(yEnd) * xGap);
            plot(yPixel2 + 1, xPixel2, fpart(yEnd) * xGap);
        } else {
            plot(xPixel2, yPixel2, rfpart(yEnd) * xGap);
            plot(xPixel2, yPixel2 + 1, fpart(yEnd) * xGap);
        }

        if (steep) {
            for (int x = (int) (xPixel1 + 1); x <= (int) (xPixel2 - 1); x++) {
                int y = (int) intery;
                plot(y, x, rfpart(intery));
                plot(y + 1, x, fpart(intery));
                intery += gradient;
            }
        } else {
            for (int x = (int) (xPixel1 + 1); x <= (int) (xPixel2 - 1); x++) {
                int y = (int) intery;
                plot(x, y, rfpart(intery));
                plot(x, y + 1, fpart(intery));
                intery += gradient;
            }
        }
    }

    private void plot(int x, int y, float alpha) {
        if (alpha <= 0) return;
        if (alpha > 1f) alpha = 1f;
        int a = (currentColor >> 24) & 0xFF;
        int r = (currentColor >> 16) & 0xFF;
        int g = (currentColor >> 8) & 0xFF;
        int b = currentColor & 0xFF;
        int newAlpha = (int) (a * alpha);
        if (newAlpha > 255) newAlpha = 255;
        int color = (newAlpha << 24) | (r << 16) | (g << 8) | b;
        writePixelRaw(x, y, color);
    }

    private float fpart(float x) { return x - (float) Math.floor(x); }
    private float rfpart(float x) { return 1f - fpart(x); }
    private float round(float x) { return (float) Math.floor(x + 0.5f); }

    // ---------- 输出 Bitmap ----------
    public Bitmap toBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(framebuffer, 0, width, 0, 0, width, height);
        return bitmap;
    }

    public int[] getFramebuffer() { return framebuffer; }
}
