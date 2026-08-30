package com.accessdroid.termux;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Set;

/**
 * AccessDroid Settings — Onboarding, VLM config, status.
 *
 * Provides a minimal Activity UI that:
 *  1. Guides the user through enabling the AccessibilityService (onboarding).
 *  2. Lets the user configure a VLM provider (endpoint + API key + model name).
 *  3. Displays live status: service running / am socket / termux-perms.
 *
 * The UI is built programmatically (no XML layout files) to avoid
 * inflate-time dependency on a build toolchain; it keeps the
 * aapt2-only build process in build.sh working.
 */
public class SettingsActivity extends Activity {

    static final String PREFS = "accessdroid_prefs";
    static final String KEY_VLM_ENDPOINT = "vlm_endpoint";
    static final String KEY_VLM_API_KEY  = "vlm_api_key";
    static final String KEY_VLM_MODEL    = "vlm_model";
    static final String KEY_SECRET       = "am_secret";

    // UI references (set in onCreate)
    private TextView tvServiceStatus, tvSocketStatus, tvVlmStatus;
    private EditText etEndpoint, etApiKey, etModel, etSecret;
    private Button   btnToggle, btnSave, btnOnboard;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);

        // ── root layout ──
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(0xFF1A1A2E);  // dark background

        // ── title ──
        TextView title = new TextView(this);
        title.setText("AccessDroid — Settings");
        title.setTextColor(0xFF00D2FF);
        title.setTextSize(22);
        title.setPadding(0, 0, 0, dp(16));
        root.addView(title);

        // ── Status section ──
        root.addView(sectionLabel("STATUS"));

        tvServiceStatus = statusRow(root, "AccessibilityService:  ");
        tvSocketStatus  = statusRow(root, "Termux am socket:     ");
        tvVlmStatus     = statusRow(root, "VLM config:           ");

        // ── Onboarding button ──
        btnOnboard = accentButton(root, "Run Onboarding Setup", 0xFF00CC66);
        btnOnboard.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { runOnboarding(); } });

        // ── Separator ──
        root.addView(separator());

        // ── VLM Provider section ──
        root.addView(sectionLabel("VLM PROVIDER"));

        etEndpoint = labeledInput(root, "API Endpoint", "https://api.openai.com/v1/chat/completions");
        etApiKey   = labeledInput(root, "API Key",      "sk-...");
        etModel    = labeledInput(root, "Model",        "gpt-4o");
        etSecret   = labeledInput(root, "amctl secret", "termux-accessdroid-2025");

        btnSave = accentButton(root, "Save Config", 0xFF0088CC);
        btnSave.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { saveConfig(); } });

        // ── Refresh on resume ──
        setContentView(root);
        refreshStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
        loadSavedConfig();
    }

    // ═══════════════════════════════════════════════════════════════════
    //                         STATUS CHECKS
    // ═══════════════════════════════════════════════════════════════════

    private void refreshStatus() {
        tvServiceStatus.setText(statusText(isAccessibilityEnabled(), "ENABLED ✓", "DISABLED ✗"));
        tvSocketStatus.setText(statusText(isTermuxSocketEnabled(), "ENABLED ✓", "DISABLED ✗"));
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean vlmCfg = prefs.getString(KEY_VLM_ENDPOINT, "").length() > 5
                      && prefs.getString(KEY_VLM_API_KEY, "").length() > 5;
        tvVlmStatus.setText(statusText(vlmCfg, "CONFIGURED ✓", "NOT CONFIGURED ✗"));
    }

    private boolean isAccessibilityEnabled() {
        String svc = getApplicationContext().getPackageName() + "/.AccessibilityServiceImpl";
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> list = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_GENERIC);
        for (AccessibilityServiceInfo info : list) {
            if (info.getId().equals(svc)) return true;
        }
        // fallback: also check secure settings directly
        try {
            String raw = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return raw != null && raw.contains("accessdroid");
        } catch (Exception e) { return false; }
    }

    private boolean isTermuxSocketEnabled() {
        try {
            Process p = Runtime.getRuntime().exec(
                    new String[]{"sh", "-c", "cat $HOME/.termux/termux.properties 2>/dev/null | grep -q run-termux-am-socket-server=true"});
            p.waitFor();
            return p.exitValue() == 0;
        } catch (Exception e) { return false; }
    }

    private String statusText(boolean ok, String on, String off) { return ok ? on : off; }

    private TextView statusRow(LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextColor(0xFFBBBBBB);
        lbl.setTextSize(14);
        row.addView(lbl);

        TextView val = new TextView(this);
        val.setTextColor(0xFF66FF66);
        val.setTextSize(14);
        row.addView(val);

        parent.addView(row);
        return val;
    }

    // ═══════════════════════════════════════════════════════════════════
    //                         ONBOARDING
    // ═══════════════════════════════════════════════════════════════════

    private void runOnboarding() {
        // Step 1: Open Accessibility settings
        Toast.makeText(this, "Step 1: Enable AccessDroid in Accessibility settings", Toast.LENGTH_LONG).show();
        try {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open Accessibility settings", Toast.LENGTH_SHORT).show();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //                         SAVE CONFIG
    // ═══════════════════════════════════════════════════════════════════

    private void saveConfig() {
        SharedPreferences.Editor ed = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        ed.putString(KEY_VLM_ENDPOINT, etEndpoint.getText().toString().trim());
        ed.putString(KEY_VLM_API_KEY,  etApiKey.getText().toString().trim());
        ed.putString(KEY_VLM_MODEL,    etModel.getText().toString().trim());
        ed.putString(KEY_SECRET,       etSecret.getText().toString().trim());
        ed.apply();
        Toast.makeText(this, "Config saved", Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    private void loadSavedConfig() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        etEndpoint.setText(prefs.getString(KEY_VLM_ENDPOINT, etEndpoint.getText().toString()));
        etApiKey.setText(prefs.getString(KEY_VLM_API_KEY, ""));
        etModel.setText(prefs.getString(KEY_VLM_MODEL, etModel.getText().toString()));
        etSecret.setText(prefs.getString(KEY_SECRET, etSecret.getText().toString()));
    }

    // ═══════════════════════════════════════════════════════════════════
    //                     UI BUILDER HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private TextView sectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF00D2FF);
        tv.setTextSize(16);
        tv.setPadding(0, dp(16), 0, dp(8));
        return tv;
    }

    private EditText labeledInput(LinearLayout parent, String label, String hint) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextColor(0xFFAAAAAA);
        lbl.setTextSize(12);
        row.addView(lbl);

        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextColor(0xFFFFFFFF);
        et.setHintTextColor(0xFF666666);
        et.setTextSize(14);
        et.setSingleLine(true);
        et.setBackgroundColor(0xFF2A2A3E);
        et.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.addView(et);

        parent.addView(row);
        return et;
    }

    private Button accentButton(LinearLayout parent, String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(0xFFFFFFFF);
        btn.setBackgroundColor(color);
        btn.setPadding(dp(16), dp(10), dp(16), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        btn.setLayoutParams(lp);
        parent.addView(btn);
        return btn;
    }

    private View separator() {
        View v = new View(this);
        v.setBackgroundColor(0xFF333355);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin = dp(16);
        lp.bottomMargin = dp(8);
        v.setLayoutParams(lp);
        return v;
    }

    private int dp(int px) {
        return (int)(px * getResources().getDisplayMetrics().density + 0.5f);
    }
}
