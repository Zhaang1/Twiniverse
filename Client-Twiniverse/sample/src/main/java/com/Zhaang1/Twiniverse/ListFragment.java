package com.Zhaang1.Twiniverse;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ListFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private FileAdapter adapter;
    private List<File> fileList = new ArrayList<>();
    private SharedViewModel sharedViewModel;
    private String currentUsername = "";

    private PopupWindow currentPopupWindow;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_list, container, false);

        recyclerView = view.findViewById(R.id.recycler_view_list);
        tvEmpty = view.findViewById(R.id.tv_empty);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FileAdapter();
        recyclerView.setAdapter(adapter);

        setupObservers();

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    closeCurrentMenu();
                }
            }
        });

        recyclerView.setOnTouchListener((v, event) -> {
            if(event.getAction() == MotionEvent.ACTION_DOWN) {
                adapter.closeAllMenus();
            }
            return false;
        });

        return view;
    }

    private void setupObservers() {
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        sharedViewModel.getAccount().observe(getViewLifecycleOwner(), account -> {
            if (account != null) {
                this.currentUsername = account;
                refreshFileList();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if(!TextUtils.isEmpty(currentUsername)) {
            refreshFileList();
        }
    }

    private void refreshFileList() {
        if (getContext() == null) return;

        fileList.clear();

        List<String> fileNames = GLBFileManager.getFileListByUser(getContext(), currentUsername);
        File dir = getContext().getExternalFilesDir(null);

        for (String name : fileNames) {
            fileList.add(new File(dir, name));
        }

        Collections.sort(fileList, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

        if (fileList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }

        adapter.notifyDataSetChanged();
    }

    private void closeCurrentMenu() {
        if (currentPopupWindow != null && currentPopupWindow.isShowing()) {
            currentPopupWindow.dismiss();
            currentPopupWindow = null;
        }
    }

    // --- Helper for UI ---
    private int dp2px(float dp) {
        if (getContext() == null) return (int) dp;
        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        return (int) (dp * metrics.density + 0.5f);
    }

    // --- Adapter ---

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_glb_file, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            File file = fileList.get(position);

            String userDefinedName = GLBFileManager.getFileNameInUser(file.getName());
            String hash = GLBFileManager.getFileNameInHash(file.getName());

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String dateStr = sdf.format(new Date(file.lastModified()));

            holder.tvName.setText(userDefinedName);
            holder.tvDate.setText(dateStr);

            holder.btnMore.setOnClickListener(v -> {
                showPopupMenu(v, file, userDefinedName, hash);
            });

            holder.itemView.setOnClickListener(v -> {
                jumpToHomeAndShow(file);
            });
        }

        @Override
        public int getItemCount() {
            return fileList.size();
        }

        public void closeAllMenus() {
            closeCurrentMenu();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvDate;
            ImageButton btnMore;

            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_file_name);
                tvDate = itemView.findViewById(R.id.tv_file_date);
                btnMore = itemView.findViewById(R.id.btn_more);
            }
        }
    }

    // --- 核心逻辑：菜单显示 (修复宽度和位置) ---
    private void showPopupMenu(View anchorView, File file, String userName, String hash) {
        closeCurrentMenu();

        if (getContext() == null) return;
        View popupView = LayoutInflater.from(getContext()).inflate(R.layout.popup_file_menu, null);

        int widthPx = dp2px(80);

        currentPopupWindow = new PopupWindow(popupView,
                widthPx,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);

        currentPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        currentPopupWindow.setElevation(dp2px(4)); // 设置阴影

        // 获取 Anchor 的宽高
        int anchorHeight = anchorView.getHeight();
        int anchorWidth = anchorView.getWidth();

        // xOff: 向左移动 (菜单宽度 - 按钮宽度)，使右边对齐
        int xOff = anchorWidth - widthPx;
        int yOff = -anchorHeight;

        // 微调：稍微向下一点点，或者完全覆盖。根据你的描述"覆盖住原先的空间"
        // 现在的计算会让菜单的 Top-Right 和 按钮的 Top-Right 重合。

        currentPopupWindow.showAsDropDown(anchorView, xOff, yOff);

        // 绑定事件
        TextView menuRename = popupView.findViewById(R.id.menu_rename);
        TextView menuShare = popupView.findViewById(R.id.menu_share);
        TextView menuDelete = popupView.findViewById(R.id.menu_delete);

        menuRename.setOnClickListener(v -> {
            currentPopupWindow.dismiss();
            showRenameDialog(file, userName);
        });

        menuShare.setOnClickListener(v -> {
            currentPopupWindow.dismiss();
            copyToClipboard(hash);
        });

        menuDelete.setOnClickListener(v -> {
            currentPopupWindow.dismiss();
            showDeleteDialog(file, userName);
        });
    }

    private void jumpToHomeAndShow(File file) {
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            BottomNavigationView nav = activity.findViewById(R.id.bottomNavigation);
            nav.setSelectedItemId(R.id.homeNavigation);

            if (file.exists()) {
                file.setLastModified(System.currentTimeMillis());
            }
        }
    }

    // --- Actions ---

    private void copyToClipboard(String text) {
        if (getContext() == null) return;
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("GLB Hash", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            showCustomToast("链接已复制到剪切板");
        }
    }

    private void showRenameDialog(File file, String oldName) {
        if (getContext() == null) return;
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_rename);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvTitle = dialog.findViewById(R.id.tv_dialog_title);
        EditText etNewName = dialog.findViewById(R.id.et_new_name);
        TextView btnCancel = dialog.findViewById(R.id.btn_cancel);
        TextView btnConfirm = dialog.findViewById(R.id.btn_confirm);

        tvTitle.setText("为 " + oldName + " 重命名");
        etNewName.setText(oldName);
        etNewName.setSelection(oldName.length());

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String newName = etNewName.getText().toString().trim();
            if (TextUtils.isEmpty(newName)) {
                showCustomToast("名称不能为空");
                return;
            }
            if (newName.equals(oldName)) {
                dialog.dismiss();
                return;
            }

            String hash = GLBFileManager.getFileNameInHash(file.getName());
            boolean success = GLBFileManager.renameFile(getContext(), file, newName, currentUsername, hash);

            if (success) {
                showCustomToast("重命名成功");
                refreshFileList();
            } else {
                showCustomToast("重命名失败");
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showDeleteDialog(File file, String userName) {
        if (getContext() == null) return;
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_camera_action);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvTitle = dialog.findViewById(R.id.tv_dialog_title);
        TextView btnCancel = dialog.findViewById(R.id.btn_dialog_cancel);
        TextView btnConfirm = dialog.findViewById(R.id.btn_dialog_confirm);
        dialog.findViewById(R.id.tv_dialog_dots).setVisibility(View.GONE);

        tvTitle.setText("确认删除 " + userName + "？");

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            if (file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    showCustomToast("删除成功");
                    refreshFileList();
                } else {
                    showCustomToast("删除失败");
                }
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showCustomToast(String message) {
        if (getContext() == null) return;
        Toast toast = Toast.makeText(getContext(), message, Toast.LENGTH_SHORT);
        TextView tv = new TextView(getContext());
        tv.setText(message);
        tv.setTextColor(Color.WHITE);
        tv.setBackgroundResource(R.drawable.bg_dialog_rounded);
        tv.setPadding(30, 20, 30, 20);
        tv.setGravity(Gravity.CENTER);
        toast.setView(tv);
        toast.show();
    }
}