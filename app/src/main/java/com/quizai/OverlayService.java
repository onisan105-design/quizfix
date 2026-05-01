package com.quizai;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import androidx.core.app.NotificationCompat;

public class OverlayService extends Service {

    private static final String CH = "quizai";
    private WindowManager wm;
    private View bubble;
    private View panel;
    private WindowManager.LayoutParams bubbleLP;
    private boolean panelOpen = false;
    private boolean analyzing = false;

    @Override
    public void onCreate() {
        super.onCreate();
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
        if (bubble != null) try { wm.removeView(bubble); } catch (Exception e) {}
        if (panel  != null) try { wm.removeView(panel);  } catch (Exception e) {}
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(CH, "QuizAI", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("QuizAI laeuft im Hintergrund");
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
    }

    private Notification buildNotif() {
        Intent stop = new Intent(this, OverlayService.class);
        stop.setAction("STOP");
        PendingIntent stopPI = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CH)
            .setContentTitle("QuizAI aktiv")
            .setContentText("Gruener Button ist auf deinem Bildschirm")
            .setSmallIcon(android.R.drawable.ic_menu_help)
            .addAction(android.R.drawable.ic_delete, "Beenden", stopPI)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void createBubble() {
        TextView tv = new TextView(this);
        tv.setText("AI");
        tv.setTextSize(18);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(Color.BLACK);
        tv.setGravity(Gravity.CENTER);
        styleBubble(tv, "#00E5A0");
        tv.setElevation(dp(10));
        bubble = tv;

        bubbleLP = new WindowManager.LayoutParams(
            dp(62), dp(62),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        bubbleLP.gravity = Gravity.TOP | Gravity.START;
        bubbleLP.x = getScreenW() - dp(78);
        bubbleLP.y = dp(320);

        tv.setOnTouchListener(new DragTap());
        wm.addView(bubble, bubbleLP);
    }

    private class DragTap implements View.OnTouchListener {
        float sx, sy; int ox, oy; boolean moved;
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    moved = false; sx = e.getRawX(); sy = e.getRawY();
                    ox = bubbleLP.x; oy = bubbleLP.y; return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX()-sx, dy = e.getRawY()-sy;
                    if (Math.abs(dx)>8||Math.abs(dy)>8) moved=true;
                    if (moved) {
                        bubbleLP.x = Math.max(0, Math.min(getScreenW()-dp(62), (int)(ox+dx)));
                        bubbleLP.y = Math.max(0, Math.min(getScreenH()-dp(62), (int)(oy+dy)));
                        wm.updateViewLayout(bubble, bubbleLP);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved) onTap(); return true;
            }
            return false;
        }
    }

    private void onTap() {
        if (analyzing) { toast("KI analysiert noch..."); return; }
        if (panelOpen) closePanel(); else openPanel();
    }

    private void openPanel() {
        if (panelOpen) return;
        WebView wv = new WebView(this);
        wv.getSettings().setJavaScriptEnabled(true);
        wv.getSettings().setAllowFileAccess(true);
        wv.getSettings().setDomStorageEnabled(true);
        wv.setWebViewClient(new WebViewClient());
        wv.addJavascriptInterface(new Bridge(), "Android");

        DisplayMetrics dm = getResources().getDisplayMetrics();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (int)(dm.heightPixels * 0.70f),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
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

    public class Bridge {
        @JavascriptInterface
        public void close() {
            new Handler(Looper.getMainLooper()).post(() -> closePanel());
        }

        @JavascriptInterface
        public void setAnalyzing(boolean b) {
            analyzing = b;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (b) setBubble("...", "#FFA726");
                else if (panelOpen) setBubble("X", "#FF4466");
                else setBubble("AI", "#00E5A0");
            });
        }

