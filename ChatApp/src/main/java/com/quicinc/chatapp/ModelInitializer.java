/*package com.quicinc.chatapp;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ModelInitializer {
    private static final String TAG = "ModelInitializer";

    private static final Map<String, String> supportedSocModel = new HashMap<String, String>() {{
        put("SM8750", "qualcomm-snapdragon-8-elite.json");
        put("SM8650", "qualcomm-snapdragon-8-gen3.json");
        put("QCS8550", "qualcomm-snapdragon-8-gen2.json");
    }};

    public static String initialize(Context context) throws Exception {
        String socModel = Build.SOC_MODEL;
        if (socModel == null || !supportedSocModel.containsKey(socModel)) {
            throw new RuntimeException("Unsupported device. Supported: " + supportedSocModel.keySet());
        }

        File externalDir = context.getExternalCacheDir();
        if (externalDir == null) {
            throw new IOException("External cache directory not found");
        }
        String externalPath = externalDir.getAbsolutePath();

        copyAssetsDir(context, "models", externalPath);
        copyAssetsDir(context, "htp_config", externalPath);

        String htpConfigPath = Paths.get(externalPath, "htp_config", supportedSocModel.get(socModel)).toString();
        Log.d(TAG, "HTP Config Path: " + htpConfigPath);

        return htpConfigPath;
    }

    private static void copyAssetsDir(Context context, String inputAssetRelPath, String outputPath) throws IOException {
        AssetManager assetManager = context.getAssets();
        File outputAssetPath = new File(Paths.get(outputPath, inputAssetRelPath).toString());
        String[] subAssetList = assetManager.list(inputAssetRelPath);

        if (subAssetList == null || subAssetList.length == 0) {
            if (!outputAssetPath.exists()) {
                copyFile(assetManager, inputAssetRelPath, outputAssetPath);
            }
            return;
        }

        if (!outputAssetPath.exists()) {
            outputAssetPath.mkdirs();
        }

        for (String subAssetName : subAssetList) {
            String inputSubAssetPath = Paths.get(inputAssetRelPath, subAssetName).toString();
            copyAssetsDir(context, inputSubAssetPath, outputPath);
        }
    }

    private static void copyFile(AssetManager assetManager, String inputFilePath, File outputAssetFile) throws IOException {
        InputStream inputStream = assetManager.open(inputFilePath);
        OutputStream outputStream = new FileOutputStream(outputAssetFile);
        byte[] buffer = new byte[1024 * 1024];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        inputStream.close();
        outputStream.close();
    }
}*/
package com.quicinc.chatapp;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ModelInitializer {
    private static final String TAG = "ModelInitializer";

    private static final Map<String, String> supportedSocModel = new HashMap<String, String>() {{
        put("SM8750", "qualcomm-snapdragon-8-elite.json");
        put("SM8650", "qualcomm-snapdragon-8-gen3.json");
        put("QCS8550", "qualcomm-snapdragon-8-gen2.json");
    }};

    public static String initialize(Context context) throws Exception {
        String socModel = Build.SOC_MODEL;
        Log.d(TAG, "Detected SoC: " + socModel);

        // ⚠️ 개발 중에는 강제로 대체 설정 허용
        String selectedConfig = supportedSocModel.get(socModel);
        if (selectedConfig == null) {
            selectedConfig = "qualcomm-snapdragon-8-gen3.json"; // 기본값으로 대체
            Log.w(TAG, "Unsupported SoC. Using fallback config: " + selectedConfig);
        }

        File externalDir = context.getExternalCacheDir();
        if (externalDir == null) {
            throw new IOException("External cache directory not found");
        }
        String externalPath = externalDir.getAbsolutePath();

        copyAssetsDir(context, "models", externalPath);
        copyAssetsDir(context, "htp_config", externalPath);

        String htpConfigPath = Paths.get(externalPath, "htp_config", selectedConfig).toString();
        Log.d(TAG, "HTP Config Path: " + htpConfigPath);

        return htpConfigPath;
    }

    private static void copyAssetsDir(Context context, String inputAssetRelPath, String outputPath) throws IOException {
        AssetManager assetManager = context.getAssets();
        File outputAssetPath = new File(Paths.get(outputPath, inputAssetRelPath).toString());
        String[] subAssetList = assetManager.list(inputAssetRelPath);

        if (subAssetList == null || subAssetList.length == 0) {
            if (!outputAssetPath.exists()) {
                copyFile(assetManager, inputAssetRelPath, outputAssetPath);
            }
            return;
        }

        if (!outputAssetPath.exists()) {
            outputAssetPath.mkdirs();
        }

        for (String subAssetName : subAssetList) {
            String inputSubAssetPath = Paths.get(inputAssetRelPath, subAssetName).toString();
            copyAssetsDir(context, inputSubAssetPath, outputPath);
        }
    }

    private static void copyFile(AssetManager assetManager, String inputFilePath, File outputAssetFile) throws IOException {
        InputStream inputStream = assetManager.open(inputFilePath);
        OutputStream outputStream = new FileOutputStream(outputAssetFile);
        byte[] buffer = new byte[1024 * 1024];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        inputStream.close();
        outputStream.close();
    }
}

