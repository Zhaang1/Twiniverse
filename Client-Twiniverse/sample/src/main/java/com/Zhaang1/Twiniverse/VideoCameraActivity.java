package com.Zhaang1.Twiniverse;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoCameraActivity extends AppCompatActivity {

    private static final String TAG = "VideoCameraActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 20;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };

    // UI
    private PreviewView viewFinder;
    private ImageButton btnBack, btnCapture, btnUpload;
    private TextView tvRecordingTimer;

    // CameraX
    private VideoCapture<Recorder> videoCapture;
    private Recording currentRecording;
    private ExecutorService cameraExecutor;

    // State & Data
    private enum RecordState { IDLE, RECORDING, RECORDED }
    private RecordState currentState = RecordState.IDLE;
    private File videoFile; // 只保存一份视频文件

    // Managers
    private CommunicationManager communicationManager;
    private String currentUsername = "guest";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_camera);

        communicationManager = new CommunicationManager();
        String user = getIntent().getStringExtra("USERNAME");
        if (user != null) currentUsername = user;

        initViews();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void initViews() {
        viewFinder = findViewById(R.id.viewFinder);
        btnBack = findViewById(R.id.btn_back);
        btnCapture = findViewById(R.id.btn_capture);
        btnUpload = findViewById(R.id.btn_upload);
        tvRecordingTimer = findViewById(R.id.tv_recording_timer);

        btnBack.setOnClickListener(v -> showExitDialog());
        btnUpload.setOnClickListener(v -> showUploadDialog());

        btnCapture.setOnClickListener(v -> onCaptureClick());
    }

    private void onCaptureClick() {
        switch (currentState) {
            case IDLE:
                startRecording();
                break;
            case RECORDING:
                stopRecording();
                break;
            case RECORDED:
                // 再次点击：删除旧视频，重新开始录制
                if (videoFile != null && videoFile.exists()) {
                    videoFile.delete();
                    videoFile = null;
                }
                startRecording();
                break;
        }
    }

    private void updateCaptureButtonUI() {
        runOnUiThread(() -> {
            switch (currentState) {
                case IDLE:
                    btnCapture.setBackgroundResource(R.drawable.btn_shutter_bg);
                    tvRecordingTimer.setVisibility(View.INVISIBLE);
                    break;
                case RECORDING:
                    btnCapture.setBackgroundResource(R.drawable.btn_shutter_recording); // 红方块
                    tvRecordingTimer.setVisibility(View.VISIBLE);
                    break;
                case RECORDED:
                    btnCapture.setBackgroundResource(R.drawable.btn_shutter_recorded); // 绿圆
                    tvRecordingTimer.setVisibility(View.INVISIBLE);
                    break;
            }
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                // 【修复】配置录制器，增加回退策略以兼容模拟器
                // 优先尝试 SD 画质，如果不支持，则选择比 SD 高或低的任意可用画质
                QualitySelector qualitySelector = QualitySelector.from(
                        Quality.SD,
                        FallbackStrategy.higherQualityOrLowerThan(Quality.SD));

                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(qualitySelector)
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture);

            } catch (Exception e) {
                Log.e(TAG, "Use case binding failed", e);
                // 避免崩溃，给用户提示
                runOnUiThread(() -> showCustomToast("相机初始化失败: " + e.getMessage()));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint("MissingPermission")
    private void startRecording() {
        if (videoCapture == null) {
            showCustomToast("相机未准备好");
            return;
        }

        // 创建临时文件
        videoFile = new File(getExternalFilesDir(null), "record_" + System.currentTimeMillis() + ".mp4");
        FileOutputOptions outputOptions = new FileOutputOptions.Builder(videoFile).build();

        // 开始录制
        currentRecording = videoCapture.getOutput()
                .prepareRecording(this, outputOptions)
                .withAudioEnabled() // 需要权限
                .start(ContextCompat.getMainExecutor(this), recordEvent -> {
                    if (recordEvent instanceof VideoRecordEvent.Start) {
                        currentState = RecordState.RECORDING;
                        updateCaptureButtonUI();
                    } else if (recordEvent instanceof VideoRecordEvent.Finalize) {
                        VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) recordEvent;
                        if (!finalizeEvent.hasError()) {
                            currentState = RecordState.RECORDED;
                            updateCaptureButtonUI();
                            showCustomToast("录制完成");
                        } else {
                            if (currentRecording != null) currentRecording.close();
                            currentRecording = null;
                            currentState = RecordState.IDLE;
                            updateCaptureButtonUI();
                            showCustomToast("录制出错: " + finalizeEvent.getError());
                            Log.e(TAG, "Video record error: " + finalizeEvent.getError());
                        }
                    } else if (recordEvent instanceof VideoRecordEvent.Status) {
                        // 更新计时器
                        long durationNanos = ((VideoRecordEvent.Status) recordEvent).getRecordingStats().getRecordedDurationNanos();
                        long seconds = durationNanos / 1_000_000_000;
                        updateTimerUI(seconds);
                    }
                });
    }

    private void updateTimerUI(long seconds) {
        long min = seconds / 60;
        long sec = seconds % 60;
        tvRecordingTimer.setText(String.format("%02d:%02d", min, sec));
    }

    private void stopRecording() {
        if (currentRecording != null) {
            currentRecording.stop();
            currentRecording = null;
        }
    }

    // --- 弹窗与上传逻辑 ---

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
            // 清理未上传的视频
            if (videoFile != null && videoFile.exists()) {
                videoFile.delete();
            }
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
        if (currentState != RecordState.RECORDED || videoFile == null || !videoFile.exists()) {
            showCustomToast("请先录制视频");
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
                    communicationManager.setCurrentUsername(currentUsername);
                    // 直接上传 videoFile
                    File glbFile = communicationManager.genByVideo(VideoCameraActivity.this, videoFile);

                    runOnUiThread(() -> {
                        handler.removeCallbacks(dotRunnable);
                        dialog.dismiss();

                        if (glbFile != null && glbFile.exists()) {
                            String oldName = glbFile.getName();
                            String hash = GLBFileManager.getFileNameInHash(oldName);
                            GLBFileManager.renameFile(VideoCameraActivity.this, glbFile, "NewGLBFile", currentUsername, hash);
                            showCustomToast("生成成功");
                        }

                        // 删除源视频以释放空间
                        if (videoFile != null && videoFile.exists()) {
                            videoFile.delete();
                        }

                        finish();
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        handler.removeCallbacks(dotRunnable);
                        dialog.dismiss();
                        String errorMsg = e.getMessage();
                        if (errorMsg == null) errorMsg = "网络连接中断 (EOF)";
                        showCustomToast("上传失败: " + errorMsg);
                    });
                }
            }).start();
        });

        dialog.show();
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

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                showCustomToast("未获得相机或录音权限，无法使用");
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}