        @JavascriptInterface
        public void vibrate() {
            Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vib != null) vib.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));
        }

        @JavascriptInterface
        public void showToast(String msg) {
            new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(OverlayService.this, msg, Toast.LENGTH_SHORT).show()
            );
        }
    }

    private String buildHTML() {
        return "<!DOCTYPE html><html><head>" +
        "<meta charset='UTF-8'>" +
        "<meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=no'>" +
        "<style>" +
        "*{margin:0;padding:0;box-sizing:border-box;font-family:system-ui,sans-serif}" +
        "body{background:#0F0F1A;color:#EEEEFF;border-radius:22px 22px 0 0;border-top:2px solid #00E5A0;height:100vh;display:flex;flex-direction:column;overflow:hidden}" +
        ".bar{width:38px;height:4px;background:#2A2A42;border-radius:2px;margin:10px auto 0;flex-shrink:0}" +
        ".hdr{display:flex;align-items:center;justify-content:space-between;padding:8px 16px 10px;border-bottom:1px solid #1C1C2E;flex-shrink:0}" +
        ".htit{font-size:17px;font-weight:800;color:#fff}" +
        ".htit b{color:#00E5A0}" +
        ".xbtn{width:32px;height:32px;background:#1C1C2E;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:15px;cursor:pointer;text-align:center;line-height:32px;color:#fff}" +
        ".scr{flex:1;overflow-y:auto;padding:12px 14px 24px}" +
        ".modes{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-bottom:12px}" +
        ".mc{background:#141420;border:2px solid #2A2A42;border-radius:13px;padding:12px 8px;text-align:center;cursor:pointer}" +
        ".mc.on{border-color:#00E5A0;background:rgba(0,229,160,.07)}" +
        ".mico{font-size:26px;margin-bottom:4px}" +
        ".mname{font-size:13px;font-weight:700;color:#99AACC}" +
        ".mc.on .mname{color:#00E5A0}" +
        ".up{border:2px dashed #2A2A42;border-radius:12px;padding:22px 12px;text-align:center;cursor:pointer;position:relative;display:flex;flex-direction:column;align-items:center;gap:5px;min-height:100px;justify-content:center;margin-bottom:10px}" +
        ".up input{position:absolute;inset:0;opacity:0;width:100%;height:100%;cursor:pointer}" +
        ".uico{font-size:30px}" +
        ".utit{font-size:14px;font-weight:700;color:#fff}" +
        ".usub{font-size:11px;color:#555577;line-height:1.5}" +
        "img.prev{width:100%;max-height:170px;object-fit:contain;border-radius:8px;display:none;background:#000;margin-bottom:10px}" +
        ".res{background:#141420;border:2px solid #2A2A42;border-radius:13px;padding:13px;margin-bottom:12px;min-height:85px;display:flex;align-items:center;justify-content:center;transition:border-color .3s}" +
        ".res.lit{border-color:rgba(0,229,160,.5)}" +
        ".empty{color:#333355;font-size:13px;text-align:center}" +
        ".rbody{display:none;width:100%}" +
        ".rtop{display:flex;align-items:flex-start;gap:10px;margin-bottom:8px}" +
        ".rltr{font-size:54px;font-weight:900;color:#00E5A0;line-height:1;flex-shrink:0}" +
        ".rtag{font-size:9px;color:#00E5A0;text-transform:uppercase;letter-spacing:1.5px;margin-bottom:4px}" +
        ".rans{font-size:15px;font-weight:800;line-height:1.3;margin-bottom:4px;color:#fff}" +
        ".rwhy{font-size:11px;color:#777799;line-height:1.5}" +
        ".cbar{height:4px;background:#1C1C2E;border-radius:2px;overflow:hidden;margin-top:8px}" +
        ".cfil{height:100%;background:linear-gradient(90deg,#00B87A,#00E5A0);border-radius:2px;width:0%;transition:width 1s}" +
        ".cpct{font-size:10px;color:#00E5A0;text-align:right;margin-top:3px}" +
        ".btn{width:100%;padding:15px;background:linear-gradient(135deg,#00E5A0,#00B87A);color:#000;border:none;border-radius:12px;font-size:15px;font-weight:800;cursor:pointer;display:flex;align-items:center;justify-content:center;gap:8px}" +
        ".btn:disabled{background:#1C1C2E;color:#444}" +
        ".spin{display:none;width:18px;height:18px;border:2.5px solid rgba(0,0,0,.2);border-top-color:#000;border-radius:50%;animation:sp .6s linear infinite}" +
        ".btn.ld .spin{display:block}.btn.ld .lbl{display:none}" +
        "@keyframes sp{to{transform:rotate(360deg)}}" +
        "</style></head><body>" +
        "<div class='bar'></div>" +
        "<div class='hdr'><div class='htit'>Quiz<b>AI</b></div><div class='xbtn' onclick='Android.close()'>X</div></div>" +
        "<div class='scr'>" +
        "<div class='modes'>" +
        "<div class='mc on' id='m0' onclick='setM(0)'><div class='mico'>&#128193;</div><div class='mname'>Screenshot</div></div>" +
        "<div class='mc' id='m1' onclick='setM(1)'><div class='mico'>&#128247;</div><div class='mname'>Kamera</div></div>" +
        "</div>" +
        "<div id='p0'><div class='up' id='ua'><input type='file' accept='image/*' onchange='load(event)'>" +
        "<div class='uico'>&#128193;</div><div class='utit'>Screenshot laden</div>" +
        "<div class='usub'>Erst Screenshot machen, dann hier tippen</div></div><img class='prev' id='pi'></div>" +
        "<div id='p1' style='display:none'><div class='up'><input type='file' accept='image/*' capture='environment' onchange='load(event)'>" +
        "<div class='uico'>&#128247;</div><div class='utit'>Kamera oeffnen</div>" +
        "<div class='usub'>Richte sie auf die Quiz-Frage</div></div><img class='prev' id='ci'></div>" +
        "<div class='res' id='res'><div class='empty' id='emp'>Bild laden - Analysieren</div>" +
        "<div class='rbody' id='rb'><div class='rtop'><div class='rltr' id='rl'>A</div>" +
        "<div><div class='rtag'>KI Antwort</div><div class='rans' id='ra'>-</div><div class='rwhy' id='rw'>-</div></div></div>" +
        "<div class='cbar'><div class='cfil' id='cf'></div></div><div class='cpct' id='cp'>-</div></div></div>" +
        "<button class='btn' id='ab' onclick='go()' disabled><div class='spin'></div><span class='lbl'>KI Analyse starten</span></button>" +
        "</div>" +
        "<script>" +
        "var b64=null,mime=null,m=0;" +
        "function setM(n){m=n;" +
        "document.getElementById('m0').classList.toggle('on',n===0);" +
        "document.getElementById('m1').classList.toggle('on',n===1);" +
        "document.getElementById('p0').style.display=n===0?'block':'none';" +
        "document.getElementById('p1').style.display=n===1?'block':'none';}" +
        "function load(e){var f=e.target.files[0];if(!f)return;" +
        "var r=new FileReader();r.onload=function(ev){" +
        "var src=ev.target.result;b64=src.split(',')[1];mime=f.type;" +
        "var img=document.getElementById(m===0?'pi':'ci');" +
        "img.src=src;img.style.display='block';" +
        "if(m===0)document.getElementById('ua').style.display='none';" +
        "document.getElementById('ab').disabled=false;};r.readAsDataURL(f);}" +
        "async function go(){if(!b64)return;" +
        "var btn=document.getElementById('ab');btn.disabled=true;btn.classList.add('ld');" +
        "Android.setAnalyzing(true);" +
        "try{var r=await fetch('https://api.anthropic.com/v1/messages',{" +
        "method:'POST',headers:{'Content-Type':'application/json'}," +
        "body:JSON.stringify({model:'claude-sonnet-4-20250514',max_tokens:500," +
        "messages:[{role:'user',content:[" +
        "{type:'image',source:{type:'base64',media_type:mime,data:b64}}," +
        "{type:'text',text:'Analysiere diesen Quiz-Screenshot. Welche Antwort ist richtig? Antworte NUR als JSON ohne Backticks: {\"letter\":\"A\",\"answer\":\"Antworttext\",\"reason\":\"Begruendung auf Deutsch\",\"confidence\":90}'}" +
        "]}]})});" +
        "var d=await r.json();" +
        "var txt=d.content.map(function(c){return c.text||'';}).join('').replace(/```json|```/g,'').trim();" +
        "var j=JSON.parse(txt);show(j);" +
        "Android.vibrate();Android.showToast('Antwort: '+j.letter+') '+j.answer);" +
        "}catch(e){Android.showToast('Fehler: '+e.message);}" +
        "btn.classList.remove('ld');btn.disabled=false;Android.setAnalyzing(false);}" +
        "function show(j){document.getElementById('emp').style.display='none';" +
        "document.getElementById('rb').style.display='block';" +
        "document.getElementById('res').classList.add('lit');" +
        "document.getElementById('rl').textContent=j.letter||'?';" +
        "document.getElementById('ra').textContent=j.answer||'-';" +
        "document.getElementById('rw').textContent=j.reason||'-';" +
        "var c=Math.min(100,j.confidence||0);" +
        "document.getElementById('cp').textContent=c+'% sicher';" +
        "setTimeout(function(){document.getElementById('cf').style.width=c+'%';},80);}" +
        "</script></body></html>";
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

    private void toast(String msg) {
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        );
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private int getScreenW() { return getResources().getDisplayMetrics().widthPixels; }
    private int getScreenH() { return getResources().getDisplayMetrics().heightPixels; }
}
