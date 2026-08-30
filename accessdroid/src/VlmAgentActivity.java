package com.accessdroid.termux;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.ScreenshotResult;
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback;
import android.accessibilityservice.GestureDescription;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * VlmAgentActivity — turns AccessDroid into a visual (VLM) agent loop.
 *
 * Flow:
 *   1. Capture screen via AccessibilityService.takeScreenshot (no perms popup).
 *   2. Base64 the bitmap and POST it to a configured VLM chat-completions
 *      endpoint with a system prompt that forces JSON {action, coordinates, reason}.
 *   3. Parse the returned JSON and execute the action as a real gesture
 *      (CLICK / TYPE / SCROLL / BACK / HOME) at the given pixel coordinates.
 *
 * Coordinates from the model are normalized 0..1000 and mapped back to the
 * device's physical resolution (see ScreenViewModel mapping).
 */

public class VlmAgentActivity extends Activity {
    private static final String TAG = "AccessDroidVLM";
    private static final String PREFS = "accessdroid_prefs";

    private TextView tvResult;
    private EditText etPrompt;
    private Button   btnSnap, btnRun, btnSingle;
    private Handler  main = new Handler(Looper.getMainLooper());
    private Executor netIo = Executors.newSingleThreadExecutor();

    // VLM config
    private String endpoint, apiKey, model;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        endpoint = prefs.getString("vlm_endpoint", "https://api.openai.com/v1/chat/completions");
        apiKey   = prefs.getString("vlm_api_key", "");
        model    = prefs.getString("vlm_model", "gpt-4o");

        // ── root ──
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(0xFF1A1A2E);

