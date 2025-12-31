package com.Zhaang1.Twiniverse;

import android.content.Context;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewParent;
import androidx.appcompat.widget.AppCompatImageView;

public class ZoomImageView extends AppCompatImageView {

    private Matrix matrix = new Matrix();
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private float saveScale = 1f;
    private float minScale = 1f;
    private float maxScale = 3f;

    private OnClickListener onClickListener;

    public ZoomImageView(Context context) {
        super(context);
        init(context);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    @Override
    public void setOnClickListener(OnClickListener l) {
        this.onClickListener = l;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 【核心修复】
        // 当检测到屏幕上有超过一个触控点（即双指操作）时，
        // 请求父控件（ViewPager2）不要拦截触摸事件，从而防止触发翻页。
        if (event.getPointerCount() > 1) {
            ViewParent parent = getParent();
            if (parent != null) {
                parent.requestDisallowInterceptTouchEvent(true);
            }
        }

        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            float origScale = saveScale;
            saveScale *= scaleFactor;

            if (saveScale > maxScale) {
                saveScale = maxScale;
                scaleFactor = maxScale / origScale;
            } else if (saveScale < minScale) {
                saveScale = minScale;
                scaleFactor = minScale / origScale;
            }

            if (origScale * scaleFactor <= minScale) {
                matrix.postScale(scaleFactor, scaleFactor, getWidth() / 2f, getHeight() / 2f);
            } else {
                matrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
            }
            setImageMatrix(matrix);
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (onClickListener != null) onClickListener.onClick(ZoomImageView.this);
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (saveScale > minScale) {
                saveScale = minScale;
                matrix.reset();
                fitCenter();
            } else {
                saveScale = maxScale;
                matrix.postScale(maxScale, maxScale, e.getX(), e.getY());
            }
            setImageMatrix(matrix);
            return true;
        }
    }

    public void resetZoom() {
        saveScale = 1f;
        matrix.reset();
        fitCenter();
        setImageMatrix(matrix);
    }

    private void fitCenter() {
        if (getDrawable() == null) return;
        int dWidth = getDrawable().getIntrinsicWidth();
        int dHeight = getDrawable().getIntrinsicHeight();
        int vWidth = getWidth();
        int vHeight = getHeight();

        if (dWidth <= 0 || dHeight <= 0 || vWidth <= 0 || vHeight <= 0) return;

        float scale;
        float dx = 0, dy = 0;

        if (dWidth * vHeight > vWidth * dHeight) {
            scale = (float) vWidth / (float) dWidth;
            dy = (vHeight - dHeight * scale) * 0.5f;
        } else {
            scale = (float) vHeight / (float) dHeight;
            dx = (vWidth - dWidth * scale) * 0.5f;
        }

        matrix.setScale(scale, scale);
        matrix.postTranslate(dx, dy);
        setImageMatrix(matrix);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            resetZoom();
        }
    }
}