package com.quizai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;

public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Settings.canDrawOverlays(this)) {
            startService(new Intent(this, OverlayService.class));
            moveTaskToBack(true);
            return;
        }
        buildUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Settings.canDrawOverlays(this)) {
            startForegroundService(new Intent(this, OverlayService.class));
            moveTaskToBack(true);
        }
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#09090F"));
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(50), dp(28), dp(50));

        TextView ico = new TextView(this);
        ico.setText("🤖");
        ico.setTextSize(64);
        ico.setGravity(Gravity.CENTER);
        root.addView(ico);

        TextView title = new TextView(this);
        title.setText("QuizAI Auto-Solver");
        title.setTextSize(24);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        lp(title, 0, 12, 0, 6);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Automatisches Quiz-Loesen mit KI");
        sub.setTextSize(13);
        sub.setTextColor(Color.parseColor("#6666AA"));
        sub.setGravity(Gravity.CENTER);
        lp(sub, 0, 0, 0, 32);
        root.addView(sub);

        TextView info = new TextView(this);
        info.setText(
            "So funktioniert es:\n\n" +
            "1  Permission erlauben (einmalig)\n\n" +
            "2  Gruener AI Button erscheint\n\n" +
            "3  Quiz-App oeffnen\n\n" +
            "4  AI Button tippen\n\n" +
            "5  AUTO LOESEN tippen\n\n" +
            "6  KI macht Screenshot, erkennt\n" +
            "   die Antwort und klickt sie!\n\n" +
            "7  Automatisch auf Weiter klicken"
        );
        info.setTextSize(14);
        info.setTextColor(Color.parseColor("#AAAACC"));
        info.setLineSpacing(5, 1);
        info.setBackgroundColor(Color.parseColor("#0F0F1A"));
        info.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ilp.setMargins(0, 0, 0, dp(28));
        info.setLayoutParams(ilp);
        root.addView(info);

        Button btn = new Button(this);
        btn.setText("STARTEN");
        btn.setTextSize(15);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setTextColor(Color.parseColor("#09090F"));
        btn.setBackgroundColor(Color.parseColor("#00E5A0"));
        btn.setPadding(0, dp(14), 0, dp(14));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btn.setLayoutParams(blp);
        btn.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, REQ_OVERLAY);
        });
        root.addView(btn);

        setContentView(root);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_OVERLAY && Settings.canDrawOverlays(this)) {
            startForegroundService(new Intent(this, OverlayService.class));
            moveTaskToBack(true);
        }
    }

    private void lp(TextView v, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        v.setLayoutParams(p);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
