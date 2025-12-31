package com.Zhaang1.Twiniverse;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DebugActivity extends AppCompatActivity {

    private CommunicationManager commManager;
    private TextView tvConsole;
    private ScrollView scrollView;
    private EditText etIp, etPort;
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug);

        commManager = new CommunicationManager();
        executorService = Executors.newSingleThreadExecutor();

        initView();
    }

    private void initView() {
        etIp = findViewById(R.id.et_ip);
        etPort = findViewById(R.id.et_port);
        tvConsole = findViewById(R.id.tv_console);
        scrollView = findViewById(R.id.scroll_view);

        Button btnSave = findViewById(R.id.btn_save_config);
        Button btnLogin = findViewById(R.id.btn_test_login);
        Button btnImg = findViewById(R.id.btn_test_img);
        Button btnVideo = findViewById(R.id.btn_test_video);

        btnSave.setOnClickListener(v -> {
            String ip = etIp.getText().toString().trim();
            String portStr = etPort.getText().toString().trim();
            if (!ip.isEmpty() && !portStr.isEmpty()) {
                commManager.setConnectionInfo(ip, Integer.parseInt(portStr));
                appendLog("System", "IP Configured: " + ip + ":" + portStr);
            }
        });

        // Test 1: Login
        btnLogin.setOnClickListener(v -> {
            appendLog("Send", "Sending Login Request...");
            executorService.execute(() -> {
                try {
                    appendLog("Send", "Try with username: admin");
                    appendLog("Send", "Try with password: 123456");
                    boolean[] result = commManager.login("admin", "123456");
                    runOnUiThread(() -> appendLog("Recv", "Login Result: " + Arrays.toString(result)));
                } catch (Exception e) {
                    runOnUiThread(() -> appendLog("Error", e.toString()));
                }
            });
        });

        // Test 2: Gen By Image (Assets: pic1.jpg, pic2.jpg)
        btnImg.setOnClickListener(v -> {
            appendLog("Send", "Loading pic1.jpg, pic2.jpg from assets and sending...");
            executorService.execute(() -> {
                try {
                    // Copy assets to temp file
                    File f1 = copyAssetToCache(this, "testFiles/pic1.jpg");
                    File f2 = copyAssetToCache(this, "testFiles/pic2.jpg");

                    List<File> files = new ArrayList<>();
                    files.add(f1);
                    files.add(f2);

                    File result = commManager.genByImage(this, files);
                    runOnUiThread(() -> appendLog("Recv", "GLB saved: " + result.getName() + " (" + result.length() + " bytes)"));
                } catch (Exception e) {
                    runOnUiThread(() -> appendLog("Error", e.toString()));
                }
            });
        });

        // Test 3: Gen By Video (Assets: vid1.mp4)
        btnVideo.setOnClickListener(v -> {
            appendLog("Send", "Loading vid1.mp4 from assets and sending...");
            executorService.execute(() -> {
                try {
                    File fVideo = copyAssetToCache(this, "testFiles/vid1.mp4");

                    File result = commManager.genByVideo(this, fVideo);
                    runOnUiThread(() -> appendLog("Recv", "GLB saved: " + result.getName() + " (" + result.length() + " bytes)"));
                } catch (Exception e) {
                    runOnUiThread(() -> appendLog("Error", e.toString()));
                }
            });
        });
    }

    private void appendLog(String tag, String msg) {
        String logLine = "> [" + tag + "] " + msg + "\n";
        tvConsole.append(logLine);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    /**
     * 将 assets 目录下的文件复制到应用的 Cache 目录，以便转换为 File 对象
     */
    private File copyAssetToCache(Context context, String assetPath) throws Exception {
        File cacheDir = context.getCacheDir();
        String fileName = assetPath.substring(assetPath.lastIndexOf("/") + 1);
        File outFile = new File(cacheDir, fileName);

        try (InputStream is = context.getAssets().open(assetPath);
             FileOutputStream fos = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
        }
        return outFile;
    }
}