package com.Zhaang1.Twiniverse;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

public class UserFragment extends Fragment {

    private TextView tvUsername;
    private ImageView ivVipStatus;
    private Button btnLogout;

    private SharedViewModel sharedViewModel;

    // 连续点击逻辑相关变量
    private long lastVipClickTime = 0;
    private int vipClickCount = 0;
    private static final long CLICK_INTERVAL = 500; // 0.5s
    private static final int CLICK_THRESHOLD = 5;   // 5次

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_user, container, false);

        tvUsername = view.findViewById(R.id.tv_username);
        ivVipStatus = view.findViewById(R.id.iv_vip_status);
        btnLogout = view.findViewById(R.id.btn_logout);

        setupObservers();

        btnLogout.setOnClickListener(v -> performLogout());

        // 添加 VIP 图标点击监听 (用于触发 Debug 模式)
        ivVipStatus.setOnClickListener(v -> handleVipClick());

        return view;
    }

    private void setupObservers() {
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

        // 1. 监听用户名
        sharedViewModel.getAccount().observe(getViewLifecycleOwner(), account -> {
            if (account != null) {
                tvUsername.setText(account);
            } else {
                tvUsername.setText("未登录");
            }
        });

        // 2. 监听 VIP 状态
        sharedViewModel.getLoginResult().observe(getViewLifecycleOwner(), isVip -> {
            updateVipUI(isVip != null && isVip);
        });
    }

    private void updateVipUI(boolean isVip) {
        // 清除可能存在的颜色滤镜，保证显示JPG原色
        ivVipStatus.clearColorFilter();

        if (isVip) {
            // 是会员：显示会员专属圆形图标
            ivVipStatus.setImageResource(R.drawable.ic_vip_round);
        } else {
            // 非会员：显示非会员圆形图标
            ivVipStatus.setImageResource(R.drawable.ic_no_vip_round);
        }
    }

    private void handleVipClick() {
        long currentTime = System.currentTimeMillis();

        // 判断两次点击间隔是否在规定时间内
        if (currentTime - lastVipClickTime < CLICK_INTERVAL) {
            vipClickCount++;
        } else {
            // 超时，重置计数为1（当前这次算第1次）
            vipClickCount = 1;
        }

        lastVipClickTime = currentTime;

        // 达到触发阈值
        if (vipClickCount >= CLICK_THRESHOLD) {
            vipClickCount = 0; // 重置计数防止重复触发
            if (getActivity() != null) {
                Intent intent = new Intent(getActivity(), DebugActivity.class);
                startActivity(intent);
            }
        }
    }

    private void performLogout() {
        if (getActivity() == null) return;

        // 跳转回 LoginActivity
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        // 清空任务栈，防止用户按返回键回到主界面
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);

        // 结束当前 Activity
        getActivity().finish();
    }
}