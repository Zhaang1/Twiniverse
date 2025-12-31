package com.Zhaang1.Twiniverse;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.ColorDrawable;
import android.media.ExifInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageCameraActivity extends AppCompatActivity {

    private static final int MAX_IMAGES = 20;
    private static final int STATE_THRESHOLD = 5;

    // Camera
    private PreviewView viewFinder;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;

    // Data
    private List<File> capturedImages = new ArrayList<>();
    private List<View> thumbnailViews = new ArrayList<>(); // 对应每个图片的 View (FrameLayout)

    // UI
    private FrameLayout containerThumbnails;
    private ImageButton btnBack, btnCapture, btnUpload;

    // Dimensions for thumbnails
    private int screenWidth;
    private int thumbWidthA, thumbHeightA;
    private int thumbWidthB, thumbHeightB;

    // Managers
    private CommunicationManager communicationManager;
    private String currentUsername = "guest";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_camera);

        // 获取屏幕宽度，计算缩略图尺寸
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        screenWidth = displayMetrics.widthPixels;

        // 状态 A: 1行5个 (宽=屏幕/5, 高=宽*4/3)
        thumbWidthA = screenWidth / 5;
        thumbHeightA = thumbWidthA * 4 / 3;

        // 状态 B: 尺寸减半
        thumbWidthB = thumbWidthA / 2;
        thumbHeightB = thumbHeightA / 2;

        communicationManager = new CommunicationManager();
        String user = getIntent().getStringExtra("USERNAME");
        if(user != null) currentUsername = user;

        initViews();
        startCamera();

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void initViews() {
        viewFinder = findViewById(R.id.viewFinder);
        containerThumbnails = findViewById(R.id.container_thumbnails);
        btnBack = findViewById(R.id.btn_back);
        btnCapture = findViewById(R.id.btn_capture);
        btnUpload = findViewById(R.id.btn_upload);

        btnCapture.setOnClickListener(v -> takePhoto());
        btnBack.setOnClickListener(v -> showExitDialog());
        btnUpload.setOnClickListener(v -> showUploadDialog());
    }

    private void startCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 10);
            return;
        }

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        if (capturedImages.size() >= MAX_IMAGES) {
            showCustomToast("图片数量已满，无法继续拍摄");
            return;
        }

        File photoFile = new File(getExternalFilesDir(null),
                System.currentTimeMillis() + ".jpg");

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        capturedImages.add(photoFile);
                        addThumbnailView(photoFile);
                        refreshThumbnailLayout(true);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        showCustomToast("拍摄失败: " + exception.getMessage());
                    }
                });
    }

    /**
     * 创建单个缩略图 View
     */
    private void addThumbnailView(File file) {
        FrameLayout itemLayout = new FrameLayout(this);

        ImageView imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

        // 修复问题2：加载并自动旋转图片，同时进行采样以防止 OOM
        Bitmap bitmap = loadRotatedThumbnail(file, thumbWidthA, thumbHeightA);
        imageView.setImageBitmap(bitmap);

        View overlay = new View(this);
        overlay.setBackgroundColor(Color.parseColor("#80FF0000")); // 半透明红
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        overlay.setLayoutParams(overlayParams);

        itemLayout.addView(imageView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        itemLayout.addView(overlay);

        // 修复问题1：修复长按删除逻辑
        setupDeleteInteraction(itemLayout, overlay, file);

        thumbnailViews.add(itemLayout);
        containerThumbnails.addView(itemLayout);
    }

    /**
     * 读取图片并根据 EXIF 信息旋转，且优化内存占用
     */
    private Bitmap loadRotatedThumbnail(File file, int reqWidth, int reqHeight) {
        try {
            // 1. 获取图片尺寸，计算缩放比例 (inSampleSize)
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;

            // 2. 解码图片 (低内存版)
            Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bmp == null) return null;

            // 3. 读取 EXIF 旋转信息
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            int rotationAngle = 0;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: rotationAngle = 90; break;
                case ExifInterface.ORIENTATION_ROTATE_180: rotationAngle = 180; break;
                case ExifInterface.ORIENTATION_ROTATE_270: rotationAngle = 270; break;
            }

            // 4. 如果需要旋转，创建新的旋转后 Bitmap
            if (rotationAngle != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotationAngle);
                Bitmap rotatedBmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
                if (rotatedBmp != bmp) {
                    bmp.recycle(); // 回收旧图
                }
                return rotatedBmp;
            }
            return bmp;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /**
     * 处理长按删除逻辑 (修复版)
     */
    @SuppressLint("ClickableViewAccessibility")
    private void setupDeleteInteraction(View itemView, View overlay, File file) {
        itemView.setOnTouchListener(new View.OnTouchListener() {
            private ValueAnimator deleteAnimator;
            // 标志位：动画是否被用户取消（如手指抬起）
            private boolean isAnimationCancelled = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int width = v.getWidth();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isAnimationCancelled = false; // 重置标志

                        deleteAnimator = ValueAnimator.ofInt(0, width);
                        deleteAnimator.setDuration(1000); // 1秒长按
                        deleteAnimator.addUpdateListener(animation -> {
                            int val = (int) animation.getAnimatedValue();
                            ViewGroup.LayoutParams lp = overlay.getLayoutParams();
                            lp.width = val;
                            overlay.setLayoutParams(lp);
                        });

                        deleteAnimator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                // 只有当动画自然结束（未被取消）时，才执行删除
                                if (!isAnimationCancelled) {
                                    performDelete(v, file);
                                }
                            }

                            @Override
                            public void onAnimationCancel(Animator animation) {
                                isAnimationCancelled = true; // 标记被取消
                            }
                        });
                        deleteAnimator.start();
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (deleteAnimator != null && deleteAnimator.isRunning()) {
                            // 用户在1秒内抬起手指，取消动画
                            deleteAnimator.cancel();
                            // 立即重置遮罩宽度
                            ViewGroup.LayoutParams lp = overlay.getLayoutParams();
                            lp.width = 0;
                            overlay.setLayoutParams(lp);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void performDelete(View view, File file) {
        view.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(200)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        containerThumbnails.removeView(view);
                        thumbnailViews.remove(view);
                        capturedImages.remove(file);
                        if (file.exists()) file.delete();
                        refreshThumbnailLayout(true);
                    }
                }).start();
    }

    private void refreshThumbnailLayout(boolean animate) {
        int count = thumbnailViews.size();
        boolean isStateB = count > STATE_THRESHOLD;

        int targetW = isStateB ? thumbWidthB : thumbWidthA;
        int targetH = isStateB ? thumbHeightB : thumbHeightA;

        for (int i = 0; i < count; i++) {
            View v = thumbnailViews.get(i);

            float targetX;
            float targetY;

            if (isStateB) {
                int col = i % 10;
                int row = i / 10;
                targetX = col * targetW;
                targetY = row * targetH;
            } else {
                targetX = i * targetW;
                targetY = 0;
            }

            if (animate) {
                v.animate()
                        .translationX(targetX)
                        .translationY(targetY)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(300)
                        .setUpdateListener(animation -> {
                            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
                            if (lp.width != targetW || lp.height != targetH) {
                                lp.width = targetW;
                                lp.height = targetH;
                                v.setLayoutParams(lp);
                            }
                        })
                        .start();
            } else {
                v.setTranslationX(targetX);
                v.setTranslationY(targetY);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
                lp.width = targetW;
                lp.height = targetH;
                v.setLayoutParams(lp);
            }
        }
    }

    // --- 弹窗与逻辑 ---

    private void showExitDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_camera_action);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvTitle = dialog.findViewById(R.id.tv_dialog_title);
        TextView btnCancel = dialog.findViewById(R.id.btn_dialog_cancel);
        TextView btnConfirm = dialog.findViewById(R.id.btn_dialog_confirm);

        tvTitle.setText("退出拍摄？");

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });

        dialog.show();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        showExitDialog();
    }

    private void showUploadDialog() {
        if (capturedImages.isEmpty()) {
            showCustomToast("请先拍摄图片");
            return;
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_camera_action);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.setCancelable(false);

        TextView tvTitle = dialog.findViewById(R.id.tv_dialog_title);
        TextView tvDots = dialog.findViewById(R.id.tv_dialog_dots);
        View divider = dialog.findViewById(R.id.view_divider_horizontal);
        LinearLayout llButtons = dialog.findViewById(R.id.ll_dialog_buttons);
        TextView btnCancel = dialog.findViewById(R.id.btn_dialog_cancel);
        TextView btnConfirm = dialog.findViewById(R.id.btn_dialog_confirm);

        tvTitle.setText("生成三维模型？");

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            tvTitle.setText("请稍候");
            divider.setVisibility(View.GONE);
            llButtons.setVisibility(View.GONE);

            final Handler handler = new Handler(Looper.getMainLooper());
            final int[] dotCount = {0};
            Runnable dotRunnable = new Runnable() {
                @Override
                public void run() {
                    dotCount[0] = (dotCount[0] % 4) + 1;
                    StringBuilder sb = new StringBuilder();
                    for(int i=0; i<dotCount[0]; i++) sb.append(".");
                    tvDots.setText(sb.toString());
                    handler.postDelayed(this, 500);
                }
            };
            handler.post(dotRunnable);

            new Thread(() -> {
                try {
                    List<File> compressedFiles = compressImages(capturedImages);

                    communicationManager.setCurrentUsername(currentUsername);
                    File glbFile = communicationManager.genByImage(ImageCameraActivity.this, compressedFiles);

                    runOnUiThread(() -> {
                        handler.removeCallbacks(dotRunnable);
                        dialog.dismiss();

                        if (glbFile != null && glbFile.exists()) {
                            String oldName = glbFile.getName();
                            String hash = GLBFileManager.getFileNameInHash(oldName);
                            GLBFileManager.renameFile(ImageCameraActivity.this, glbFile, "NewGLBFile", currentUsername, hash);
                            showCustomToast("生成成功");
                        }

                        finish();
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        handler.removeCallbacks(dotRunnable);
                        dialog.dismiss();
                        String errorMsg = e.getMessage();
                        if (errorMsg == null) {
                            errorMsg = "网络连接中断 (EOF)";
                        }
                        showCustomToast("上传失败: " + errorMsg);
                    });
                }
            }).start();
        });

        dialog.show();
    }

    private List<File> compressImages(List<File> originals) {
        List<File> compressed = new ArrayList<>();
        for (File file : originals) {
            File compFile = file;
            long size = file.length();
            if (size > 1024 * 1024) {
                try {
                    Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath());
                    File temp = new File(getExternalFilesDir(null), "compressed_" + file.getName());
                    int quality = 90;
                    do {
                        FileOutputStream fos = new FileOutputStream(temp);
                        bmp.compress(Bitmap.CompressFormat.JPEG, quality, fos);
                        fos.close();
                        quality -= 10;
                    } while (temp.length() > 1024 * 1024 && quality > 10);
                    compFile = temp;
                    bmp.recycle();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            compressed.add(compFile);
        }
        return compressed;
    }

    private void showCustomToast(String message) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextColor(Color.WHITE);
        tv.setBackgroundResource(R.drawable.bg_dialog_rounded);
        tv.setPadding(30, 20, 30, 20);
        tv.setGravity(Gravity.CENTER);
        toast.setView(tv);
        toast.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}