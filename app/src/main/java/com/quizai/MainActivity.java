package com.quizai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;

public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Settings.canDrawOverlays(this)) {
            startOverlayService();
            return;
        }
        buildUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Settings.canDrawOverlays(this)) {
            startOverlayService();
        }
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#09090F"));
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(32), dp(60), dp(32), dp(60));

        TextView ico = new TextView(this);
        ico.setText("🤖");
        ico.setTextSize(72);
        ico.setGravity(Gravity.CENTER);
        root.addView(ico);

        TextView title = new TextView(this);
        title.setText("QuizAI Solver");
        title.setTextSize(26);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        setMargin(title, 0, 16, 0, 8);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("KI löst Quizfragen automatisch");
        sub.setTextSize(13);
        sub.setTextColor(Color.parseColor("#6666AA"));
        sub.setGravity(Gravity.CENTER);
        setMargin(sub, 0, 0, 0, 40);
        root.addView(sub);

        TextView info = new TextView(this);
        info.setText(
            "So funktioniert es:\n\n" +
            "1  App starten\n\n" +
            "2  Permission einmal erlauben\n\n" +
            "3  Gruener Button erscheint\n   auf deinem Bildschirm\n\n" +
            "4  Oeffne deine Quiz-App\n   (TikTok, WhatsApp, egal)\n\n" +
            "5  Tippe den Button\n\n" +
            "6  Screenshot laden - KI loest es!"
        );
        info.setTextSize(14);
        info.setTextColor(Color.parseColor("#AAAACC"));
        info.setLineSpacing(6, 1);
        info.setBackgroundColor(Color.parseColor("#0F0F1A"));
        info.setPadding(dp(16), dp(16), dp(16), dp(16));
        setMargin(info, 0, 0, 0, 36);
        root.addView(info);

        Button btn = new Button(this);
        btn.setText("STARTEN");
        btn.setTextSize(15);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setTextColor(Color.parseColor("#09090F"));
        btn.setBackgroundColor(Color.parseColor("#00E5A0"));
        btn.setPadding(0, dp(14), 0, dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> askPermission());
        root.addView(btn);

        setContentView(root);
    }

    private void askPermission() {
        new AlertDialog.Builder(this)
            .setTitle("Permission benoetigt")
            .setMessage(
                "QuizAI braucht die Erlaubnis ueber anderen Apps angezeigt zu werden.\n\n" +
                "Im naechsten Screen:\n" +
                "- QuizAI in der Liste suchen\n" +
                "- Schalter aktivieren\n" +
                "- Zurueck kommen"
            )
            .setPositiveButton("OK, weiter", (d, w) -> {
                Intent i = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
                );
                startActivityForResult(i, REQ_OVERLAY);
            })
            .setNegativeButton("Abbrechen", null)
            .show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                startOverlayService();
            } else {
                new AlertDialog.Builder(this)
                    .setTitle("Bitte Permission erlauben!")
                    .setMessage("Ohne diese Permission kann der schwebende Button nicht erscheinen.")
                    .setPositiveButton("Nochmal", (d, w) -> askPermission())
                    .show();
            }
        }
    }

    private void startOverlayService() {
        Intent i = new Intent(this, OverlayService.class);
        startForegroundService(i);
        moveTaskToBack(true);
    }

    private void setMargin(TextView v, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        v.setLayoutParams(lp);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
