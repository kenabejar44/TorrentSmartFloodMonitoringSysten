package com.example.mainpage;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Standardized helper for controlling device Flashlight / Torch during emergency alerts.
 * Ensures clean setup, error handling, permission checks, and automatic deactivation.
 */
public class FlashlightHelper {

    private static final String TAG = "FlashlightHelper";
    private static final Handler flashHandler = new Handler(Looper.getMainLooper());
    private static boolean isFlashing = false;
    private static String activeCameraId = null;

    public static synchronized void triggerFlashAlert(Context context) {
        if (context == null) return;
        try {
            if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
                Log.w(TAG, "Device does not have camera flash feature.");
                return;
            }

            CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager == null) return;

            String[] cameraIds = cameraManager.getCameraIdList();
            if (cameraIds == null || cameraIds.length == 0) return;

            String targetCameraId = null;
            for (String id : cameraIds) {
                try {
                    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                    Boolean hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    if (Boolean.TRUE.equals(hasFlash) && (facing == null || facing == CameraCharacteristics.LENS_FACING_BACK)) {
                        targetCameraId = id;
                        break;
                    }
                } catch (Throwable ignored) {}
            }

            if (targetCameraId == null) {
                targetCameraId = cameraIds[0];
            }

            final String finalCameraId = targetCameraId;
            activeCameraId = finalCameraId;

            // Stop any ongoing flash sequence first
            stopFlashAlert(context);

            isFlashing = true;

            // Flash 5 times (200ms ON, 200ms OFF)
            for (int i = 0; i < 5; i++) {
                final int index = i;
                flashHandler.postDelayed(() -> {
                    if (!isFlashing) return;
                    try {
                        cameraManager.setTorchMode(finalCameraId, true);
                        flashHandler.postDelayed(() -> {
                            try {
                                cameraManager.setTorchMode(finalCameraId, false);
                            } catch (Throwable ignored) {}
                        }, 200);
                    } catch (Throwable t) {
                        Log.e(TAG, "Failed to set torch mode ON at pulse " + index + ": " + t.getMessage());
                    }
                }, i * 400L);
            }

            // Ensure torch is turned OFF at end of sequence
            flashHandler.postDelayed(() -> {
                try {
                    cameraManager.setTorchMode(finalCameraId, false);
                } catch (Throwable ignored) {}
                isFlashing = false;
            }, 5 * 400L + 300L);

        } catch (Throwable t) {
            Log.e(TAG, "Failed to trigger flash alert: " + t.getMessage());
        }
    }

    public static synchronized void stopFlashAlert(Context context) {
        isFlashing = false;
        flashHandler.removeCallbacksAndMessages(null);

        if (context == null) return;
        try {
            if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) return;
            CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager == null) return;

            if (activeCameraId != null) {
                try {
                    cameraManager.setTorchMode(activeCameraId, false);
                } catch (Throwable ignored) {}
            } else {
                String[] cameraIds = cameraManager.getCameraIdList();
                if (cameraIds != null) {
                    for (String id : cameraIds) {
                        try {
                            cameraManager.setTorchMode(id, false);
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error stopping flash: " + t.getMessage());
        }
    }
}
