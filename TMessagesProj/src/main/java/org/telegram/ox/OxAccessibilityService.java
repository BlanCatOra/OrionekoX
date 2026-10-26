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

    /** getRootInActiveWindow can be null or a floating tab bar; prefer the main app window. */
    private AccessibilityNodeInfo activeRoot() {
        AccessibilityNodeInfo active = getRootInActiveWindow();
        Rect activeBounds = new Rect();
        if (active != null) active.getBoundsInScreen(activeBounds);
        int cx = AndroidUtilities.displaySize.x / 2;
        int cy = AndroidUtilities.displaySize.y / 2;
        // Dialogs and full-screen pages contain the screen center; the glass tab bar
        // at the bottom does not, and its root only exposes the five tab labels.
        if (active != null && activeBounds.contains(cx, cy)) return active;
        AccessibilityNodeInfo best = null;
        long bestArea = 0;
        try {
            java.util.List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (int i = windows.size() - 1; i >= 0; i--) {
                    AccessibilityWindowInfo w = windows.get(i);
                    if (w == null || w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                    AccessibilityNodeInfo r = w.getRoot();
                    if (r == null) continue;
                    Rect b = new Rect();
                    r.getBoundsInScreen(b);
                    if (!b.contains(cx, cy)) continue;
                    long area = (long) b.width() * b.height();
                    if (area > bestArea) {
                        best = r;
                        bestArea = area;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (best != null) {
            android.util.Log.i("OxA11y", "activeRoot fallback active=" + activeBounds
                    + " area=" + bestArea);
            return best;
        }
        return active;
    }

    public static boolean longPress(int x, int y, int durationMs) {
        OxAccessibilityService service = instance;
        if (service == null) return false;
        int duration = Math.max(400, Math.min(3000, durationMs));
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, duration);
        return service.dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }

    /** Finds visible text and long-presses its center (Telegram rows have no clickable ancestor). */
    public static String longPressText(String query, int durationMs) {
        OxAccessibilityService service = instance;
        if (service == null) return "error: accessibility service is not enabled";
        if (query == null || query.trim().isEmpty()) return "error: text is required";
        final String needle = query.trim().toLowerCase();
        final int duration = Math.max(400, Math.min(3000, durationMs <= 0 ? 900 : durationMs));
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
                java.util.ArrayList<AccessibilityNodeInfo> matches = new java.util.ArrayList<>();
                try {
                    java.util.List<AccessibilityNodeInfo> byText =
                            root.findAccessibilityNodeInfosByText(query.trim());
                    if (byText != null) {
                        for (AccessibilityNodeInfo n : byText) {
                            if (n != null && !matches.contains(n)) matches.add(n);
                        }
                    }
                } catch (Throwable ignored) {
                }
                for (int pass = 0; pass < 2; pass++) {
                    for (AccessibilityNodeInfo node : nodes) {
                        CharSequence text = node.getText();
                        CharSequence desc = node.getContentDescription();
                        String label = text != null && text.length() > 0 ? text.toString()
                                : (desc != null ? desc.toString() : null);
                        if (label == null) continue;
                        String lower = label.toLowerCase();
                        boolean match = pass == 0 ? lower.equals(needle) : lower.contains(needle);
                        if (match && !matches.contains(node)) matches.add(node);
                    }
                    if (!matches.isEmpty()) break;
                }
                StringBuilder lpSample = new StringBuilder();
                int lpShown = 0;
                for (AccessibilityNodeInfo n : nodes) {
                    CharSequence t = n.getText();
                    CharSequence d = n.getContentDescription();
                    String l = t != null && t.length() > 0 ? t.toString() : (d != null ? d.toString() : null);
                    if (l == null || lpShown >= 5) continue;
                    String low = l.toLowerCase();
                    if (low.contains("echo") || low.contains("assistant")) {
                        Rect rr = new Rect();
                        n.getBoundsInScreen(rr);
                        lpSample.append(l.replace('\n', ' ')).append('@').append(rr).append(';');
                        lpShown++;
                    }
                }
                Rect activeRect = new Rect();
                root.getBoundsInScreen(activeRect);
                android.util.Log.i("OxA11y", "longPressText rootPkg=" + root.getPackageName()
                        + " cls=" + root.getClassName() + " bounds=" + activeRect
                        + " nodes=" + nodes.size()
                        + " matches=" + matches.size() + " sample=" + lpSample);
                if (matches.isEmpty()) {
                    try {
                        java.util.List<AccessibilityWindowInfo> ws = service.getWindows();
                        if (ws != null) {
                            for (AccessibilityWindowInfo w : ws) {
                                if (w == null || w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                                AccessibilityNodeInfo wr = w.getRoot();
                                Rect wb = new Rect();
                                if (wr != null) wr.getBoundsInScreen(wb);
                                android.util.Log.i("OxA11y", "win layer=" + w.getLayer()
                                        + " bounds=" + wb + " nodes=" + countNodes(wr, 0)
                                        + " cls=" + (wr == null ? "null" : wr.getClassName()));
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
                for (AccessibilityNodeInfo match : matches) {
                    CharSequence text = match.getText();
                    CharSequence desc = match.getContentDescription();
                    String label = text != null && text.length() > 0 ? text.toString()
                            : (desc != null ? desc.toString() : "");
                    Rect r = new Rect();
                    match.getBoundsInScreen(r);
                    int cx = (r.left + r.right) / 2;
                    int cy = (r.top + r.bottom) / 2;
                    android.util.Log.i("OxA11y", "lp match=\"" + label.replace('\n', ' ') + "\" bounds=" + r
                            + " screenH=" + AndroidUtilities.displaySize.y);
                    if (r.top < 0 || r.bottom > AndroidUtilities.displaySize.y || cx <= 0 || cy <= 0) continue;
                    AccessibilityNodeInfo clickable = match;
                    while (clickable != null && !clickable.isClickable()) clickable = clickable.getParent();
                    if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
                        result[0] = "long-pressed \"" + label + "\" bounds=[" + r.left + "," + r.top + "][" + r.right + "," + r.bottom + "]";
                        return;
                    }
                    if (longPress(cx, cy, duration)) {
                        result[0] = "long-pressed \"" + label + "\" bounds=[" + r.left + "," + r.top + "][" + r.right + "," + r.bottom + "] (coordinate)";
                        return;
                    }
                }
                result[0] = "not found or off screen: " + query;
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

    /** Polls the active window until the text appears or timeout_ms elapses. */
    public static String waitForText(String query, int timeoutMs) {
        OxAccessibilityService service = instance;
        if (service == null) return "error: accessibility service is not enabled";
        if (query == null || query.trim().isEmpty()) return "error: text is required";
        final String needle = query.trim().toLowerCase();
        final long timeout = Math.max(500, Math.min(15000, timeoutMs <= 0 ? 5000 : timeoutMs));
        final long deadline = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < deadline) {
            final String[] out = new String[1];
            final CountDownLatch latch = new CountDownLatch(1);
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    AccessibilityNodeInfo root = service.activeRoot();
                    if (root == null) return;
                    java.util.ArrayList<AccessibilityNodeInfo> nodes = new java.util.ArrayList<>();
                    collect(root, nodes, 0);
                    java.util.ArrayList<AccessibilityNodeInfo> matches = new java.util.ArrayList<>();
                    try {
                        java.util.List<AccessibilityNodeInfo> byText =
                                root.findAccessibilityNodeInfosByText(query.trim());
                        if (byText != null) {
                            for (AccessibilityNodeInfo n : byText) {
                                if (n != null && !matches.contains(n)) matches.add(n);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    for (int pass = 0; pass < 2; pass++) {
                        for (AccessibilityNodeInfo node : nodes) {
                            CharSequence text = node.getText();
                            CharSequence desc = node.getContentDescription();
                            String label = text != null && text.length() > 0 ? text.toString()
                                    : (desc != null ? desc.toString() : null);
                            if (label == null) continue;
                            String lower = label.toLowerCase();
                            boolean hit = pass == 0 ? lower.equals(needle) : lower.contains(needle);
                            if (hit && !matches.contains(node)) matches.add(node);
                        }
                        if (!matches.isEmpty()) break;
                    }
                    if (!matches.isEmpty()) {
                        AccessibilityNodeInfo match = matches.get(0);
                        CharSequence text = match.getText();
                        CharSequence desc = match.getContentDescription();
                        String label = text != null && text.length() > 0 ? text.toString()
                                : (desc != null ? desc.toString() : "");
                        Rect r = new Rect();
                        match.getBoundsInScreen(r);
                        out[0] = "found \"" + label.replace('\n', ' ') + "\" bounds=["
                                + r.left + "," + r.top + "][" + r.right + "," + r.bottom + "]";
                    }
                } catch (Throwable t) {
                    out[0] = "error: " + t.getMessage();
                } finally {
                    latch.countDown();
                }
            });
            try {
                latch.await(4, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "timeout waiting for: " + query;
            }
            if (out[0] != null) return out[0];
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return "timeout waiting for: " + query;
    }

    /** Types into the focused/first editable field, optionally selected by label. */
    public static String inputText(String target, String text) {
        OxAccessibilityService service = instance;
        if (service == null) return "error: accessibility service is not enabled";
        if (text == null) return "error: text is required";
        final String[] result = new String[1];
        final CountDownLatch latch = new CountDownLatch(1);
        AndroidUtilities.runOnUIThread(() -> {
            try {
                AccessibilityNodeInfo root = service.activeRoot();
                if (root == null) {
                    result[0] = "error: no active window";
                    return;
                }
                AccessibilityNodeInfo field = null;
                String matched = "";
                String needle = target == null ? "" : target.trim().toLowerCase();
                if (!needle.isEmpty()) {
                    java.util.ArrayList<AccessibilityNodeInfo> nodes = new java.util.ArrayList<>();
                    collect(root, nodes, 0);
                    for (int pass = 0; pass < 2 && field == null; pass++) {
                        for (AccessibilityNodeInfo node : nodes) {
                            CharSequence t = node.getText();
                            CharSequence d = node.getContentDescription();
                            String label = t != null && t.length() > 0 ? t.toString()
                                    : (d != null ? d.toString() : "");
                            if (label.isEmpty() && android.os.Build.VERSION.SDK_INT >= 26) {
                                CharSequence hint = node.getHintText();
                                if (hint != null) label = hint.toString();
                            }
                            if (label.isEmpty()) continue;
                            String lower = label.toLowerCase();
                            boolean hit = pass == 0 ? lower.equals(needle) : lower.contains(needle);
                            if (!hit) continue;
                            AccessibilityNodeInfo editable = findEditable(node, 0);
                            if (editable == null) editable = findEditableParent(node);
                            if (editable != null) {
                                field = editable;
                                matched = label;
                                break;
                            }
                        }
                    }
                }
                if (field == null) field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (field == null) {
                    java.util.ArrayList<AccessibilityNodeInfo> nodes = new java.util.ArrayList<>();
                    collect(root, nodes, 0);
                    for (AccessibilityNodeInfo node : nodes) {
                        if (node.isEditable()) {
                            field = node;
                            break;
                        }
                    }
                }
                if (field == null) {
                    result[0] = "error: no editable field found";
                    return;
                }
                field.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                android.os.Bundle args = new android.os.Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                boolean ok = field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                String described = matched.isEmpty() ? field.getClassName().toString() : matched;
                result[0] = ok ? "typed into \"" + described + "\"" : "error: set text failed";
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

    private static AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 12) return null;
        if (node.isEditable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo hit = findEditable(node.getChild(i), depth + 1);
            if (hit != null) return hit;
        }
        return null;
    }

    private static AccessibilityNodeInfo findEditableParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo parent = node;
        for (int i = 0; i < 8 && parent != null; i++) {
            if (parent.isEditable()) return parent;
            parent = parent.getParent();
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
        if (node == null || depth > 24 || out.size() > 2000) return;
        out.add(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collect(child, out, depth + 1);
        }
    }

    public static String findTextScrolled(String query, int maxScrolls, boolean click) {
        return findTextScrolled(query, maxScrolls, click, false);
    }

    public static String findTextScrolled(String query, int maxScrolls, boolean click, boolean backward) {
        OxAccessibilityService service = instance;
        if (service == null) return "error: accessibility service is not enabled";
        if (query == null || query.trim().isEmpty()) return "error: text is required";
        final String needle = query.trim().toLowerCase();
        int limit = Math.max(0, Math.min(12, maxScrolls));
        for (int step = 0; step <= limit; step++) {
            final int stepFinal = step;
            final String[] out = new String[1];
            final CountDownLatch latch = new CountDownLatch(1);
            AndroidUtilities.runOnUIThread(() -> {
                AccessibilityNodeInfo root = null;
                try {
                    root = service.activeRoot();
                    if (root == null) {
                        out[0] = "error: no active window";
                        return;
                    }
                    java.util.ArrayList<AccessibilityNodeInfo> nodes = new java.util.ArrayList<>();
                    collect(root, nodes, 0);
                    StringBuilder sample = new StringBuilder();
                    int sampled = 0;
                    for (AccessibilityNodeInfo n : nodes) {
                        CharSequence t = n.getText();
                        if (t != null && t.length() > 0 && sampled < 8
                                && (t.toString().startsWith("ui_") || t.toString().startsWith("get_"))) {
                            sample.append(t).append(',');
                            sampled++;
                        }
                    }
                    android.util.Log.i("OxA11y", "step=" + stepFinal + " rootPkg=" + root.getPackageName()
                            + " rootCls=" + root.getClassName() + " nodes=" + nodes.size()
                            + " sample=" + sample);
                    java.util.ArrayList<AccessibilityNodeInfo> matches = new java.util.ArrayList<>();
                    // The framework index is not limited by our traversal cap, which
                    // matters for long settings lists where the row sits deep in the tree.
                    try {
                        java.util.List<AccessibilityNodeInfo> byText =
                                root.findAccessibilityNodeInfosByText(query.trim());
                        if (byText != null) {
                            for (AccessibilityNodeInfo n : byText) {
                                if (n != null && !matches.contains(n)) matches.add(n);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    for (int pass = 0; pass < 2; pass++) {
                        for (AccessibilityNodeInfo node : nodes) {
                            CharSequence text = node.getText();
                            CharSequence desc = node.getContentDescription();
                            String l = text != null && text.length() > 0 ? text.toString()
                                    : (desc != null ? desc.toString() : null);
                            if (l == null) continue;
                            boolean hit = pass == 0 ? l.toLowerCase().equals(needle) : l.toLowerCase().contains(needle);
                            if (hit && !matches.contains(node)) matches.add(node);
                        }
                        if (!matches.isEmpty()) break;
                    }
                    if (!matches.isEmpty()) {
                        for (AccessibilityNodeInfo match : matches) {
                            CharSequence text = match.getText();
                            CharSequence desc = match.getContentDescription();
                            String label = text != null && text.length() > 0 ? text.toString()
                                    : (desc != null ? desc.toString() : "");
                            Rect r = new Rect();
                            match.getBoundsInScreen(r);
                            AccessibilityNodeInfo clickable = match;
                            while (clickable != null && !clickable.isClickable()) clickable = clickable.getParent();
                            if (!click) {
                                out[0] = "found \"" + label + "\" after " + stepFinal + " scroll(s) bounds=[" + r.left + "," + r.top + "][" + r.right + "," + r.bottom + "] clickable=" + (clickable != null);
                                return;
                            }
                            if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                out[0] = "clicked \"" + label + "\" after " + stepFinal + " scroll(s) bounds=[" + r.left + "," + r.top + "][" + r.right + "," + r.bottom + "]";
                                return;
                            }
                            // Telegram lists handle taps on the RecyclerView, so rows often
                            // expose no clickable ancestor; fall back to tapping the center.
                            int cx = (r.left + r.right) / 2;
                            int cy = (r.top + r.bottom) / 2;
                            if (cx > 0 && cy > 0 && cy < AndroidUtilities.displaySize.y
                                    && r.top >= 0 && r.bottom <= AndroidUtilities.displaySize.y && tap(cx, cy)) {
                                out[0] = "tapped \"" + label + "\" after " + stepFinal + " scroll(s) bounds=[" + r.left + "," + r.top + "][" + r.right + "," + r.bottom + "] (coordinate)";
                                return;
                            }
                        }
                    }
                    AccessibilityNodeInfo scrollable = findScrollable(root, 0);
                    if (scrollable == null) {
                        out[0] = "not found and no scrollable container: " + query;
                        return;
                    }
                    Rect sb = new Rect();
                    scrollable.getBoundsInScreen(sb);
                    android.util.Log.i("OxA11y", "scrollable=" + scrollable.getClassName()
                            + " bounds=" + sb);
                    // The next iteration searches this new position; scrolling after
                    // the last allowed step would move the target into view unsearched.
                    if (stepFinal >= limit) {
                        out[0] = "not found after " + limit + " scroll(s): " + query;
                        return;
                    }
                    boolean scrolled = scrollable.performAction(backward
                            ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                            : AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                    android.util.Log.i("OxA11y", "scrollAction=" + scrolled);
                    out[0] = null;
                } catch (Throwable t) {
                    out[0] = "error: " + t.getMessage();
                } finally {
                    latch.countDown();
                }
            });
            try {
                latch.await(8, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (out[0] != null) return out[0];
            try {
                Thread.sleep(450);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return "not found after " + limit + " scroll(s): " + query;
    }

    private static int countNodes(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 20) return 0;
        int count = 1;
        for (int i = 0; i < node.getChildCount(); i++) {
            count += countNodes(node.getChild(i), depth + 1);
        }
        return count;
    }

    private static AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 24) return null;
        if (node.isScrollable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo hit = findScrollable(child, depth + 1);
            if (hit != null) return hit;
        }
        return null;
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
