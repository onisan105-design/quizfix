package com.quizai;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import androidx.core.app.NotificationCompat;

public class OverlayService extends Service {

    private static final String CH = "quizai";
    private static OverlayService instance;
    private WindowManager wm;
    private View bubble;
    private View panel;
    private WindowManager.LayoutParams bubbleLP;
    private boolean panelOpen = false;

    // Speed in milliseconds
    private int speedMs = 2000;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        createChannel();
        startForeground(1, buildNotif());
        createBubble();
    }

    @Override public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }
    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        if (bubble != null) try { wm.removeView(bubble); } catch (Exception e) {}
        if (panel  != null) try { wm.removeView(panel);  } catch (Exception e) {}
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(CH, "QuizAI", NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
    }

    private Notification buildNotif() {
        Intent stop = new Intent(this, OverlayService.class);
        stop.setAction("STOP");
        PendingIntent stopPI = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CH)
            .setContentTitle("QuizAI aktiv")
            .setContentText("Tippe den gruenen Button zum Loesen")
            .setSmallIcon(android.R.drawable.ic_menu_help)
            .addAction(android.R.drawable.ic_delete, "Beenden", stopPI)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    // ── FLOATING BUBBLE ───────────────────────────────────────

    private void createBubble() {
        TextView tv = new TextView(this);
        tv.setText("AI");
        tv.setTextSize(16);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(Color.BLACK);
        tv.setGravity(Gravity.CENTER);
        styleBubble(tv, "#00E5A0");
        tv.setElevation(dp(12));
        bubble = tv;

        bubbleLP = new WindowManager.LayoutParams(
            dp(60), dp(60),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        bubbleLP.gravity = Gravity.TOP | Gravity.START;
        bubbleLP.x = getScreenW() - dp(76);
        bubbleLP.y = dp(300);

        tv.setOnTouchListener(new DragTap());
        wm.addView(bubble, bubbleLP);
    }

    private class DragTap implements View.OnTouchListener {
        float sx, sy; int ox, oy; boolean moved;
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    moved=false; sx=e.getRawX(); sy=e.getRawY(); ox=bubbleLP.x; oy=bubbleLP.y; return true;
                case MotionEvent.ACTION_MOVE:
                    float dx=e.getRawX()-sx, dy=e.getRawY()-sy;
                    if(Math.abs(dx)>8||Math.abs(dy)>8) moved=true;
                    if(moved){
                        bubbleLP.x=Math.max(0,Math.min(getScreenW()-dp(60),(int)(ox+dx)));
                        bubbleLP.y=Math.max(0,Math.min(getScreenH()-dp(60),(int)(oy+dy)));
                        wm.updateViewLayout(bubble,bubbleLP);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if(!moved) onTap(); return true;
            }
            return false;
        }
    }

    private void onTap() {
        if (panelOpen) closePanel();
        else openPanel();
    }

    // ── PANEL ─────────────────────────────────────────────────

    private void openPanel() {
        if (panelOpen) return;

        WebView wv = new WebView(this);
        wv.getSettings().setJavaScriptEnabled(true);
        wv.getSettings().setDomStorageEnabled(true);
        wv.setWebViewClient(new WebViewClient());
        wv.addJavascriptInterface(new Bridge(), "Android");

        DisplayMetrics dm = getResources().getDisplayMetrics();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (int)(dm.heightPixels * 0.72f),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.BOTTOM;

        wv.loadDataWithBaseURL("https://quizai.app", buildHTML(), "text/html", "UTF-8", null);
        panel = wv;
        wm.addView(panel, lp);
        panelOpen = true;
        setBubble("X", "#FF4466");
    }

    private void closePanel() {
        if (!panelOpen) return;
        try { wm.removeView(panel); } catch (Exception e) {}
        panel = null;
        panelOpen = false;
        setBubble("AI", "#00E5A0");
    }

    // ── JAVASCRIPT BRIDGE ─────────────────────────────────────

    public class Bridge {
        @JavascriptInterface
        public void close() {
            new Handler(Looper.getMainLooper()).post(() -> closePanel());
        }

        @JavascriptInterface
        public void solve(int speed) {
            speedMs = speed;
            // Prüfe ob Accessibility Service aktiv
            if (!QuizAccessibilityService.isRunning()) {
                showToastStatic("Bitte Accessibility Permission aktivieren!");
                // Öffne Einstellungen
                new Handler(Looper.getMainLooper()).post(() -> {
                    Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                });
                return;
            }
            // Panel schliessen dann Screenshot
            new Handler(Looper.getMainLooper()).post(() -> {
                closePanel();
                // Kurz warten damit Panel weg ist
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    setBubble("...", "#FFA726");
                    showToastStatic("Screenshot wird gemacht...");
                    QuizAccessibilityService.triggerSolve(speedMs);
                }, 600);
            });
        }

        @JavascriptInterface
        public void setSpeed(int ms) {
            speedMs = ms;
        }

        @JavascriptInterface
        public boolean isAccessibilityOn() {
            return QuizAccessibilityService.isRunning();
        }

        @JavascriptInterface
        public void openAccessibilitySettings() {
            new Handler(Looper.getMainLooper()).post(() -> {
                Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            });
        }

        @JavascriptInterface
        public void vibrate() {
            Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vib != null) vib.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    // ── HTML PANEL ────────────────────────────────────────────

    private String buildHTML() {
        return "<!DOCTYPE html><html><head>" +
        "<meta charset='UTF-8'>" +
        "<meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=no'>" +
        "<style>" +
        "*{margin:0;padding:0;box-sizing:border-box;font-family:system-ui,sans-serif}" +
        "body{background:#0F0F1A;color:#EEEEFF;border-radius:22px 22px 0 0;border-top:2.5px solid #00E5A0;height:100vh;display:flex;flex-direction:column;overflow:hidden}" +
        ".bar{width:40px;height:4px;background:#2A2A42;border-radius:2px;margin:10px auto 0;flex-shrink:0}" +
        ".hdr{display:flex;align-items:center;justify-content:space-between;padding:8px 16px 10px;border-bottom:1px solid #1C1C2E;flex-shrink:0}" +
        ".htit{font-size:18px;font-weight:900;color:#fff}.htit b{color:#00E5A0}" +
        ".xbtn{width:34px;height:34px;background:#1C1C2E;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:16px;cursor:pointer;text-align:center;line-height:34px;color:#fff}" +
        ".scr{flex:1;overflow-y:auto;padding:14px}" +

        // Accessibility warning
        ".acc-warn{background:rgba(255,68,102,.1);border:1.5px solid rgba(255,68,102,.4);border-radius:12px;padding:12px 14px;margin-bottom:12px;display:none}" +
        ".acc-warn.show{display:block}" +
        ".acc-title{font-size:13px;font-weight:800;color:#FF4466;margin-bottom:6px}" +
        ".acc-txt{font-size:11px;color:#AAAACC;line-height:1.6;margin-bottom:10px}" +
        ".acc-btn{width:100%;padding:10px;background:#FF4466;color:#fff;border:none;border-radius:8px;font-size:13px;font-weight:700;cursor:pointer}" +

        // Status
        ".status{background:#141420;border:1.5px solid #2A2A42;border-radius:12px;padding:12px 14px;margin-bottom:12px;display:flex;align-items:center;gap:10px}" +
        ".sdot{width:10px;height:10px;border-radius:50%;background:#00E5A0;flex-shrink:0}" +
        ".stxt{font-size:13px;color:#AAAACC;flex:1}" +
        ".status.ok .sdot{background:#00E5A0}" +
        ".status.err .sdot{background:#FF4466}" +
        ".status.busy .sdot{background:#FFA726;animation:blink .6s infinite}" +
        "@keyframes blink{0%,100%{opacity:1}50%{opacity:.3}}" +

        // Result
        ".res{background:#141420;border:1.5px solid #2A2A42;border-radius:12px;padding:14px;margin-bottom:12px;min-height:80px;display:flex;align-items:center;justify-content:center}" +
        ".res.lit{border-color:rgba(0,229,160,.5)}" +
        ".empty{color:#333355;font-size:13px;text-align:center}" +
        ".rbody{display:none;width:100%}" +
        ".rtop{display:flex;align-items:center;gap:14px}" +
        ".rltr{font-size:56px;font-weight:900;color:#00E5A0;line-height:1}" +
        ".rans{font-size:15px;font-weight:800;color:#fff;line-height:1.3}" +
        ".rpct{font-size:11px;color:#00E5A0;margin-top:4px}" +

        // Speed
        ".speed-row{display:flex;align-items:center;gap:10px;margin-bottom:12px;background:#141420;border:1.5px solid #2A2A42;border-radius:12px;padding:12px 14px}" +
        ".speed-lbl{font-size:13px;color:#AAAACC;flex:1}" +
        ".speed-val{font-size:13px;font-weight:700;color:#00E5A0;min-width:50px;text-align:right}" +
        "input[type=range]{flex:1;accent-color:#00E5A0}" +

        // Buttons
        ".solve-btn{width:100%;padding:18px;background:linear-gradient(135deg,#00E5A0,#00B87A);color:#000;border:none;border-radius:14px;font-size:16px;font-weight:900;cursor:pointer;margin-bottom:8px;letter-spacing:.3px}" +
        ".solve-btn:active{transform:scale(.98)}" +
        ".solve-btn:disabled{background:#1C1C2E;color:#444}" +
        ".steps{background:#141420;border:1.5px solid #2A2A42;border-radius:12px;padding:12px 14px}" +
        ".step{font-size:12px;color:#AAAACC;line-height:2;display:flex;gap:8px;align-items:center}" +
        ".step b{color:#00E5A0}" +
        "</style></head><body>" +

        "<div class='bar'></div>" +
        "<div class='hdr'><div class='htit'>Quiz<b>AI</b> Auto</div><div class='xbtn' onclick='Android.close()'>X</div></div>" +

        "<div class='scr'>" +

        // Accessibility Warning
        "<div class='acc-warn' id='accWarn'>" +
        "<div class='acc-title'>⚠️ Accessibility Permission fehlt!</div>" +
        "<div class='acc-txt'>Damit QuizAI automatisch Screenshots macht und Antworten klickt, musst du einmalig die Accessibility Permission geben:\n\n" +
        "1. Tippe den Button unten\n" +
        "2. Suche 'QuizAI Auto-Solver'\n" +
        "3. Schalter aktivieren</div>" +
        "<button class='acc-btn' onclick='Android.openAccessibilitySettings()'>Accessibility aktivieren →</button>" +
        "</div>" +

        // Status
        "<div class='status ok' id='statusBox'>" +
        "<div class='sdot' id='sdot'></div>" +
        "<div class='stxt' id='stxt'>Bereit — tippe START zum automatischen Loesen</div>" +
        "</div>" +

        // Result
        "<div class='res' id='res'>" +
        "<div class='empty' id='emp'>KI Antwort erscheint hier...</div>" +
        "<div class='rbody' id='rb'>" +
        "<div class='rtop'>" +
        "<div class='rltr' id='rl'>A</div>" +
        "<div><div class='rans' id='ra'>-</div><div class='rpct' id='rp'>-</div></div>" +
        "</div></div></div>" +

        // Speed slider
        "<div class='speed-row'>" +
        "<div class='speed-lbl'>Geschwindigkeit</div>" +
        "<input type='range' min='500' max='5000' value='2000' step='500' id='spd' oninput='updateSpeed(this.value)'>" +
        "<div class='speed-val' id='spdVal'>2.0s</div>" +
        "</div>" +

        // Start button
        "<button class='solve-btn' id='solveBtn' onclick='startSolve()'>AUTO LOESEN</button>" +

        // Steps
        "<div class='steps'>" +
        "<div class='step'><b>1</b> Oeffne die Quiz-App</div>" +
        "<div class='step'><b>2</b> Frage auf dem Bildschirm zeigen</div>" +
        "<div class='step'><b>3</b> AI Button tippen</div>" +
        "<div class='step'><b>4</b> AUTO LOESEN tippen</div>" +
        "<div class='step'><b>5</b> KI macht alles automatisch!</div>" +
        "</div>" +

        "</div>" + // /scr

        "<script>" +
        "var speed=2000;" +

        // Check accessibility on load
        "window.onload=function(){" +
        "  if(!Android.isAccessibilityOn()){" +
        "    document.getElementById('accWarn').classList.add('show');" +
        "    document.getElementById('solveBtn').disabled=true;" +
        "    document.getElementById('stxt').textContent='Accessibility Permission fehlt!';" +
        "    document.getElementById('statusBox').className='status err';" +
        "  }" +
        "};" +

        "function updateSpeed(v){" +
        "  speed=parseInt(v);" +
        "  document.getElementById('spdVal').textContent=(speed/1000).toFixed(1)+'s';" +
        "  Android.setSpeed(speed);" +
        "}" +

        "function startSolve(){" +
        "  document.getElementById('stxt').textContent='Screenshot wird gemacht...';" +
        "  document.getElementById('statusBox').className='status busy';" +
        "  document.getElementById('solveBtn').disabled=true;" +
        "  Android.solve(speed);" +
        "  setTimeout(function(){" +
        "    document.getElementById('solveBtn').disabled=false;" +
        "    document.getElementById('statusBox').className='status ok';" +
        "    document.getElementById('stxt').textContent='Bereit fuer naechste Frage';" +
        "  }, speed+3000);" +
        "}" +

        "function updateResult(letter,answer,conf){" +
        "  document.getElementById('emp').style.display='none';" +
        "  document.getElementById('rb').style.display='block';" +
        "  document.getElementById('res').classList.add('lit');" +
        "  document.getElementById('rl').textContent=letter;" +
        "  document.getElementById('ra').textContent=answer;" +
        "  document.getElementById('rp').textContent=conf+'% sicher';" +
        "}" +
        "</script></body></html>";
    }

    // ── STATIC HELPERS ────────────────────────────────────────

    public static void showToastStatic(String msg) {
        if (instance == null) return;
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(instance, msg, Toast.LENGTH_SHORT).show()
        );
    }

    public static void updateResult(String letter, String answer, int conf) {
        if (instance == null || instance.panel == null) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            if (instance.panel instanceof WebView) {
                ((WebView) instance.panel).evaluateJavascript(
                    "updateResult('" + letter + "','" + answer.replace("'", "\\'") + "'," + conf + ")", null
                );
            }
            instance.setBubble("AI", "#00E5A0");
        });
    }

    private void styleBubble(TextView tv, String color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor(color));
        tv.setBackground(bg);
    }

    private void setBubble(String text, String color) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (bubble instanceof TextView) {
                ((TextView) bubble).setText(text);
                styleBubble((TextView) bubble, color);
            }
        });
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private int getScreenW() { return getResources().getDisplayMetrics().widthPixels; }
    private int getScreenH() { return getResources().getDisplayMetrics().heightPixels; }
}
