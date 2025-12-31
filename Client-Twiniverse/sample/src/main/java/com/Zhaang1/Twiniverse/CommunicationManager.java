package com.Zhaang1.Twiniverse;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CommunicationManager {

    private String serverIp = "103.45.130.80";
    private int serverPort = 27172;
    private static final int TIMEOUT = 600000; // 600秒超时

    private static final byte CMD_LOGIN = 1;
    private static final byte CMD_IMAGE = 2;
    private static final byte CMD_VIDEO = 3;
    private static final byte CMD_GET_GLB = 4;

    // 当前登录用户名，默认为 guest
    private String currentUsername = "guest";

    public void setConnectionInfo(String ip, int port) {
        this.serverIp = ip;
        this.serverPort = port;
    }

    public void setCurrentUsername(String username) {
        if (!TextUtils.isEmpty(username)) {
            this.currentUsername = username;
        }
    }

    public boolean[] login(String username, String password) throws Exception {
        JSONObject json = new JSONObject();
        json.put("u", username);
        json.put("p", password);
        byte[] payload = json.toString().getBytes(StandardCharsets.UTF_8);

        ResponseData response = sendRequest(CMD_LOGIN, payload);

        String jsonResp = new String(response.data, StandardCharsets.UTF_8);
        JSONArray arr = new JSONArray(jsonResp);

        boolean success = arr.getBoolean(0);
        if (success) {
            this.currentUsername = username;
        }

        return new boolean[]{success, arr.getBoolean(1)};
    }

    public File genByImage(Context context, List<File> images) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream bufferDos = new DataOutputStream(buffer);

        // 协议: [图片数量] + [Size1][Data1] + [Size2][Data2]...
        bufferDos.writeInt(images.size());

        for (File img : images) {
            byte[] fileBytes = readFileToBytes(img);
            bufferDos.writeInt(fileBytes.length);
            bufferDos.write(fileBytes);
        }

        byte[] payload = buffer.toByteArray();

        // 发送请求
        ResponseData response = sendRequest(CMD_IMAGE, payload);

        if (response.data == null || response.data.length == 0) {
            throw new IOException("Server returned empty data");
        }

        // 使用服务器返回的文件名
        String finalFileName = generateFileName(response.filename);
        return saveResponseToFile(context, response.data, finalFileName);
    }

    public File genByVideo(Context context, File video) throws Exception {
        byte[] payload = readFileToBytes(video);

        ResponseData response = sendRequest(CMD_VIDEO, payload);

        if (response.data == null || response.data.length == 0) {
            throw new IOException("Server returned empty data");
        }

        // 使用服务器返回的文件名
        String finalFileName = generateFileName(response.filename);
        return saveResponseToFile(context, response.data, finalFileName);
    }

    /**
     * 通过 Hash 获取 GLB 文件
     */
    public File getGLBByHash(Context context, String hash) throws Exception {
        byte[] payload = hash.getBytes(StandardCharsets.UTF_8);

        // 发送请求 CMD_GET_GLB (4)
        ResponseData response = sendRequest(CMD_GET_GLB, payload);

        // 【核心修复】 防止 OOM (Out Of Memory)
        // 只有当数据长度很小（例如小于1KB）时，才尝试将其转换为字符串来检查错误信息。
        // 如果是几百MB的文件数据，直接跳过此检查。
        if (response.data.length < 1024) {
            String respStr = new String(response.data, StandardCharsets.UTF_8);
            if (respStr.startsWith("ERROR_")) {
                throw new IOException("Server Error: " + respStr);
            }
        }

        if (response.data == null || response.data.length == 0) {
            throw new IOException("Server returned empty data");
        }

        // 手动构造文件名
        String finalFileName = generateFileName(hash + ".glb");
        return saveResponseToFile(context, response.data, finalFileName);
    }

    private String generateFileName(String serverProvidedName) {
        String hashPart = serverProvidedName;
        // 如果服务器传来 NullName 或空，做个保底
        if (TextUtils.isEmpty(hashPart) || "NullName".equals(hashPart)) {
            hashPart = "unknown_hash";
        }

        if (hashPart.toLowerCase().endsWith(".glb")) {
            hashPart = hashPart.substring(0, hashPart.length() - 4);
        }

        // 构造: 时间戳_用户名_哈希.glb
        return System.currentTimeMillis() + "_" + currentUsername + "_" + hashPart + ".glb";
    }

    private static class ResponseData {
        String filename;
        byte[] data;

        ResponseData(String filename, byte[] data) {
            this.filename = filename;
            this.data = data;
        }
    }

    private ResponseData sendRequest(byte cmd, byte[] data) throws IOException {
        Socket socket = null;
        DataOutputStream dos = null;
        DataInputStream dis = null;

        try {
            socket = new Socket(serverIp, serverPort);
            // 针对大文件下载，适当放宽超时时间，但在socket连接层面由系统控制
            // socket.setSoTimeout(TIMEOUT);

            dos = new DataOutputStream(socket.getOutputStream());
            dis = new DataInputStream(socket.getInputStream());

            // 1. 发送请求头
            dos.writeByte(cmd);
            dos.writeInt(data.length);

            // 2. 发送请求体
            dos.write(data);
            dos.flush();

            // 3. 读取响应
            String filename = null;
            byte[] responseData;

            if (cmd == CMD_IMAGE || cmd == CMD_VIDEO) {
                // CMD 2/3: [NameLen][Name][DataLen][Data]
                int nameLen = dis.readInt();
                if (nameLen < 0 || nameLen > 1024) {
                    throw new IOException("Invalid filename length: " + nameLen);
                }

                if (nameLen > 0) {
                    byte[] nameBytes = new byte[nameLen];
                    dis.readFully(nameBytes);
                    filename = new String(nameBytes, StandardCharsets.UTF_8);
                }

                int dataLen = dis.readInt();
                if (dataLen < 0) {
                    throw new IOException("Invalid data length: " + dataLen);
                }

                responseData = new byte[dataLen];
                dis.readFully(responseData);

            } else {
                // CMD 1/4: [DataLen][Data]
                int len = dis.readInt();
                if (len < 0) throw new IOException("Invalid response length");

                responseData = new byte[len];
                dis.readFully(responseData);
            }

            return new ResponseData(filename, responseData);

        } finally {
            if (dos != null) dos.close();
            if (dis != null) dis.close();
            if (socket != null) socket.close();
        }
    }

    private byte[] readFileToBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = fis.read(buf)) != -1) {
                bos.write(buf, 0, len);
            }
            return bos.toByteArray();
        }
    }

    private File saveResponseToFile(Context context, byte[] data, String fileName) throws IOException {
        File dir = context.getExternalFilesDir(null);
        if (dir == null) dir = context.getFilesDir();

        File destFile = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(destFile)) {
            fos.write(data);
        }
        return destFile;
    }
}