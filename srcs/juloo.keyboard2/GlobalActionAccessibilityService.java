package juloo.keyboard2;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.MagnificationConfig;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class GlobalActionAccessibilityService extends AccessibilityService {

    private static GlobalActionAccessibilityService instance;
    private MagnificationController magnificationController;
    private SidePanelController sidePanelController;

    private boolean isFlashlightOn = false;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        magnificationController = getMagnificationController();
        Log.d("GlobalActionService", "Service connected, MagnificationController: " + (magnificationController != null));

        sidePanelController = new SidePanelController(this, this);
        updateSidePanelState();
    }

    private void updateSidePanelState() {
        if (sidePanelController != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean enabled = prefs.getBoolean("enable_side_panel", false);
            sidePanelController.setEnabled(enabled);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

    }

    @Override
    public void onInterrupt() {
        if (sidePanelController != null) {
            sidePanelController.setEnabled(false);
            sidePanelController = null;
        }
        instance = null;
        magnificationController = null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (sidePanelController != null) {
            sidePanelController.setEnabled(false);
            sidePanelController = null;
        }
        instance = null;
        magnificationController = null;
        return super.onUnbind(intent);
    }

    public static GlobalActionAccessibilityService getInstance() {
        return instance;
    }

    public void performCustomAction(String command) {
        switch (command) {
            case "kill_app":
                performGlobalAction(GLOBAL_ACTION_BACK);
                try { Thread.sleep(200); } catch (InterruptedException e) {}
                performGlobalAction(GLOBAL_ACTION_BACK);







                performGlobalAction(GLOBAL_ACTION_HOME);
                break;
            case "kill_all":
                performGlobalAction(GLOBAL_ACTION_RECENTS);

                Toast.makeText(this, "Kill All: Open Recents & Clear manually", Toast.LENGTH_SHORT).show();
                break;
            case "prev_app":
                performGlobalAction(GLOBAL_ACTION_RECENTS);
                try { Thread.sleep(100); } catch (InterruptedException e) {}
                performGlobalAction(GLOBAL_ACTION_RECENTS);
                break;
            case "next_app":



                performGlobalAction(GLOBAL_ACTION_RECENTS);
                break;
            case "flashlight":
                toggleFlashlight();
                break;
            case "rotation_lock":
                toggleRotationLock();
                break;
            case "wifi_toggle":
                toggleWifi();
                break;
            case "bluetooth_toggle":


                Intent bt = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                bt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(bt);
                break;
        }
    }

    private void toggleFlashlight() {
        if (Build.VERSION.SDK_INT >= 23) {
            CameraManager cam = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            try {
                String id = cam.getCameraIdList()[0];
                isFlashlightOn = !isFlashlightOn;
                cam.setTorchMode(id, isFlashlightOn);
            } catch (CameraAccessException e) {
                Toast.makeText(this, "Flashlight Error", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Flashlight not supported on this Android version", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleRotationLock() {
        try {
            int current = Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION);
            Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, current == 1 ? 0 : 1);
            Toast.makeText(this, "Rotation " + (current == 1 ? "Locked" : "Unlocked"), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {

            Toast.makeText(this, "Permission required to toggle rotation", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    private void toggleWifi() {
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            if (Build.VERSION.SDK_INT < 29) {
                wifi.setWifiEnabled(!wifi.isWifiEnabled());
            } else {


                Intent panel = new Intent(Settings.Panel.ACTION_WIFI);
                panel.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(panel);
            }
        }
    }



    public void click(float x, float y) {
        Path clickPath = new Path();
        clickPath.moveTo(x, y);
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 50));
        dispatchGesture(gestureBuilder.build(), null, null);
    }

    public void rightClick(float x, float y) {
        longClick(x, y);
    }

    public void longClick(float x, float y) {
        Path clickPath = new Path();
        clickPath.moveTo(x, y);
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 600));
        dispatchGesture(gestureBuilder.build(), null, null);
    }


    public void updateMagnification(boolean enabled, float scale, float centerX, float centerY) {
        if (magnificationController == null) {
            magnificationController = getMagnificationController();
            if (magnificationController == null) {
                Log.e("GlobalActionService", "MagnificationController is null");
                return;
            }
        }

        if (enabled) {
            if (Build.VERSION.SDK_INT >= 33) {
                try {
                    MagnificationConfig.Builder builder = new MagnificationConfig.Builder();
                    builder.setMode(MagnificationConfig.MAGNIFICATION_MODE_WINDOW);
                    builder.setScale(scale);
                    builder.setCenterX(centerX);
                    builder.setCenterY(centerY);
                    magnificationController.setMagnificationConfig(builder.build(), false);
                } catch (Exception e) {
                    magnificationController.setScale(scale, false);
                    magnificationController.setCenter(centerX, centerY, false);
                }
            } else {
                magnificationController.setScale(scale, false);
                magnificationController.setCenter(centerX, centerY, false);
            }
        } else {
            if (Build.VERSION.SDK_INT >= 33) {
                try {
                    MagnificationConfig.Builder builder = new MagnificationConfig.Builder();
                    builder.setMode(MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN);
                    builder.setScale(1.0f);
                    magnificationController.setMagnificationConfig(builder.build(), false);
                    magnificationController.reset(true);
                } catch (Exception e) {
                    magnificationController.reset(true);
                }
            } else {
                magnificationController.reset(true);
            }
        }
    }
}
