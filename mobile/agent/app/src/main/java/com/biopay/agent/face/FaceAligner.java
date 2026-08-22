package com.biopay.agent.face;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;

/**
 * Crops and aligns a captured face to the fixed 112x112 frame the prototype embedder expects,
 * using a 2-point (eye-to-eye) similarity transform -- rotate/scale/translate the source image so
 * both eyes land on fixed canonical positions, then crop to {@link #OUTPUT_SIZE}.
 *
 * <p>The canonical eye coordinates below are a widely-used geometric convention for 112x112 face
 * crops (not model-specific IP -- just a target layout), used here as a best-effort match: the
 * embedder's own model card does not publish the exact alignment its training pipeline used, so
 * this is an assumption, not a confirmed spec. See {@code assets/face/README.md}.
 *
 * <p>The transform math ({@link #similarityTransform}) is pure and JVM-testable, matching the
 * pattern in {@link FaceDetectionHeuristics}; only {@link #align} touches Android graphics types
 * and so isn't unit-tested the same way ML Kit's own detector isn't.
 */
public final class FaceAligner {
    private FaceAligner() { }

    public static final int OUTPUT_SIZE = 112;

    private static final double CANONICAL_LEFT_EYE_X = 38.2946;
    private static final double CANONICAL_LEFT_EYE_Y = 51.6963;
    private static final double CANONICAL_RIGHT_EYE_X = 73.5318;
    private static final double CANONICAL_RIGHT_EYE_Y = 51.5014;

    /** Similarity-transform coefficients for {@code x' = a*x - b*y + tx, y' = b*x + a*y + ty}. */
    public static final class Transform {
        public final double a;
        public final double b;
        public final double tx;
        public final double ty;

        Transform(double a, double b, double tx, double ty) {
            this.a = a;
            this.b = b;
            this.tx = tx;
            this.ty = ty;
        }
    }

    /**
     * Closed-form best-fit similarity transform (uniform scale + rotation + translation, no shear)
     * mapping the source eye positions onto the destination eye positions.
     */
    public static Transform similarityTransform(double srcLeftEyeX, double srcLeftEyeY,
            double srcRightEyeX, double srcRightEyeY, double dstLeftEyeX, double dstLeftEyeY,
            double dstRightEyeX, double dstRightEyeY) {
        double srcDx = srcRightEyeX - srcLeftEyeX;
        double srcDy = srcRightEyeY - srcLeftEyeY;
        double dstDx = dstRightEyeX - dstLeftEyeX;
        double dstDy = dstRightEyeY - dstLeftEyeY;

        double srcLenSq = srcDx * srcDx + srcDy * srcDy;
        if (srcLenSq < 1e-9) {
            throw new IllegalArgumentException("Source eye positions must not coincide");
        }

        double a = (dstDx * srcDx + dstDy * srcDy) / srcLenSq;
        double b = (dstDy * srcDx - dstDx * srcDy) / srcLenSq;
        double tx = dstLeftEyeX - (a * srcLeftEyeX - b * srcLeftEyeY);
        double ty = dstLeftEyeY - (b * srcLeftEyeX + a * srcLeftEyeY);
        return new Transform(a, b, tx, ty);
    }

    /** Aligns and crops {@code source} to a fresh {@link #OUTPUT_SIZE}x{@link #OUTPUT_SIZE} bitmap. */
    public static Bitmap align(Bitmap source, PointF leftEye, PointF rightEye) {
        Transform t = similarityTransform(leftEye.x, leftEye.y, rightEye.x, rightEye.y,
                CANONICAL_LEFT_EYE_X, CANONICAL_LEFT_EYE_Y, CANONICAL_RIGHT_EYE_X, CANONICAL_RIGHT_EYE_Y);

        Matrix matrix = new Matrix();
        matrix.setValues(new float[]{
                (float) t.a, (float) -t.b, (float) t.tx,
                (float) t.b, (float) t.a, (float) t.ty,
                0f, 0f, 1f
        });

        Bitmap output = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawBitmap(source, matrix, new Paint(Paint.FILTER_BITMAP_FLAG));
        return output;
    }
}
