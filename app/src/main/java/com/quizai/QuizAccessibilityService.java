package com.quizai;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.Log;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;

public class QuizAccessibilityService extends AccessibilityService {

    private static final String TAG = "QuizAI";
    public static QuizAccessibilityService instance;
    private static boolean analyzing = false;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Accessibility Service verbunden");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    public void solveQuiz(int speedMs) {
        if (analyzing) return;
        analyzing = true;

        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            Executors.newSingleThreadExecutor(),
            new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult result) {
                    Bitmap bitmap = Bitmap.wrapHardwareBuffer(
                        result.getHardwareBuffer(),
                        result.getColorSpace()
                    );
                    if (bitmap == null) {
                        OverlayService.showToastStatic("Screenshot leer!");
                        analyzing = false;
                        return;
                    }
                    Bitmap soft = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                    new Thread(() -> sendToAI(soft, speedMs)).start();
                }

                @Override
                public void onFailure(int errorCode) {
                    Log.e(TAG, "Screenshot Fehler: " + errorCode);
                    OverlayService.showToastStatic("Screenshot Fehler: " + errorCode);
                    analyzing = false;
                }
            }
        );
    }

    private void sendToAI(Bitmap bitmap, int speedMs) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

            String json = "{\"model\":\"claude-sonnet-4-20250514\",\"max_tokens\":200," +
                "\"messages\":[{\"role\":\"user\",\"content\":[" +
                "{\"type\":\"image\",\"source\":{\"type\":\"base64\",\"media_type\":\"image/jpeg\",\"data\":\"" + b64 + "\"}}," +
                "{\"type\":\"text\",\"text\":\"Quiz-Screenshot: Welche Antwort ist richtig? Nur JSON: {\\\"letter\\\":\\\"A\\\",\\\"answer\\\":\\\"Text\\\",\\\"confidence\\\":90}\"}" +
                "]}]}";

            URL url = new URL("https://api.anthropic.com/v1/messages");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);

            OutputStream os = conn.getOutputStream();
            os.write(json.getBytes(StandardCharsets.UTF_8));
            os.close();

            byte[] resp = conn.getInputStream().readAllBytes();
            String respStr = new String(resp, StandardCharsets.UTF_8);

            JSONObject root = new JSONObject(respStr);
            String text = root.getJSONArray("content").getJSONObject(0).getString("text");
            text = text.replace("```json","").replace("```","").trim();
            JSONObject result = new JSONObject(text);

            String letter = result.getString("letter");
            String answer = result.getString("answer");
            int conf = result.optInt("confidence", 0);

            OverlayService.showToastStatic("KI: " + letter + ") " + answer);
            OverlayService.updateResult(letter, answer, conf);

            new Handler(Looper.getMainLooper()).postDelayed(() ->
                clickAnswer(letter, answer, speedMs), 500);

        } catch (Exception e) {
            Log.e(TAG, "AI Fehler: " + e.getMessage());
            OverlayService.showToastStatic("Fehler: " + e.getMessage());
            analyzing = false;
        }
    }

    private void clickAnswer(String letter, String answer, int speedMs) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            boolean clicked = false;
            if (root != null) clicked = findAndClick(root, answer);
            if (!clicked) {
                int pos = "ABCD".indexOf(letter.toUpperCase());
                if (pos >= 0) clickByPosition(pos);
            }
            OverlayService.showToastStatic("Geklickt: " + letter);
            new Handler(Looper.getMainLooper()).postDelayed(this::clickNext, speedMs);
        } catch (Exception e) {
            Log.e(TAG, "Klick Fehler: " + e.getMessage());
        } finally {
            new Handler(Looper.getMainLooper()).postDelayed(() -> analyzing = false, speedMs + 2000);
        }
    }

    private boolean findAndClick(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        CharSequence t = node.getText();
        int len = Math.min(8, text.length());
        if (t != null && t.toString().toLowerCase().contains(text.toLowerCase().substring(0, len))) {
            if (node.isClickable()) { node.performAction(AccessibilityNodeInfo.ACTION_CLICK); return true; }
            AccessibilityNodeInfo p = node.getParent();
            if (p != null && p.isClickable()) { p.performAction(AccessibilityNodeInfo.ACTION_CLICK); return true; }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (findAndClick(node.getChild(i), text)) return true;
        }
        return false;
    }

    private void clickByPosition(int pos) {
        float h = getResources().getDisplayMetrics().heightPixels;
        float w = getResources().getDisplayMetrics().widthPixels;
        performClick(w * 0.5f, h * 0.45f + pos * (h * 0.11f));
    }

    private void clickNext() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        String[] words = {"Weiter","Next","OK","Fortfahren","Continue","Fertig"};
        for (String w : words) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(w);
            if (nodes != null && !nodes.isEmpty() && nodes.get(0).isClickable()) {
                nodes.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return;
            }
        }
        float h = getResources().getDisplayMetrics().heightPixels;
        float w = getResources().getDisplayMetrics().widthPixels;
        performClick(w * 0.5f, h * 0.88f);
    }

    private void performClick(float x, float y) {
        Path p = new Path();
        p.moveTo(x, y);
        dispatchGesture(new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(p, 0, 100))
            .build(), null, null);
    }

    public static boolean isRunning() { return instance != null; }

    public static void triggerSolve(int speedMs) {
        if (instance != null)
            new Handler(Looper.getMainLooper()).post(() -> instance.solveQuiz(speedMs));
    }
}
