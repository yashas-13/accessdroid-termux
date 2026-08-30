package com.accessdroid.termux;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.AccessibilityService.ScreenshotResult;
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccessibilityServiceImpl extends AccessibilityService {

    private static final String TAG = "AccessDroid";
    private static final String ACTION_TAP   = "com.accessdroid.termux.ACTION_TAP";
    private static final String ACTION_SWIPE = "com.accessdroid.termux.ACTION_SWIPE";
    private static final String ACTION_LONG_PRESS = "com.accessdroid.termux.ACTION_LONG_PRESS";
    private static final String ACTION_SCROLL_UP   = "com.accessdroid.termux.ACTION_SCROLL_UP";
    private static final String ACTION_SCROLL_DOWN = "com.accessdroid.termux.ACTION_SCROLL_DOWN";
    private static final String ACTION_BACK  = "com.accessdroid.termux.ACTION_BACK";
    private static final String ACTION_HOME  = "com.accessdroid.termux.ACTION_HOME";
    private static final String ACTION_RECENTS = "com.accessdroid.termux.ACTION_RECENTS";
    private static final String ACTION_TYPE  = "com.accessdroid.termux.ACTION_TYPE";
    private static final String ACTION_CLICK_TEXT = "com.accessdroid.termux.ACTION_CLICK_TEXT";
    private static final String ACTION_CLICK_ID  = "com.accessdroid.termux.ACTION_CLICK_ID";
    private static final String ACTION_FIND_ID   = "com.accessdroid.termux.ACTION_FIND_ID";
    private static final String ACTION_SCROLL_TO = "com.accessdroid.termux.ACTION_SCROLL_TO";
    private static final String ACTION_PRESS_KEY = "com.accessdroid.termux.ACTION_PRESS_KEY";
    private static final String ACTION_SCREENDUMP = "com.accessdroid.termux.ACTION_SCREENDUMP";
    private static final String ACTION_GET_UI_TREE = "com.accessdroid.termux.ACTION_GET_UI_TREE";

    static AccessibilityServiceImpl sInstance;
    int screenW, screenH;
    BroadcastReceiver receiver;
    Handler handler = new Handler(Looper.getMainLooper());
    // Shared secret – set via build-time constant or default. amctl must send it.
    static final String SECRET = "termux-accessdroid-2025";
    // Screenshot callback state
    interface ScreenshotCB { void onResult(android.graphics.Bitmap bmp); }
    ScreenshotCB pendingCb = null;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleCommand(intent);
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_TAP);
        filter.addAction(ACTION_SWIPE);
        filter.addAction(ACTION_LONG_PRESS);
        filter.addAction(ACTION_SCROLL_UP);
        filter.addAction(ACTION_SCROLL_DOWN);
        filter.addAction(ACTION_BACK);
        filter.addAction(ACTION_HOME);
        filter.addAction(ACTION_RECENTS);
        filter.addAction(ACTION_TYPE);
        filter.addAction(ACTION_CLICK_TEXT);
        filter.addAction(ACTION_CLICK_ID);
        filter.addAction(ACTION_FIND_ID);
        filter.addAction(ACTION_SCROLL_TO);
        filter.addAction(ACTION_PRESS_KEY);
        filter.addAction(ACTION_SCREENDUMP);
        filter.addAction(ACTION_GET_UI_TREE);
        registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        Log.i(TAG, "AccessibilityService connected – ready");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (receiver != null) {
            try { unregisterReceiver(receiver); } catch (Exception e) { }
        }
        sInstance = null;
    }

    /* -------------------------------------------------------------- */
    /*                        Command dispatcher                      */
    /* -------------------------------------------------------------- */
    private void handleCommand(Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        // Basic safety: require the shared secret from amctl.
        String secret = intent.getStringExtra("secret");
        if (secret == null || !secret.equals(SECRET)) {
            Log.w(TAG, "rejected command with bad/missing secret: " + action);
            return;
        }
        Log.d(TAG, "cmd: " + action);

        switch (action) {
            case ACTION_TAP: {
                int x = intent.getIntExtra("x", -1);
                int y = intent.getIntExtra("y", -1);
                if (x < 0 || y < 0) return;
                run(makeTap(x, y, 80L), "tap");
                break;
            }
            case ACTION_LONG_PRESS: {
                int x = intent.getIntExtra("x", -1);
                int y = intent.getIntExtra("y", -1);
                long dur = intent.getLongExtra("duration", 500);
                if (x < 0 || y < 0) return;
                run(makeTap(x, y, dur), "longpress");
                break;
            }
            case ACTION_SWIPE: {
                int x1 = intent.getIntExtra("x1", -1);
                int y1 = intent.getIntExtra("y1", -1);
                int x2 = intent.getIntExtra("x2", -1);
                int y2 = intent.getIntExtra("y2", -1);
                long dur = intent.getLongExtra("duration", 300);
                if (x1 < 0 || x2 < 0) return;
                run(makeSwipe(x1, y1, x2, y2, dur), "swipe");
                break;
            }
            case ACTION_SCROLL_UP: {
                int x = intent.getIntExtra("x", screenW / 2);
                int y = intent.getIntExtra("y", (int)(screenH * 0.5f));
                run(makeScroll(x, y, -0.4f), "scrollUp");
                break;
            }
            case ACTION_SCROLL_DOWN: {
                int x = intent.getIntExtra("x", screenW / 2);
                int y = intent.getIntExtra("y", (int)(screenH * 0.5f));
                run(makeScroll(x, y, 0.4f), "scrollDown");
                break;
            }
            case ACTION_BACK:
                performGlobalAction(GLOBAL_ACTION_BACK);
                break;
            case ACTION_HOME:
                performGlobalAction(GLOBAL_ACTION_HOME);
                break;
            case ACTION_RECENTS:
                performGlobalAction(GLOBAL_ACTION_RECENTS);
                break;
            case ACTION_TYPE: {
                String text = intent.getStringExtra("text");
                if (text != null && !text.isEmpty()) {
                    Bundle args = new Bundle();
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                    AccessibilityNodeInfo focus = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                    if (focus != null) {
                        focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                        Log.i(TAG, "typed: " + text);
                    } else {
                        Log.w(TAG, "no focused input field");
                    }
                }
                break;
            }
            case ACTION_CLICK_TEXT: {
                String txt = intent.getStringExtra("text");
                if (txt != null && !txt.isEmpty()) {
                    AccessibilityNodeInfo root = getRootInActiveWindow();
                    if (root != null) {
                        AccessibilityNodeInfo target = findByText(root, txt);
                        if (target != null) {
                            boolean ok = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            Log.i(TAG, "click '" + txt + "' → " + ok);
                        }
                    }
                }
                break;
            }
            case ACTION_CLICK_ID: {
                String rid = intent.getStringExtra("id");
                if (rid != null && !rid.isEmpty()) {
                    AccessibilityNodeInfo root = getRootInActiveWindow();
                    if (root != null) {
                        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(rid);
                        if (!nodes.isEmpty()) {
                            boolean ok = nodes.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            Log.i(TAG, "click id=" + rid + " → " + ok);
                        }
                    }
                }
                break;
            }
            case ACTION_SCROLL_TO: {
                String txt = intent.getStringExtra("text");
                if (txt != null && !txt.isEmpty()) {
                    AccessibilityNodeInfo root = getRootInActiveWindow();
                    if (root != null) {
                        AccessibilityNodeInfo target = findByText(root, txt);
                        if (target != null) {
                            boolean ok = target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                            Log.i(TAG, "scrollTo '" + txt + "' → " + ok);
                        }
                    }
                }
                break;
            }
            case ACTION_GET_UI_TREE: {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    List<String> lines = new ArrayList<>();
                    dumpNode(root, lines, 0, 8);
                    for (String line : lines) Log.d(TAG, line);
                }
                break;
            }
            case ACTION_SCREENDUMP: {
                int winId = intent.getIntExtra("windowId", -1);
                if (Build.VERSION.SDK_INT >= 33) {
                    takeScreenshot(winId, getMainExecutor(),
                        new TakeScreenshotCallback() {
                            @Override
                            public void onSuccess(ScreenshotResult r) {
                                Log.i(TAG, "screenshot ok (windowId=" + winId + ")");
                                if (pendingCb != null) {
                                    pendingCb.onResult(null);
                                    pendingCb = null;
                                }
                            }
                            @Override
                            public void onFailure(int errCode) {
                                Log.e(TAG, "screenshot fail: " + errCode);
                            }
                        });
                } else {
                    Log.w(TAG, "takeScreenshot requires API 33+");
                }
                break;
            }
            case ACTION_PRESS_KEY: {
                int keyCode = intent.getIntExtra("keyCode", 0);
                // AccessibilityService only exposes a few global actions,
                // not arbitrary keycodes. Map the handful we can.
                int global = -1;
                switch (keyCode) {
                    case KeyEvent.KEYCODE_BACK:        global = GLOBAL_ACTION_BACK; break;
                    case KeyEvent.KEYCODE_HOME:        global = GLOBAL_ACTION_HOME; break;
                    case KeyEvent.KEYCODE_APP_SWITCH:  global = GLOBAL_ACTION_RECENTS; break;
                    default: 
                        Log.w(TAG, "unsupported keycode via a11y: " + keyCode);
                        return;
                }
                performGlobalAction(global);
                break;
            }
        }
    }

    /* -------------------------------------------------------------- */
    /*                        Gesture builders                        */
    /* -------------------------------------------------------------- */
    private void run(GestureDescription g, String name) {
        dispatchGesture(g, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                Log.d(TAG, name + " ok");
            }
            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                Log.w(TAG, name + " cancelled");
            }
        }, null);
    }

    private GestureDescription makeTap(long x, long y, long duration) {
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.StrokeDescription s =
            new GestureDescription.StrokeDescription(p, 0, duration);
        return new GestureDescription.Builder().addStroke(s).build();
    }

    private GestureDescription makeSwipe(int x1, int y1, int x2, int y2, long duration) {
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        GestureDescription.StrokeDescription s =
            new GestureDescription.StrokeDescription(p, 0, duration);
        return new GestureDescription.Builder().addStroke(s).build();
    }

    private GestureDescription makeScroll(int cx, int cy, float fraction) {
        // vertical scroll gesture: from center, flick upward (negative = scroll content down)
        long dy = (long)(screenH * fraction);
        Path p = new Path();
        p.moveTo(cx, cy);
        p.lineTo(cx, cy + dy);
        GestureDescription.StrokeDescription s =
            new GestureDescription.StrokeDescription(p, 0, 200);
        return new GestureDescription.Builder().addStroke(s).build();
    }

    /* -------------------------------------------------------------- */
    /*                      UI-tree / find helpers                    */
    /* -------------------------------------------------------------- */
    private AccessibilityNodeInfo findByText(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        CharSequence cs = node.getText();
        CharSequence desc = node.getContentDescription();
        if ((cs != null && cs.toString().contains(text)) ||
            (desc != null && desc.toString().contains(text))) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = findByText(child, text);
                if (found != null) return found;
                child.recycle();
            }
        }
        return null;
    }

    private void dumpNode(AccessibilityNodeInfo node, List<String> out, int depth, int maxDepth) {
        if (depth > maxDepth || node == null) return;
        String indent = new String(new char[depth]).replace("\0", "  ");
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        CharSequence txt = node.getText();
        CharSequence desc = node.getContentDescription();
        String cls = node.getClassName() != null ? node.getClassName().toString() : "";
        String shortClass = cls.contains(".") ? cls.substring(cls.lastIndexOf('.') + 1) : cls;
        out.add(String.format("%s[%s] id=%s bounds=%s txt=%s desc=%s",
            indent, shortClass, node.getViewIdResourceName(), r,
            txt == null ? "" : txt, desc == null ? "" : desc));

        for (int i = 0; i < node.getChildCount(); i++) {
            dumpNode(node.getChild(i), out, depth + 1, maxDepth);
        }
    }
}