        TextView title = new TextView(this);
        title.setText("AccessDroid · VLM Agent");
        title.setTextColor(0xFF00D2FF);
        title.setTextSize(22);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title);

        tvResult = new TextView(this);
        tvResult.setText("Ready. Model: " + model + "\nEndpoint: " + endpoint + "\n");
        tvResult.setTextColor(0xFFBBEEBB);
        tvResult.setTextSize(13);
        tvResult.setPadding(0, 0, 0, dp(12));
        root.addView(tvResult);

        etPrompt = new EditText(this);
        etPrompt.setHint("e.g. Click the search bar and type 'weather'");
        etPrompt.setTextColor(0xFFFFFFFF);
        etPrompt.setHintTextColor(0xFF666666);
        etPrompt.setTextSize(14);
        etPrompt.setPadding(dp(12), dp(10), dp(12), dp(10));
        etPrompt.setBackgroundColor(0xFF2A2A3E);
        root.addView(etPrompt);

        // Buttons row
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        btnSnap = new Button(this);
        btnSnap.setText("Screenshot");
        btnSnap.setTextColor(0xFFFFFFFF);
        btnSnap.setBackgroundColor(0xFF0088CC);
        row.addView(btnSnap);

        btnSingle = new Button(this);
        btnSingle.setText("Run 1 step");
        btnSingle.setTextColor(0xFFFFFFFF);
        btnSingle.setBackgroundColor(0xFF00CC66);
        row.addView(btnSingle);

        btnRun = new Button(this);
        btnRun.setText("Auto-loop");
        btnRun.setTextColor(0xFFFFFFFF);
        btnRun.setBackgroundColor(0xFFCC6600);
        row.addView(btnRun);

        root.addView(row);

        btnSnap.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { snapShot(); } });
        btnSingle.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (serviceReady()) runSingleStep(); else notifyServiceOff(); } });
        btnRun.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (serviceReady()) runAutoLoop(); else notifyServiceOff(); } });

        setContentView(root);
    }

    // ═══════════════════════════════════════════════════════════════════
    //                     SERVICE ACCESS
    // ═══════════════════════════════════════════════════════════════════

    private AccessibilityServiceImpl service() {
        return AccessibilityServiceImpl.sInstance;
    }

    private boolean serviceReady() {
        AccessibilityServiceImpl s = service();
        return s != null && s.isConnected();
    }

    private void notifyServiceOff() {
        Toast.makeText(this, "AccessibilityService not running. Enable it in Settings → Accessibility → AccessDroid", Toast.LENGTH_LONG).show();
        try { startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)); } catch (Exception ignore) {}
    }

    // ═══════════════════════════════════════════════════════════════════
    //                        SCREEN CAPTURE
    // ═══════════════════════════════════════════════════════════════════

    private void snapShot() {
        final AccessibilityServiceImpl s = service();
        if (s == null) { notifyServiceOff(); return; }
        if (Build.VERSION.SDK_INT < 33) { Toast.makeText(this, "Screenshot needs Android 13+ (API 33)", Toast.LENGTH_LONG).show(); return; }

        s.captureBitmap(new AccessibilityServiceImpl.ScreenshotCB() {
            @Override public void onResult(Bitmap bmp) {
                if (bmp == null) { toast("Screenshot failed"); return; }
                toast("Captured " + bmp.getWidth() + "x" + bmp.getHeight());
                Log.i(TAG, "captured " + bmp.getWidth() + "x" + bmp.getHeight());
            }
        });
    }

    private void toast(final String m) { main.post(new Runnable() { public void run() { Toast.makeText(VlmAgentActivity.this, m, Toast.LENGTH_SHORT).show(); } }); }

    // ═══════════════════════════════════════════════════════════════════
    //                      VLM SINGLE STEP
    // ═══════════════════════════════════════════════════════════════════

    private void runSingleStep() {
        final AccessibilityServiceImpl s = service();
        if (Build.VERSION.SDK_INT < 33) { toast("Needs API 33+"); return; }

        final String userPrompt = etPrompt.getText().toString().trim();
        if (userPrompt.isEmpty()) { toast("Enter a task first"); return; }
        if (apiKey.isEmpty()) { toast("Set API key in Settings → VLM Provider first"); return; }

        tvResult.setText("Capturing screen…");
        s.captureBitmap(new AccessibilityServiceImpl.ScreenshotCB() {
            @Override public void onResult(Bitmap bmp) {
                if (bmp == null) { tvResult.setText("Screenshot failed"); return; }
                main.post(new Runnable() { public void run() { tvResult.setText("Sending to VLM (" + model + ")…"); } });
                netIo.execute(new Runnable() { public void run() { sendToVlm(bmp, userPrompt); } });
            }
        });
    }

    private void runAutoLoop() {
        final int steps = 5;
        tvResult.setText("Auto-loop: " + steps + " steps");
        for (int i = 0; i < steps; i++) {
            final int step = i + 1;
            main.postDelayed(new Runnable() { public void run() { runSingleStep(); } }, 2500L * i);
            tvResult.setText("Auto-loop step " + step + "/" + steps + " scheduled");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //                    VLM REQUEST (chat-completions)
    // ═══════════════════════════════════════════════════════════════════

    private void sendToVlm(Bitmap bmp, String userPrompt) {
        try {
            // Downscale to keep request small
            Bitmap scaled = bmp;
            int maxDim = 512;
            if (Math.max(bmp.getWidth(), bmp.getHeight()) > maxDim) {
                float scale = (float) maxDim / Math.max(bmp.getWidth(), bmp.getHeight());
                scaled = Bitmap.createScaledBitmap(bmp,
                        Math.round(bmp.getWidth() * scale),
                        Math.round(bmp.getHeight() * scale), true);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos);
            String b64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP);

            String system = "You are an Android OS automation agent. The screen is " +
                bmp.getWidth() + "x" + bmp.getHeight() +
                " pixels. Respond STRICTLY in JSON: " +
                "{\"action\":\"CLICK|TYPE|SCROLL|BACK|HOME\",\"coordinates\":[X,Y],\"text\":\"...\",\"reason\":\"...\"}. " +
                "Coordinates are normalized 0..1000 scale. If TYPE, include text. Return only the JSON object.";

            JSONObject body = new JSONObject();
            body.put("model", model);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", system));
            messages.put(new JSONObject().put("role", "user").put("content", new JSONArray()
                .put(new JSONObject().put("type", "text").put("text", userPrompt))
                .put(new JSONObject().put("type", "image_url")
                     .put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + b64))))
            );
            body.put("messages", messages);
            body.put("max_tokens", 200);

            final String response = httpPost(endpoint, apiKey, body.toString());
            String parsed = parseCompletion(response);
            main.post(new Runnable() { public void run() { executeParsedAction(parsed, userPrompt); } });
        } catch (Exception e) {
            Log.e(TAG, "VLM error", e);
            main.post(new Runnable() { public void run() { tvResult.setText("VLM error: " + e.getMessage()); } });
        }
    }

    private String httpPost(String urlStr, String apiKey, String json) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        OutputStream os = conn.getOutputStream();
        os.write(json.getBytes());
        os.flush(); os.close();
        int code = conn.getResponseCode();
        BufferedReader br = new BufferedReader(new InputStreamReader(
                code >= 400 ? conn.getErrorStream() : conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private String parseCompletion(String resp) {
        try {
            JSONObject obj = new JSONObject(resp);
            String content = obj.getJSONArray("choices")
                .getJSONObject(0).getJSONObject("message").getString("content");
            return content;
        } catch (Exception e) { return resp; } // fallback: raw response
    }

    // ═══════════════════════════════════════════════════════════════════
    //               EXECUTE PARSED VLM ACTION (coordinates)
    // ═══════════════════════════════════════════════════════════════════

    private void executeParsedAction(String raw, String userPrompt) {
        AccessibilityServiceImpl s = service();
        if (s == null) { tvResult.setText("Service disconnected"); return; }
        try {
            JSONObject jo = new JSONObject(extractJson(raw));
            String action = jo.optString("action", "CLICK").toUpperCase();
            JSONArray coords = jo.optJSONArray("coordinates");
            double nx = coords != null && coords.length() > 0 ? coords.getDouble(0) : -1;
            double ny = coords != null && coords.length() > 1 ? coords.getDouble(1) : -1;
            String text = jo.optString("text", "");
            String reason = jo.optString("reason", "");

            tvResult.setText("VLM action: " + action + " " + (coords!=null?coords.toString():"") + "\ntext='" + text + "'\nreason: " + reason);

            switch (action) {
                case "CLICK":
                    if (nx >= 0 && ny >= 0) { s.vlmClick((float)nx, (float)ny); }
                    break;
                case "TYPE":
                    if (!text.isEmpty()) {
                        if (nx >= 0 && ny >= 0) { s.vlmClick((float)nx, (float)ny); }
                        s.typeText(text);
                    }
                    break;
                case "SCROLL":
                    s.vlmScroll(nx >= 0 && ny >= 0 ? (float)nx : 500f, ny >= 0 && ny >= 0 ? (float)ny : 500f);
                    break;
                case "BACK":  s.pressBack();  break;
                case "HOME":  s.pressHome();  break;
                default:
                    tvResult.setText("Unknown action: " + action);
            }
        } catch (Exception e) {
            tvResult.setText("Could not parse: " + raw);
            Log.e(TAG, "parse fail", e);
        }
    }

    private String extractJson(String raw) {
        int s = raw.indexOf('{');
        int e = raw.lastIndexOf('}');
        if (s >= 0 && e > s) return raw.substring(s, e + 1);
        return raw;
    }

    private int dp(int px) { return (int)(px * getResources().getDisplayMetrics().density + 0.5f); }
}
