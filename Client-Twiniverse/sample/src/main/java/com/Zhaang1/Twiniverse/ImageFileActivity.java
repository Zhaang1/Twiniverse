package com.Zhaang1.Twiniverse;

import android.Manifest;
import android.app.Dialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build; // 导入 Build
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.LayoutInflater;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageFileActivity extends AppCompatActivity {

    private static final int MAX_SELECT_COUNT = 20;
    private static final int REQUEST_CODE_READ_STORAGE = 101;

    // UI
    private TextView tvCount;
    private ImageButton btnBack, btnUpload;
    private RecyclerView recyclerView;
    private FrameLayout containerPreview;
    private ViewPager2 previewPager;

    // Data
    private List<ImageItem> allImages = new ArrayList<>();
    private List<ImageItem> selectedImages = new ArrayList<>();

    // Adapters
    private ImageGridAdapter gridAdapter;
    private ImagePreviewAdapter previewAdapter;

    // Managers
    private CommunicationManager communicationManager;
    private String currentUsername = "guest";
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_file);

        communicationManager = new CommunicationManager();
        String user = getIntent().getStringExtra("USERNAME");
        if (user != null) currentUsername = user;

        executorService = Executors.newSingleThreadExecutor();

        initViews();
        checkPermissionAndLoadImages();
    }

    private void initViews() {
        tvCount = findViewById(R.id.tv_count);
        btnBack = findViewById(R.id.btn_back);
        btnUpload = findViewById(R.id.btn_upload);
        recyclerView = findViewById(R.id.recycler_view);
        containerPreview = findViewById(R.id.container_preview);
        previewPager = findViewById(R.id.preview_pager);

        // Setup Grid RecyclerView
        recyclerView.setLayoutManager(new GridLayoutManager(this, 5));
        gridAdapter = new ImageGridAdapter(this);
        recyclerView.setAdapter(gridAdapter);

        // Setup Preview ViewPager
        previewAdapter = new ImagePreviewAdapter();
        previewPager.setAdapter(previewAdapter);
        previewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);

        // Listeners
        btnBack.setOnClickListener(v -> showExitDialog());
        btnUpload.setOnClickListener(v -> showUploadDialog());

        // 点击预览背景退出预览
        containerPreview.setOnClickListener(v -> closePreview());

        updateCountText();
    }

    private void updateCountText() {
        tvCount.setText(selectedImages.size() + "/" + MAX_SELECT_COUNT);
    }

    /**
     * 【修复】适配 Android 13+ 的权限检查逻辑
     */
    private void checkPermissionAndLoadImages() {
        String permission;
        if (Build.VERSION.SDK_INT >= 33) { // Android 13 (Tiramisu)
            permission = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, REQUEST_CODE_READ_STORAGE);
        } else {
            loadImages();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_READ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadImages();
            } else {
                Toast.makeText(this, "无权限读取相册，请在设置中开启", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadImages() {
        executorService.execute(() -> {
            List<ImageItem> images = new ArrayList<>();
            Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            String[] projection = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA};
            String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

            try (Cursor cursor = getContentResolver().query(uri, projection, null, null, sortOrder)) {
                if (cursor != null) {
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                    // 注意：在Android 10+上直接读取 DATA 路径可能会有限制，但在拥有READ权限且 requestLegacyExternalStorage=true 时通常可行
                    // 更推荐的方式是全程使用 Uri + ContentResolver，但为了复用之前的 File 压缩逻辑，这里依然读取 Path
                    int pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);

                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(idColumn);
                        String path = cursor.getString(pathColumn);
                        Uri contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                        // 过滤无效路径
                        if (path != null && new File(path).exists()) {
                            images.add(new ImageItem(contentUri, path));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            runOnUiThread(() -> {
                allImages.clear();
                allImages.addAll(images);
                gridAdapter.notifyDataSetChanged();
                // 同时更新预览 Adapter 数据
                previewAdapter.setImages(allImages);
            });
        });
    }

    private void openPreview(int position) {
        containerPreview.setVisibility(View.VISIBLE);
        previewPager.setCurrentItem(position, false);
    }

    private void closePreview() {
        containerPreview.setVisibility(View.GONE);
    }

    // --- Adapters ---

    private class ImageGridAdapter extends RecyclerView.Adapter<ImageGridAdapter.ViewHolder> {

        private Context context;
        private int itemSize;

        public ImageGridAdapter(Context context) {
            this.context = context;
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            // 5列，减去 padding 2dp 左右
            itemSize = (screenWidth - 4) / 5;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image_grid, parent, false);
            // 强制正方形
            view.getLayoutParams().width = itemSize;
            view.getLayoutParams().height = itemSize;
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ImageItem item = allImages.get(position);

            holder.loadThumbnail(item.path);

            boolean isSelected = selectedImages.contains(item);
            holder.ivCheck.setImageResource(isSelected ?
                    R.drawable.ic_check_circle_checked : R.drawable.ic_check_circle_unchecked);
            holder.viewMask.setVisibility(isSelected ? View.VISIBLE : View.GONE);

            // 点击圆环：选择/取消
            holder.ivCheck.setOnClickListener(v -> {
                if (isSelected) {
                    selectedImages.remove(item);
                } else {
                    if (selectedImages.size() >= MAX_SELECT_COUNT) {
                        showCustomToast("最多选择" + MAX_SELECT_COUNT + "张图片");
                        return;
                    }
                    selectedImages.add(item);
                }
                notifyItemChanged(position);
                updateCountText();
            });

            // 点击图片：预览
            holder.ivThumb.setOnClickListener(v -> openPreview(position));
        }

        @Override
        public int getItemCount() {
            return allImages.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivThumb, ivCheck;
            View viewMask;

            ViewHolder(View itemView) {
                super(itemView);
                ivThumb = itemView.findViewById(R.id.iv_thumb);
                ivCheck = itemView.findViewById(R.id.iv_check);
                viewMask = itemView.findViewById(R.id.view_mask);
            }

            void loadThumbnail(String path) {
                ivThumb.post(() -> {
                    executorService.execute(() -> {
                        Bitmap bmp = decodeSampledBitmap(path, itemSize, itemSize);
                        ivThumb.post(() -> ivThumb.setImageBitmap(bmp));
                    });
                });
            }
        }
    }

    private class ImagePreviewAdapter extends RecyclerView.Adapter<ImagePreviewAdapter.PreviewHolder> {

        private List<ImageItem> previewList = new ArrayList<>();

        public void setImages(List<ImageItem> list) {
            this.previewList = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public PreviewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image_preview, parent, false);
            return new PreviewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PreviewHolder holder, int position) {
            ImageItem item = previewList.get(position);

            holder.zoomImageView.resetZoom();

            executorService.execute(() -> {
                int w = containerPreview.getWidth();
                int h = containerPreview.getHeight();
                if(w==0) w=1080; if(h==0) h=1920;

                Bitmap bmp = decodeSampledBitmap(item.path, w, h);
                holder.zoomImageView.post(() -> {
                    holder.zoomImageView.setImageBitmap(bmp);
                    holder.zoomImageView.resetZoom();
                });
            });

            holder.zoomImageView.setOnClickListener(v -> closePreview());
        }

        @Override
        public int getItemCount() {
            return previewList.size();
        }

        class PreviewHolder extends RecyclerView.ViewHolder {
            ZoomImageView zoomImageView;
            PreviewHolder(View itemView) {
                super(itemView);
                zoomImageView = itemView.findViewById(R.id.iv_preview);
            }
        }
    }

    // --- Helper Utils ---

    private static class ImageItem {
        Uri uri;
        String path;
        ImageItem(Uri uri, String path) { this.uri = uri; this.path = path; }
    }

    private Bitmap decodeSampledBitmap(String path, int reqWidth, int reqHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
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

    // --- Dialogs & Upload Logic ---

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
        if (selectedImages.isEmpty()) {
            showCustomToast("请先选择图片");
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
                    List<File> sourceFiles = new ArrayList<>();
                    for(ImageItem item : selectedImages) sourceFiles.add(new File(item.path));

                    List<File> compressedFiles = compressImages(sourceFiles);

                    communicationManager.setCurrentUsername(currentUsername);
                    File glbFile = communicationManager.genByImage(ImageFileActivity.this, compressedFiles);

                    runOnUiThread(() -> {
                        handler.removeCallbacks(dotRunnable);
                        dialog.dismiss();

                        if (glbFile != null && glbFile.exists()) {
                            String oldName = glbFile.getName();
                            String hash = GLBFileManager.getFileNameInHash(oldName);
                            GLBFileManager.renameFile(ImageFileActivity.this, glbFile, "NewGLBFile", currentUsername, hash);
                            showCustomToast("生成成功");
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
                        showCustomToast("上传失败: " + msg);
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
                    File temp = new File(getExternalCacheDir(), "upload_temp_" + file.getName());
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
        if (executorService != null) executorService.shutdown();
    }
}