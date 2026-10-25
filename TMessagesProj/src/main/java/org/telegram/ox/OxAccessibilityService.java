package org.telegram.ox;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Accessibility bridge used by the UI-automation tools. Only the tools call into
 * it; the service keeps no event history and exposes no screen content unless the
 * user has explicitly granted the accessibility permission.
 */
public class OxAccessibilityService extends AccessibilityService {

    private static volatile OxAccessibilityService instance;

    public static OxAccessibilityService getInstance() {
        return instance;
    }

    public static boolean isReady() {
        return instance != null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    public static String dumpUi() {
        OxAccessibilityService service = instance;
        if (service == null) return "error: accessibility service is not enabled";
        final String[] result = new String[1];
        final CountDownLatch latch = new CountDownLatch(1);
        AndroidUtilities.runOnUIThread(() -> {
            try {
                AccessibilityNodeInfo root = service.activeRoot();
                if (root == null) {
                    result[0] = "error: no active window";
                    return;
                }
                StringBuilder sb = new StringBuilder();
                sb.append("package=").append(root.getPackageName()).append('\n');
                walk(root, sb, 0, 0, new int[]{0});
                root.recycle();
                result[0] = sb.toString();
            } catch (Throwable t) {
                result[0] = "error: " + t.getMessage();
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await(8, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0] == null ? "timeout" : result[0];
    }

    private static void walk(AccessibilityNodeInfo node, StringBuilder sb, int depth, int index, int[] count) {
        if (node == null || depth > 24 || count[0] > 240) return;
        count[0]++;
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        String cls = node.getClassName() == null ? "" : node.getClassName().toString();
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        for (int i = 0; i < depth; i++) sb.append("  ");
        sb.append('[').append(index).append("] ").append(cls);
        if (text != null && text.length() > 0) sb.append(" text=\"").append(text.toString().replace("\n", "\\n")).append('"');
        if (desc != null && desc.length() > 0) sb.append(" desc=\"").append(desc.toString().replace("\n", "\\n")).append('"');
        if (node.isClickable()) sb.append(" clickable");
        if (node.isScrollable()) sb.append(" scrollable");
        if (node.isEditable()) sb.append(" editable");
        sb.append(" bounds=[").append(r.left).append(',').append(r.top).append("][").append(r.right).append(',').append(r.bottom).append("]\n");
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            walk(child, sb, depth + 1, i, count);
            child.recycle();
        }
    }

    public static boolean tap(int x, int y) {
        OxAccessibilityService service = instance;
        if (service == null) return false;
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 60);
        return service.dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }

    public static boolean swipe(int x1, int y1, int x2, int y2, int durationMs) {
        OxAccessibilityService service = instance;
        if (service == null) return false;
        int duration = Math.max(50, Math.min(2000, durationMs));
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, duration);
        return service.dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }

    /** getRootInActiveWindow can be null while a dialog/IME is animating; fall back to the newest app window. */
    private AccessibilityNodeInfo activeRoot() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) return root;
        try {
            java.util.List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (int i = windows.size() - 1; i >= 0; i--) {
                    AccessibilityWindowInfo w = windows.get(i);
                    if (w == null || w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                    AccessibilityNodeInfo r = w.getRoot();
                    if (r != null) return r;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static String tapText(String query) {
        OxAccessibilityService service = instance;
        if (service == null) return "error: accessibility service is not enabled";
        if (query == null || query.trim().isEmpty()) return "error: text is required";
        final String needle = query.trim().toLowerCase();
        final String[] result = new String[1];
        final CountDownLatch latch = new CountDownLatch(1);
        AndroidUtilities.runOnUIThread(() -> {
            try {
                AccessibilityNodeInfo root = service.activeRoot();
                if (root == null) {
                    result[0] = "error: no active window";
                    return;
                }
                java.util.ArrayList<AccessibilityNodeInfo> nodes = new java.util.ArrayList<>();
                collect(root, nodes, 0);
                for (int pass = 0; pass < 2; pass++) {
                    for (AccessibilityNodeInfo node : nodes) {
                        CharSequence text = node.getText();
                        CharSequence desc = node.getContentDescription();
                        String label = text != null && text.length() > 0 ? text.toString()
                                : (desc != null ? desc.toString() : null);
                        if (label == null) continue;
                        String lower = label.toLowerCase();
                        boolean match = pass == 0 ? lower.equals(needle) : lower.contains(needle);
                        if (!match) continue;
                        AccessibilityNodeInfo clickable = node;
                        while (clickable != null && !clickable.isClickable()) clickable = clickable.getParent();
                        if (clickable == null) continue;
                        Rect r = new Rect();
                        node.getBoundsInScreen(r);
                        if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            result[0] = "clicked \"" + label + "\" bounds=[" + r.left + "," + r.top + "][" + r.right + "," + r.bottom + "]";
                            return;
                        }
                    }
                }
                result[0] = "not clickable: " + query;
            } catch (Throwable t) {
                result[0] = "error: " + t.getMessage();
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await(8, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0] == null ? "timeout" : result[0];
    }

    private static void collect(AccessibilityNodeInfo node, java.util.ArrayList<AccessibilityNodeInfo> out, int depth) {
        if (node == null || depth > 24 || out.size() > 400) return;
        out.add(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collect(child, out, depth + 1);
        }
    }

    public static boolean globalAction(String name) {
        OxAccessibilityService service = instance;
        if (service == null) return false;
        int action;
        switch (name == null ? "" : name.toLowerCase()) {
            case "back": action = GLOBAL_ACTION_BACK; break;
            case "home": action = GLOBAL_ACTION_HOME; break;
            case "recents": action = GLOBAL_ACTION_RECENTS; break;
            case "notifications": action = GLOBAL_ACTION_NOTIFICATIONS; break;
            case "quick_settings": action = GLOBAL_ACTION_QUICK_SETTINGS; break;
            case "power": action = GLOBAL_ACTION_POWER_DIALOG; break;
            default: return false;
        }
        return service.performGlobalAction(action);
    }

    public static String screenshot() {
        OxAccessibilityService service = instance;
        if (service == null) return "error: accessibility service is not enabled";
        if (Build.VERSION.SDK_INT < 30) return "error: screenshot requires Android 11+";
        final String[] result = new String[1];
        final CountDownLatch latch = new CountDownLatch(1);
        final File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "oxauto");
        dir.mkdirs();
        final File file = new File(dir, "screen-" + System.currentTimeMillis() + ".png");
        AndroidUtilities.runOnUIThread(() -> {
            try {
                service.takeScreenshot(Display.DEFAULT_DISPLAY, service.getMainExecutor(),
                        new TakeScreenshotCallback() {
                            @Override
                            public void onSuccess(ScreenshotResult screenshot) {
                                try (HardwareBuffer buffer = screenshot.getHardwareBuffer()) {
                                    Bitmap hw = Bitmap.wrapHardwareBuffer(buffer, screenshot.getColorSpace());
                                    if (hw == null) {
                                        result[0] = "error: could not wrap screenshot";
                                        return;
                                    }
                                    Bitmap soft = hw.copy(Bitmap.Config.ARGB_8888, false);
                                    try (FileOutputStream out = new FileOutputStream(file)) {
                                        soft.compress(Bitmap.CompressFormat.PNG, 100, out);
                                    }
                                    result[0] = file.getAbsolutePath();
                                } catch (Throwable t) {
                                    result[0] = "error: " + t.getMessage();
                                } finally {
                                    latch.countDown();
                                }
                            }

                            @Override
                            public void onFailure(int errorCode) {
                                result[0] = "error: screenshot failed (" + errorCode + ")";
                                latch.countDown();
                            }
                        });
            } catch (Throwable t) {
                result[0] = "error: " + t.getMessage();
                latch.countDown();
            }
        });
        try {
            latch.await(12, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0] == null ? "timeout" : result[0];
    }
}
