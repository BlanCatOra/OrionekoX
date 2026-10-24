package org.telegram.ox;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

// Minimal no-op accessibility service: gives agents a requestable permission channel.
// The actual UI-driving tooling lands with the permission system milestone.
public class OxAccessibilityService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
