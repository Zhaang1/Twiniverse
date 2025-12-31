package com.Zhaang1.Twiniverse;

import android.Manifest;
import android.app.Dialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoFileActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_READ_STORAGE = 102;

    private ImageButton btnBack, btnUpload;
    private RecyclerView recyclerView;
    private FrameLayout containerPreview;
    private ViewPager2 previewPager;

    private List<VideoItem> allVideos = new ArrayList<>();
    private VideoItem selectedVideo = null; // 单选

    private VideoGridAdapter gridAdapter;
    private VideoPreviewAdapter previewAdapter;

    private CommunicationManager communicationManager;
    private String currentUsername = "guest";
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_file);

        communicationManager = new CommunicationManager();
        String user = getIntent().getStringExtra("USERNAME");
        if (user != null) currentUsername = user;

        executorService = Executors.newSingleThreadExecutor();

        initViews();
        checkPermissionAndLoadVideos();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_back);
        btnUpload = findViewById(R.id.btn_upload);
        recyclerView = findViewById(R.id.recycler_view);
        containerPreview = findViewById(R.id.container_preview);
        previewPager = findViewById(R.id.preview_pager);

        recyclerView.setLayoutManager(new GridLayoutManager(this, 5));
        gridAdapter = new VideoGridAdapter(this);
        recyclerView.setAdapter(gridAdapter);

        previewAdapter = new VideoPreviewAdapter();
        previewPager.setAdapter(previewAdapter);
        previewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);

        // 页面切换时停止播放
        previewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                // 这里通知 Adapter 停止旧的播放，或者 Adapter 内部自行处理
                // 由于 RecyclerView 的复用机制，最好在 Adapter 里处理视图的 reset
                previewAdapter.notifyItemChanged(position); // 简单粗暴重置 UI 状态
                // 停止其他所有的播放 (稍微复杂，简单做法是在 Adapter 的 onViewDetachedFromWindow 中停止)
            }
        });

        btnBack.setOnClickListener(v -> showExitDialog());
        btnUpload.setOnClickListener(v -> showUploadDialog());
        containerPreview.setOnClickListener(v -> closePreview());
    }

    private void checkPermissionAndLoadVideos() {
        String permission;
        if (Build.VERSION.SDK_INT >= 33) {
            permission = Manifest.permission.READ_MEDIA_VIDEO;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, REQUEST_CODE_READ_STORAGE);
        } else {
            loadVideos();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_READ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadVideos();
            } else {
                Toast.makeText(this, "无权限读取视频", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadVideos() {
        executorService.execute(() -> {
            List<VideoItem> videos = new ArrayList<>();
            Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            String[] projection = {MediaStore.Video.Media._ID, MediaStore.Video.Media.DATA, MediaStore.Video.Media.DURATION};
            String sortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";

            try (Cursor cursor = getContentResolver().query(uri, projection, null, null, sortOrder)) {
                if (cursor != null) {
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                    int pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                    int durColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);

                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(idColumn);
                        String path = cursor.getString(pathColumn);
                        long duration = cursor.getLong(durColumn);
                        Uri contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                        if (path != null && new File(path).exists()) {
                            videos.add(new VideoItem(contentUri, path, duration));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            runOnUiThread(() -> {
                allVideos.clear();
                allVideos.addAll(videos);
                gridAdapter.notifyDataSetChanged();
                previewAdapter.setVideos(allVideos);
            });
        });
    }

    private void openPreview(int position) {
        containerPreview.setVisibility(View.VISIBLE);
        previewPager.setCurrentItem(position, false);
    }

    private void closePreview() {
        containerPreview.setVisibility(View.GONE);
        // 停止当前播放
        // 这里的处理比较 trick，可以直接刷新 Adapter 让 View 释放
        previewAdapter.notifyDataSetChanged();
    }

    // --- Adapters ---

    private class VideoGridAdapter extends RecyclerView.Adapter<VideoGridAdapter.ViewHolder> {
        private Context context;
        private int itemSize;

        public VideoGridAdapter(Context context) {
            this.context = context;
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            itemSize = (screenWidth - 4) / 5;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video_grid, parent, false);
            view.getLayoutParams().width = itemSize;
            view.getLayoutParams().height = itemSize;
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            VideoItem item = allVideos.get(position);
            holder.loadThumbnail(item.path);

            // 显示时长 (mm:ss)
            long sec = item.duration / 1000;
            holder.tvDuration.setText(String.format("%02d:%02d", sec/60, sec%60));

            boolean isSelected = (selectedVideo == item);
            holder.ivCheck.setImageResource(isSelected ?
                    R.drawable.ic_check_circle_checked : R.drawable.ic_check_circle_unchecked);
            holder.viewMask.setVisibility(isSelected ? View.VISIBLE : View.GONE);

            holder.ivCheck.setOnClickListener(v -> {
                if (isSelected) {
                    selectedVideo = null;
                } else {
                    selectedVideo = item;
                }
                notifyDataSetChanged(); // 刷新所有以更新单选状态
            });

            holder.ivThumb.setOnClickListener(v -> openPreview(position));
        }

        @Override
        public int getItemCount() {
            return allVideos.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivThumb, ivCheck;
            TextView tvDuration;
            View viewMask;

            ViewHolder(View itemView) {
                super(itemView);
                ivThumb = itemView.findViewById(R.id.iv_thumb);
                ivCheck = itemView.findViewById(R.id.iv_check);
                tvDuration = itemView.findViewById(R.id.tv_duration);
                viewMask = itemView.findViewById(R.id.view_mask);
            }

            void loadThumbnail(String path) {
                ivThumb.post(() -> {
                    executorService.execute(() -> {
                        // 使用系统工具获取视频缩略图
                        Bitmap bmp = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND);
                        ivThumb.post(() -> ivThumb.setImageBitmap(bmp));
                    });
                });
            }
        }
    }

    private class VideoPreviewAdapter extends RecyclerView.Adapter<VideoPreviewAdapter.PreviewHolder> {
        private List<VideoItem> videoList = new ArrayList<>();

        public void setVideos(List<VideoItem> list) {
            this.videoList = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public PreviewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video_preview, parent, false);
            return new PreviewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PreviewHolder holder, int position) {
            VideoItem item = videoList.get(position);
            holder.bind(item);
        }

        @Override
        public void onViewDetachedFromWindow(@NonNull PreviewHolder holder) {
            super.onViewDetachedFromWindow(holder);
            holder.stop(); // 页面滑出时停止播放
        }

        @Override
        public int getItemCount() {
            return videoList.size();
        }

        class PreviewHolder extends RecyclerView.ViewHolder {
            ZoomTextureView videoView;
            ImageView btnCenterPlay, btnPlayPause;
            LinearLayout llControls;
            SeekBar seekBar;

            Handler handler = new Handler(Looper.getMainLooper());
            Runnable progressRunnable = new Runnable() {
                @Override
                public void run() {
                    if (videoView.isPlaying()) {
                        seekBar.setProgress(videoView.getCurrentPosition());
                        handler.postDelayed(this, 500);
                    }
                }
            };

            PreviewHolder(View itemView) {
                super(itemView);
                videoView = itemView.findViewById(R.id.video_view);
                btnCenterPlay = itemView.findViewById(R.id.iv_play_center);
                btnPlayPause = itemView.findViewById(R.id.iv_play_pause);
                llControls = itemView.findViewById(R.id.ll_controls);
                seekBar = itemView.findViewById(R.id.seekbar);

                // 点击背景退出
                videoView.setOnClickListener(v -> closePreview());
            }

            void bind(VideoItem item) {
                resetUI();
                videoView.setVideoPath(item.path);

                btnCenterPlay.setOnClickListener(v -> startPlay());
                btnPlayPause.setOnClickListener(v -> {
                    if (videoView.isPlaying()) {
                        videoView.pause();
                        btnPlayPause.setImageResource(R.drawable.ic_play_small);
                        handler.removeCallbacks(progressRunnable);
                    } else {
                        videoView.start();
                        btnPlayPause.setImageResource(R.drawable.ic_pause_small);
                        handler.post(progressRunnable);
                    }
                });

                seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser) videoView.seekTo(progress);
                    }
                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
                });

                videoView.setOnCompletionListener(() -> {
                    resetUI(); // 播放结束回到初始状态
                });
            }

            void startPlay() {
                btnCenterPlay.setVisibility(View.GONE);
                llControls.setVisibility(View.VISIBLE);

                videoView.start();
                seekBar.setMax(videoView.getDuration());
                btnPlayPause.setImageResource(R.drawable.ic_pause_small);
                handler.post(progressRunnable);
            }

            void stop() {
                videoView.pause();
                videoView.seekTo(0);
                handler.removeCallbacks(progressRunnable);
                resetUI();
            }

            void resetUI() {
                btnCenterPlay.setVisibility(View.VISIBLE);
                llControls.setVisibility(View.GONE);
                seekBar.setProgress(0);
            }
        }
    }

    private static class VideoItem {
        Uri uri;
        String path;
        long duration;
        VideoItem(Uri uri, String path, long duration) { this.uri = uri; this.path = path; this.duration = duration; }
    }

    // --- Dialogs (复用 ImageFileActivity 逻辑，略微修改文本) ---

    private void showExitDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_camera_action);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvTitle = dialog.findViewById(R.id.tv_dialog_title);
        TextView btnCancel = dialog.findViewById(R.id.btn_dialog_cancel);
        TextView btnConfirm = dialog.findViewById(R.id.btn_dialog_confirm);

        tvTitle.setText("退出选择？");

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
        if (containerPreview.getVisibility() == View.VISIBLE) {
            closePreview();
        } else {
            showExitDialog();
        }
    }

    private void showUploadDialog() {
        if (selectedVideo == null) {
            Toast.makeText(this, "请先选择视频", Toast.LENGTH_SHORT).show();
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
                    File glbFile = communicationManager.genByVideo(VideoFileActivity.this, new File(selectedVideo.path));

                    runOnUiThread(() -> {
                        handler.removeCallbacks(dotRunnable);
                        dialog.dismiss();

                        if (glbFile != null && glbFile.exists()) {
                            String oldName = glbFile.getName();
                            String hash = GLBFileManager.getFileNameInHash(oldName);
                            GLBFileManager.renameFile(VideoFileActivity.this, glbFile, "NewGLBFile", currentUsername, hash);
                            Toast.makeText(VideoFileActivity.this, "生成成功", Toast.LENGTH_SHORT).show();
                        }
                        finish();
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        handler.removeCallbacks(dotRunnable);
                        dialog.dismiss();
                        String msg = e.getMessage();
                        if(msg == null) msg = "网络连接中断";
                        Toast.makeText(VideoFileActivity.this, "上传失败: " + msg, Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        });

        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) executorService.shutdown();
    }
}