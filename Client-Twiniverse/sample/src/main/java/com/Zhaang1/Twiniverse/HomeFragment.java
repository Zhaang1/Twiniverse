package com.Zhaang1.Twiniverse;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.io.File;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";

    // Views
    private ConstraintLayout homeRoot;
    private WebView webView;
    private EditText etGlbName;
    private ImageButton btnRename;

    private CardView containerAddAction;
    private FrameLayout contentAddNormal;
    private LinearLayout contentAddExpanded;
    private LinearLayout contentAddSubOptions;

    private ImageButton btnOptionImage, btnOptionVideo, btnOptionLink;
    private ImageButton btnSourceCamera, btnSourceFile;
    // 移除了相机移动按钮

    private SharedViewModel sharedViewModel;
    private CommunicationManager communicationManager;

    private boolean isRenameMode = false;
    private File currentGlbFile = null;
    private int addActionState = 0;
    private String selectedGenerationType = "";
    private String currentUsername = "";
    private boolean isUserVip = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        homeRoot = view.findViewById(R.id.home_root);
        webView = view.findViewById(R.id.web_view);
        etGlbName = view.findViewById(R.id.et_glb_name);
        btnRename = view.findViewById(R.id.btn_rename);

        containerAddAction = view.findViewById(R.id.container_add_action);
        contentAddNormal = view.findViewById(R.id.content_add_normal);
        contentAddExpanded = view.findViewById(R.id.content_add_expanded);
        contentAddSubOptions = view.findViewById(R.id.content_add_sub_options);

        btnOptionImage = view.findViewById(R.id.btn_option_image);
        btnOptionVideo = view.findViewById(R.id.btn_option_video);
        btnOptionLink = view.findViewById(R.id.btn_option_link);
        btnSourceCamera = view.findViewById(R.id.btn_source_camera);
        btnSourceFile = view.findViewById(R.id.btn_source_file);

        communicationManager = new CommunicationManager();

        setupWebView();
        setupClickListeners(view);
        setupObservers();

        return view;
    }

    private void setupObservers() {
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        sharedViewModel.getAccount().observe(getViewLifecycleOwner(), account -> {
            if (account != null) {
                this.currentUsername = account;
                communicationManager.setCurrentUsername(account);
                loadLatestModel();
            }
        });
        sharedViewModel.getLoginResult().observe(getViewLifecycleOwner(), isVip -> {
            this.isUserVip = (isVip != null && isVip);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
        if (currentUsername != null && !currentUsername.isEmpty()) {
            loadLatestModel();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupClickListeners(View rootView) {
        btnRename.setOnClickListener(v -> toggleRenameMode());

        etGlbName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                toggleRenameMode();
                return true;
            }
            return false;
        });

        etGlbName.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (addActionState != 0) {
                    resetAddActionState();
                    return true;
                }
            }
            return false;
        });

        contentAddNormal.setOnClickListener(v -> setAddActionState(1));

        btnOptionImage.setOnClickListener(v -> {
            selectedGenerationType = "IMAGE";
            setAddActionState(2);
        });

        btnOptionVideo.setOnClickListener(v -> {
            if (isUserVip) {
                selectedGenerationType = "VIDEO";
                setAddActionState(2);
            } else {
                showVipDialog();
            }
        });

        btnOptionLink.setOnClickListener(v -> {
            resetAddActionState();
            showLinkInputDialog();
        });

        btnSourceCamera.setOnClickListener(v -> {
            navigateToGenerationActivity(true);
            resetAddActionState();
        });

        btnSourceFile.setOnClickListener(v -> {
            navigateToGenerationActivity(false);
            resetAddActionState();
        });

        webView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (addActionState != 0) {
                    resetAddActionState();
                    return true;
                }
            }
            return false;
        });

        rootView.setOnClickListener(v -> {
            if (addActionState != 0) {
                resetAddActionState();
            }
        });
    }

    private void showLinkInputDialog() {
        if (getContext() == null) return;
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_rename);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvTitle = dialog.findViewById(R.id.tv_dialog_title);
        EditText etInput = dialog.findViewById(R.id.et_new_name);
        TextView btnCancel = dialog.findViewById(R.id.btn_cancel);
        TextView btnConfirm = dialog.findViewById(R.id.btn_confirm);

        tvTitle.setText("请输入获取链接");
        etInput.setHint("输入Hash值");
        etInput.setText("");

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String hash = etInput.getText().toString().trim();
            if (TextUtils.isEmpty(hash)) {
                showCustomToast("链接不能为空");
                return;
            }
            dialog.dismiss();
            performGetGlbByHash(hash);
        });

        dialog.show();
    }

    private void performGetGlbByHash(String hash) {
        if (getContext() == null) return;

        Dialog loadingDialog = new Dialog(getContext());
        loadingDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        loadingDialog.setContentView(R.layout.dialog_camera_action);
        loadingDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        loadingDialog.setCancelable(false);

        TextView tvTitle = loadingDialog.findViewById(R.id.tv_dialog_title);
        TextView tvDots = loadingDialog.findViewById(R.id.tv_dialog_dots);
        loadingDialog.findViewById(R.id.view_divider_horizontal).setVisibility(View.GONE);
        loadingDialog.findViewById(R.id.ll_dialog_buttons).setVisibility(View.GONE);

        tvTitle.setText("请稍候");

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

        loadingDialog.show();

        new Thread(() -> {
            try {
                File glbFile = communicationManager.getGLBByHash(getContext(), hash);

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        handler.removeCallbacks(dotRunnable);
                        loadingDialog.dismiss();

                        if (glbFile != null && glbFile.exists()) {
                            String originalHash = GLBFileManager.getFileNameInHash(glbFile.getName());
                            GLBFileManager.renameFile(getContext(), glbFile, "NewGLBFile", currentUsername, originalHash);

                            showCustomToast("获取成功");
                            loadLatestModel();
                        } else {
                            showCustomToast("获取失败：文件不存在");
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        handler.removeCallbacks(dotRunnable);
                        loadingDialog.dismiss();
                        String msg = e.getMessage();
                        if (msg == null) msg = "网络错误";
                        showCustomToast("获取失败: " + msg);
                    });
                }
            }
        }).start();
    }

    private void showVipDialog() {
        if (getContext() == null) return;
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_vip_only);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        TextView btnConfirm = dialog.findViewById(R.id.btn_dialog_confirm);
        btnConfirm.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void toggleRenameMode() {
        if (currentGlbFile == null) return;

        isRenameMode = !isRenameMode;
        etGlbName.setEnabled(isRenameMode);

        if (isRenameMode) {
            etGlbName.requestFocus();
            btnRename.setBackgroundResource(R.drawable.bg_home_btn_active);
            btnRename.setImageResource(R.drawable.ic_icon_check);
            btnRename.setColorFilter(Color.parseColor("#DDDDDD"));
        } else {
            btnRename.setBackgroundResource(R.drawable.bg_home_btn_normal);
            btnRename.setImageResource(R.drawable.ic_icon_edit);
            performFileRename();
        }
    }

    private void performFileRename() {
        if (currentGlbFile != null && currentGlbFile.exists()) {
            String newName = etGlbName.getText().toString().trim();
            if (newName.isEmpty()) return;

            String oldName = currentGlbFile.getName();
            String hash = GLBFileManager.getFileNameInHash(oldName);
            if (hash.isEmpty()) hash = "legacy";

            boolean success = GLBFileManager.renameFile(getContext(), currentGlbFile, newName, currentUsername, hash);
            if (success) {
                currentGlbFile = GLBFileManager.getFileByName(getContext(), newName + "_" + currentUsername + "_" + hash + ".glb");
                showCustomToast("重命名成功");
            } else {
                showCustomToast("重命名失败");
            }
        }
    }

    private void setAddActionState(int state) {
        if (homeRoot != null) {
            AutoTransition transition = new AutoTransition();
            transition.setDuration(200);
            if (webView != null) transition.excludeTarget(webView, true);
            TransitionManager.beginDelayedTransition(homeRoot, transition);
        }

        addActionState = state;

        contentAddNormal.setVisibility(View.GONE);
        contentAddExpanded.setVisibility(View.GONE);
        contentAddSubOptions.setVisibility(View.GONE);

        switch (state) {
            case 0:
                contentAddNormal.setVisibility(View.VISIBLE);
                containerAddAction.setCardBackgroundColor(Color.parseColor("#1F1F1F"));
                break;
            case 1:
                contentAddExpanded.setVisibility(View.VISIBLE);
                containerAddAction.setCardBackgroundColor(Color.parseColor("#2B6EF6"));
                break;
            case 2:
                contentAddSubOptions.setVisibility(View.VISIBLE);
                containerAddAction.setCardBackgroundColor(Color.parseColor("#2B6EF6"));
                break;
        }
    }

    private void resetAddActionState() {
        setAddActionState(0);
        selectedGenerationType = "";
    }

    private void navigateToGenerationActivity(boolean isCamera) {
        Class<?> targetClass = null;

        if ("IMAGE".equals(selectedGenerationType)) {
            targetClass = isCamera ? ImageCameraActivity.class : ImageFileActivity.class;
        } else if ("VIDEO".equals(selectedGenerationType)) {
            targetClass = isCamera ? VideoCameraActivity.class : VideoFileActivity.class;
        }

        if (targetClass != null) {
            Intent intent = new Intent(getActivity(), targetClass);
            intent.putExtra("USERNAME", this.currentUsername);
            startActivity(intent);
        }
    }

    private void loadLatestModel() {
        File latest = GLBFileManager.getLatestGLBFile(getContext(), currentUsername);

        if (latest != null && latest.exists()) {
            if (currentGlbFile == null || !latest.getAbsolutePath().equals(currentGlbFile.getAbsolutePath())) {
                etGlbName.setHint("model_filename.glb");
                load3DModel(latest);
            } else {
                String userDefinedName = GLBFileManager.getFileNameInUser(latest.getName());
                if (!etGlbName.getText().toString().equals(userDefinedName)) {
                    etGlbName.setText(userDefinedName);
                }
            }
        } else {
            etGlbName.setText("");
            etGlbName.setHint("");
            currentGlbFile = null;
            webView.loadUrl("about:blank");
            webView.setBackgroundColor(Color.parseColor("#0F1115"));
        }
    }

    private void load3DModel(File file) {
        this.currentGlbFile = file;

        String userDefinedName = GLBFileManager.getFileNameInUser(file.getName());
        etGlbName.setText(userDefinedName);

        String fileUri = "file://" + file.getAbsolutePath();
        String html = getHTMLContent(fileUri);

        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
        webView.setBackgroundColor(Color.TRANSPARENT);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        try {
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
        } catch (Exception e) {
            Log.w(TAG, "file access config error: " + e.getMessage());
        }

        webView.addJavascriptInterface(new AndroidJsBridge(), "Android");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.setBackgroundColor(Color.TRANSPARENT);
    }

    private String getHTMLContent(String modelPath) {
        return "<!doctype html>\n" +
                "<html>\n" +
                "<head>\n" +
                "  <meta charset='utf-8'>\n" +
                "  <meta name='viewport' content='width=device-width, initial-scale=1'>\n" +
                "  <title>Viewer</title>\n" +
                "  <style>html,body{width:100%;height:100%;margin:0;padding:0;overflow:hidden;background-color:#0F1115;}#renderCanvas{width:100%;height:100%;touch-action:none;outline:none;}</style>\n" +
                "  <script src='https://cdn.babylonjs.com/babylon.js'></script>\n" +
                "  <script src='https://cdn.babylonjs.com/loaders/babylonjs.loaders.min.js'></script>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <canvas id='renderCanvas'></canvas>\n" +
                "  <script>\n" +
                "    (function(){\n" +
                "      const canvas = document.getElementById('renderCanvas');\n" +
                "      const engine = new BABYLON.Engine(canvas, true);\n" +
                "      const scene = new BABYLON.Scene(engine);\n" +
                "      scene.clearColor = new BABYLON.Color3.FromHexString('#0F1115');\n" +
                "      \n" +
                "      const camera = new BABYLON.ArcRotateCamera('camera', Math.PI / 2, Math.PI / 4, 5, BABYLON.Vector3.Zero(), scene);\n" +
                "      camera.attachControl(canvas, true);\n" +
                "      camera.angularSensibilityX = 1500;\n" +
                "      camera.angularSensibilityY = 1500;\n" +
                "      camera.wheelPrecision = 100;\n" + // 缩放灵敏度减半
                "      camera.minZ = 0.001;\n" + // 保持小近裁剪面
                "      \n" +
                "      const light = new BABYLON.HemisphericLight('hemi', new BABYLON.Vector3(0, 1, 0), scene);\n" +
                "      light.intensity = 1.0;\n" +
                "      \n" +
                "      // 移除了 window.moveCamera\n" +
                "      \n" +
                "      function computeBoundsAndFrame(meshes){\n" +
                "        let min = new BABYLON.Vector3(Number.MAX_VALUE, Number.MAX_VALUE, Number.MAX_VALUE);\n" +
                "        let max = new BABYLON.Vector3(-Number.MAX_VALUE, -Number.MAX_VALUE, -Number.MAX_VALUE);\n" +
                "        let meshCount = 0;\n" +
                "        meshes.forEach(function(m){\n" +
                "          if (!m.getBoundingInfo) return;\n" +
                "          const bi = m.getBoundingInfo();\n" +
                "          if (bi.minimumWorld && bi.maximumWorld) {\n" +
                "            min = BABYLON.Vector3.Minimize(min, bi.minimumWorld);\n" +
                "            max = BABYLON.Vector3.Maximize(max, bi.maximumWorld);\n" +
                "            meshCount++;\n" +
                "          }\n" +
                "        });\n" +
                "        if (meshCount === 0) return null;\n" +
                "        const center = min.add(max).scale(0.5);\n" +
                "        const radius = max.subtract(center).length();\n" +
                "        return {center: center, radius: radius};\n" +
                "      }\n" +
                "      BABYLON.SceneLoader.Append('', '" + modelPath + "', scene,\n" +
                "        function (sc) {\n" +
                "            const info = computeBoundsAndFrame(sc.meshes);\n" +
                "            if (info) {\n" +
                "              camera.setTarget(info.center);\n" +
                "              camera.radius = info.radius * 2.0;\n" +
                "              // 恢复标准的半径限制，防止穿模或无限拉远\n" +
                "              camera.lowerRadiusLimit = info.radius * 0.1;\n" +
                "              camera.upperRadiusLimit = info.radius * 10.0;\n" +
                "            }\n" +
                "            if(Android && Android.onModelLoaded) Android.onModelLoaded();\n" +
                "        },\n" +
                "        null,\n" +
                "        function (scene, message) {\n" +
                "          if(Android && Android.log) Android.log('Error: ' + message);\n" +
                "          if(Android && Android.onModelLoaded) Android.onModelLoaded();\n" +
                "        }\n" +
                "      );\n" +
                "      engine.runRenderLoop(function(){ if (scene) scene.render(); });\n" +
                "      window.addEventListener('resize', function(){ engine.resize(); });\n" +
                "    })();\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>\n";
    }

    private class AndroidJsBridge {
        @JavascriptInterface
        public void onModelLoaded() {}
        @JavascriptInterface
        public void log(String msg) { Log.i(TAG, "JS_LOG: " + msg); }
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

    @Override
    public void onPause() {
        if (webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        currentGlbFile = null;
        super.onDestroyView();
    }
}