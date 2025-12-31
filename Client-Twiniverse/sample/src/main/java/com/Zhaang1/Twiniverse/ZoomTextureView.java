package com.Zhaang1.Twiniverse;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewParent;

import java.io.IOException;

public class ZoomTextureView extends TextureView implements TextureView.SurfaceTextureListener {

    private MediaPlayer mediaPlayer;
    private Matrix matrix = new Matrix();

    // 手势相关
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private float saveScale = 1f;
    private float minScale = 1f;
    private float maxScale = 3f;

    private String videoPath;
    private OnClickListener onClickListener;
    private OnCompletionListener onCompletionListener;

    public interface OnCompletionListener {
        void onCompletion();
    }

    public ZoomTextureView(Context context) {
        this(context, null);
    }

    public ZoomTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSurfaceTextureListener(this);
        initGestures(context);
    }

    private void initGestures(Context context) {
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    public void setVideoPath(String path) {
        this.videoPath = path;
        // 如果 Surface 已经准备好，直接打开；否则等待回调
        if (isAvailable()) {
            openVideo();
        }
    }

    private void openVideo() {
        if (videoPath == null || getSurfaceTexture() == null) return;
        try {
            if (mediaPlayer != null) {
                mediaPlayer.reset();
            } else {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setOnCompletionListener(mp -> {
                    if (onCompletionListener != null) onCompletionListener.onCompletion();
                });
                mediaPlayer.setOnVideoSizeChangedListener((mp, width, height) -> fitCenter(width, height));
            }
            mediaPlayer.setDataSource(videoPath);
            mediaPlayer.setSurface(new Surface(getSurfaceTexture()));

            // 【核心修复】在准备完成后，强制渲染第一帧
            mediaPlayer.setOnPreparedListener(mp -> {
                fitCenter(mp.getVideoWidth(), mp.getVideoHeight());
                // seekTo(1) 会触发解码器渲染第1毫秒的帧到 Surface 上
                // 从而在不调用 start() 的情况下显示视频封面
                mp.seekTo(1);
            });

            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- 播放控制 ---

    public void start() {
        if (mediaPlayer != null) mediaPlayer.start();
    }

    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) mediaPlayer.pause();
    }

    public void seekTo(int msec) {
        if (mediaPlayer != null) mediaPlayer.seekTo(msec);
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public int getCurrentPosition() {
        return mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
    }

    public int getDuration() {
        return mediaPlayer != null ? mediaPlayer.getDuration() : 0;
    }

    public void stopAndRelease() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    public void setOnCompletionListener(OnCompletionListener listener) {
        this.onCompletionListener = listener;
    }

    @Override
    public void setOnClickListener(OnClickListener l) {
        this.onClickListener = l;
    }

    // --- TextureView Listener ---

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        openVideo();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // 大小改变时可能需要重新适配，但在 ViewPager 中通常由 LayoutPass 触发
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        stopAndRelease();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) { }

    // --- 手势与缩放逻辑 ---

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 双指操作时请求父控件不拦截事件
        if (event.getPointerCount() > 1) {
            ViewParent parent = getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
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
            setTransform(matrix);
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (onClickListener != null) onClickListener.onClick(ZoomTextureView.this);
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (saveScale > minScale) {
                saveScale = minScale;
                matrix.reset();
                if (mediaPlayer != null) fitCenter(mediaPlayer.getVideoWidth(), mediaPlayer.getVideoHeight());
            } else {
                saveScale = maxScale;
                matrix.postScale(maxScale, maxScale, e.getX(), e.getY());
            }
            setTransform(matrix);
            return true;
        }
    }

    // 适配屏幕
    private void fitCenter(int videoWidth, int videoHeight) {
        if (videoWidth == 0 || videoHeight == 0) return;

        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (viewWidth == 0 || viewHeight == 0) return;

        float pivotX = viewWidth / 2f;
        float pivotY = viewHeight / 2f;

        float scaleFit;
        if ((float) videoWidth / videoHeight > (float) viewWidth / viewHeight) {
            // 视频宽高比 > 视图宽高比（视频更宽），以宽为基准匹配
            scaleFit = (float) viewWidth / videoWidth;
        } else {
            // 视频宽高比 <= 视图宽高比（视频更高），以高为基准匹配
            scaleFit = (float) viewHeight / videoHeight;
        }

        // 计算 TextureView 需要的缩放值来“反拉伸”并保持比例
        float scaleX_tex = (videoWidth * scaleFit) / viewWidth;
        float scaleY_tex = (videoHeight * scaleFit) / viewHeight;

        matrix.reset();
        matrix.setScale(scaleX_tex, scaleY_tex, pivotX, pivotY);
        setTransform(matrix);

        saveScale = 1f;
    }
}