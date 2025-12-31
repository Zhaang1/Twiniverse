package com.Zhaang1.Twiniverse;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import android.view.Gravity;

public class LoginActivity extends AppCompatActivity {

    private EditText etAccount, etPassword;
    private FrameLayout flAccountContainer, flPasswordContainer;
    private TextView tvAccountPlaceholder, tvPasswordPlaceholder;
    private Button btnLogin;
    private ProgressBar progressBar; // 新增进度条

    private CommunicationManager communicationManager = new CommunicationManager();

    private static final long ANIM_DURATION = 160L;

    // 用于更新进度的 Handler
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;
    private long startTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // 绑定视图
        etAccount = findViewById(R.id.etAccount);
        etPassword = findViewById(R.id.etPassword);
        flAccountContainer = findViewById(R.id.flAccountContainer);
        flPasswordContainer = findViewById(R.id.flPasswordContainer);
        tvAccountPlaceholder = findViewById(R.id.tvAccountPlaceholder);
        tvPasswordPlaceholder = findViewById(R.id.tvPasswordPlaceholder);
        btnLogin = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBar); // 绑定进度条

        // 初始化占位与容器样式
        setupPlaceholderBehavior(etAccount, flAccountContainer, tvAccountPlaceholder);
        setupPlaceholderBehavior(etPassword, flPasswordContainer, tvPasswordPlaceholder);

        // 登录按钮逻辑
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final String account = etAccount.getText() != null ? etAccount.getText().toString() : "";
                final String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

                // 1. 锁定按钮并改变颜色 (变灰)
                btnLogin.setEnabled(false);
                btnLogin.getBackground().setColorFilter(Color.GRAY, PorterDuff.Mode.MULTIPLY);

                // 2. 开始进度条动画
                startProgressAnimation();

                // 3. 在后台线程执行耗时网络请求，防止阻塞 UI (导致进度条不走)
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // 执行登录
                        // skip login
//                        boolean[] results = {true, true};
                        // normal login

                        boolean[] results = {false, false};
                        try {
                            results = communicationManager.login(account, password);
                        } catch (Exception e) {
                            results[0] = false;
                            results[1] = true;
                        }

                        // 回到主线程处理结果
                        boolean[] finalResults = results;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                handleLoginResult(finalResults);
                            }
                        });
                    }
                }).start();
            }
        });
    }

    /**
     * 启动模拟进度条逻辑
     * 0.5s -> 60%
     * 1.0s -> 80%
     * 2.0s -> 95%
     */
    private void startProgressAnimation() {
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        startTime = System.currentTimeMillis();

        progressRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTime;
                int targetProgress;

                if (elapsed <= 500) {
                    // 0~0.5s: 0% -> 60%
                    targetProgress = (int) (60 * (elapsed / 500.0f));
                } else if (elapsed <= 1000) {
                    // 0.5s~1s: 60% -> 80%
                    targetProgress = 60 + (int) (20 * ((elapsed - 500) / 500.0f));
                } else if (elapsed <= 2000) {
                    // 1s~2s: 80% -> 95%
                    targetProgress = 80 + (int) (15 * ((elapsed - 1000) / 1000.0f));
                } else {
                    // >2s: 保持 95%
                    targetProgress = 95;
                }

                progressBar.setProgress(targetProgress);

                // 如果还没满，继续刷新 (约 60fps)
                if (progressBar.getProgress() < 100) {
                    progressHandler.postDelayed(this, 16);
                }
            }
        };
        progressHandler.post(progressRunnable);
    }

    /**
     * 处理登录结果
     */
    private void handleLoginResult(boolean[] results) {
        // 停止模拟进度的 Runnable
        progressHandler.removeCallbacks(progressRunnable);
        // 立即设置进度为 100%
        progressBar.setProgress(100);

        if (results != null && results[0]) {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.putExtra("ISVIP", results[1]);
            intent.putExtra("ACCOUNT", etAccount.getText().toString());
            startActivity(intent);
            finish();
        } else if(results[1]){
            showCustomToast("服务器或网络异常，登录失败");
            resetLoginUI();
        } else{
            showCustomToast("登录失败，请检查账号密码");
            resetLoginUI();
        }
    }

    /**
     * 重置 UI 状态 (用于登录失败)
     */
    private void resetLoginUI() {
        btnLogin.setEnabled(true);
        // 清除颜色滤镜，恢复原色
        btnLogin.getBackground().clearColorFilter();

        // 隐藏或清空进度条
        progressBar.setVisibility(View.INVISIBLE);
        progressBar.setProgress(0);
    }

    private void setupPlaceholderBehavior(final EditText editText,
                                          final FrameLayout container,
                                          final TextView placeholderLarge) {
        // 初始状态
        if (editText.getText() != null && editText.getText().length() > 0) {
            placeholderLarge.setVisibility(View.INVISIBLE);
            placeholderLarge.setAlpha(1f);
            setContainerFocused(container, false);
        } else {
            placeholderLarge.setVisibility(View.VISIBLE);
            placeholderLarge.setAlpha(1f);
            setContainerFocused(container, false);
        }

        // 焦点变化监听
        editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus) {
                    hidePlaceholder(placeholderLarge);
                    setContainerFocused(container, true);
                } else {
                    if (editText.getText() == null || editText.getText().length() == 0) {
                        showPlaceholder(placeholderLarge);
                        setContainerFocused(container, false);
                    } else {
                        hidePlaceholder(placeholderLarge);
                        setContainerFocused(container, false);
                    }
                }
            }
        });

        // 文本变化监听
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override
            public void onTextChanged(CharSequence s,int start,int before,int count){
                if (s != null && s.length() > 0) {
                    hidePlaceholder(placeholderLarge);
                } else {
                    if (!editText.isFocused()) {
                        showPlaceholder(placeholderLarge);
                    }
                }
            }
            @Override
            public void afterTextChanged(Editable s){}
        });
    }

    private void setContainerFocused(FrameLayout container, boolean focused) {
        if (focused) {
            container.setBackgroundResource(R.drawable.input_bg_focused);
        } else {
            container.setBackgroundResource(R.drawable.input_bg_default);
        }
    }

    private void hidePlaceholder(final TextView placeholderLarge) {
        if (placeholderLarge.getVisibility() == View.INVISIBLE) {
            placeholderLarge.setAlpha(1f);
            return;
        }
        placeholderLarge.animate().cancel();
        placeholderLarge.animate().alpha(0f).setDuration(ANIM_DURATION).setInterpolator(new DecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        placeholderLarge.setVisibility(View.INVISIBLE);
                        placeholderLarge.setAlpha(1f);
                        placeholderLarge.animate().setListener(null);
                    }
                }).start();
    }

    private void showCustomToast(String message) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextColor(Color.WHITE);
        // 复用之前定义的深灰色圆角背景
        tv.setBackgroundResource(R.drawable.bg_dialog_rounded);
        tv.setPadding(30, 20, 30, 20);
        tv.setGravity(Gravity.CENTER);
        toast.setView(tv);
        toast.show();
    }

    private void showPlaceholder(final TextView placeholderLarge) {
        placeholderLarge.animate().cancel();
        placeholderLarge.setVisibility(View.VISIBLE);
        placeholderLarge.setAlpha(0f);
        placeholderLarge.animate().alpha(1f).setDuration(ANIM_DURATION).setInterpolator(new DecelerateInterpolator()).setListener(null).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        progressHandler.removeCallbacksAndMessages(null);
    }
}