package com.Zhaang1.Twiniverse;

import android.content.Context;
import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GLBFileManager {

    private static final String TAG = "GLBFileManager";
    private static final String EXTENSION = ".glb";

    public static List<String> getFileListByUser(Context context, String username) {
        List<String> result = new ArrayList<>();
        File dir = context.getExternalFilesDir(null);
        if (dir == null || !dir.exists()) return result;

        File[] files = dir.listFiles((d, name) -> name.endsWith(EXTENSION));
        if (files == null) return result;

        for (File f : files) {
            String name = f.getName();
            // 解析文件名中间的 username 部分
            String[] parts = name.split("_");
            if (parts.length >= 3) {
                String fileUser = parts[1];
                if (TextUtils.equals(fileUser, username)) {
                    result.add(name);
                }
            }
        }
        return result;
    }

    /**
     * 获取指定用户最新的GLB文件
     * @param context 上下文
     * @param username 当前登录用户名
     * @return 最新的文件，如果没有则返回 null
     */
    public static File getLatestGLBFile(Context context, String username) {
        File dir = context.getExternalFilesDir(null);
        if (dir == null || !dir.exists()) return null;

        File[] files = dir.listFiles((d, name) -> name.endsWith(EXTENSION));
        if (files == null || files.length == 0) return null;

        // 1. 筛选属于该用户的文件
        List<File> userFiles = new ArrayList<>();
        for (File f : files) {
            String name = f.getName();
            String[] parts = name.split("_");
            if (parts.length >= 3) {
                String fileUser = parts[1];
                if (TextUtils.equals(fileUser, username)) {
                    userFiles.add(f);
                }
            }
        }

        if (userFiles.isEmpty()) {
            return null;
        }

        // 2. 按最后修改时间降序排序
        Collections.sort(userFiles, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                return Long.compare(f2.lastModified(), f1.lastModified());
            }
        });

        // 返回最新的一个
        return userFiles.get(0);
    }

    public static File getFileByName(Context context, String fullFileName) {
        File dir = context.getExternalFilesDir(null);
        return new File(dir, fullFileName);
    }

    public static String getFileNameInUser(String fullFileName) {
        if (TextUtils.isEmpty(fullFileName)) return "";
        String[] parts = fullFileName.split("_");
        if (parts.length >= 1) {
            return parts[0];
        }
        return fullFileName.replace(EXTENSION, "");
    }

    public static String getFileNameInHash(String fullFileName) {
        if (TextUtils.isEmpty(fullFileName)) return "";
        String nameNoExt = fullFileName.replace(EXTENSION, "");
        String[] parts = nameNoExt.split("_");
        if (parts.length >= 3) {
            return parts[parts.length - 1];
        }
        return "";
    }

    public static boolean renameFile(Context context, File oldFile, String newCustomName, String username, String hash) {
        String newFullName = newCustomName + "_" + username + "_" + hash + EXTENSION;
        File newFile = new File(context.getExternalFilesDir(null), newFullName);
        return oldFile.renameTo(newFile);
    }
}