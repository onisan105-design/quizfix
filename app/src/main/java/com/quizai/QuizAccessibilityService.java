package com.quizai;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class QuizAccessibilityService extends AccessibilityService {

    private static final String TAG = "QuizAI";
    public static QuizAccessibilityService instance;
    private static boolean analyzing = false;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Accessibility Service verbunden ✓");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    // ── HAUPTFUNKTION: Screenshot → KI → Auto-Klick ──────────

    public void solveQuiz(int speedMs) {
        if (analyzing) return;
        analyzing = true;

        // Screenshot machen
        takeScreenshot(new TakeScreenshotCallback() {
            @Override
            public void onSuccess(Bitmap bitmap) {
                Log.d(TAG, "Screenshot gemacht: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                // An KI senden
                new Thread(() -> sendToAI(bitmap, speedMs)).start();
            }

            @Override
            public void onFailure(int errorCode) {
                Log.e(TAG, "Screenshot Fehler: " + errorCode);
                analyzing = false;
                OverlayService.showToastStatic("Screenshot fehlgeschlagen!");
            }
        }, null);
    }

    // ── KI API AUFRUFEN ───────────────────────────────────────

    private void sendToAI(Bitmap bitmap, int speedMs) {
        try {
            // Bitmap zu Base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
            String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

            // JSON bauen
            String json = "{\"model\":\"claude-sonnet-4-20250514\",\"max_tokens\":300," +
                "\"messages\":[{\"role\":\"user\",\"content\":[" +
                "{\"type\":\"image\",\"source\":{\"type\":\"base64\",\"media_type\":\"image/jpeg\",\"data\":\"" + b64 + "\"}}," +
                "{\"type\":\"text\",\"text\":\"Analysiere diesen Quiz-Screenshot. Welche Antwort ist richtig? Antworte NUR als JSON: {\\\"letter\\\":\\\"A\\\",\\\"answer\\\":\\\"Antworttext\\\",\\\"confidence\\\":90}\"}" +
                "]}]}";

            // API aufrufen
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

            // Antwort lesen
            byte[] response = conn.getInputStream().readAllBytes();
            String responseStr = new String(response, StandardCharsets.UTF_8);
            Log.d(TAG, "API Antwort: " + responseStr);

            // JSON parsen
            JSONObject root = new JSONObject(responseStr);
            String text = root.getJSONArray("content").getJSONObject(0).getString("text");
            text = text.replace("```json", "").replace("```", "").trim();
            JSONObject result = new JSONObject(text);

            String letter = result.getString("letter");
            String answer = result.getString("answer");
            int confidence = result.optInt("confidence", 0);

            Log.d(TAG, "Antwort: " + letter + ") " + answer + " (" + confidence + "%)");
            OverlayService.showToastStatic("KI: " + letter + ") " + answer + " (" + confidence + "%)");
            OverlayService.updateResult(letter, answer, confidence);

            // Auto-Klick nach Verzögerung
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                clickAnswer(letter, answer, speedMs);
            }, 500);

        } catch (Exception e) {
            Log.e(TAG, "AI Fehler: " + e.getMessage());
            OverlayService.showToastStatic("Fehler: " + e.getMessage());
            analyzing = false;
        }
    }

    // ── ANTWORT ANKLICKEN ─────────────────────────────────────

    private void clickAnswer(String letter, String answer, int speedMs) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                Log.e(TAG, "Kein aktives Fenster!");
                analyzing = false;
                return;
            }

            // Versuche den Antwort-Text zu finden und zu klicken
            boolean clicked = findAndClick(root, answer);

            if (!clicked) {
                // Falls nicht gefunden: klicke basierend auf Position (A=1., B=2., etc.)
                int position = "ABCD".indexOf(letter);
                if (position >= 0) {
                    clickByPosition(position);
                    clicked = true;
                }
            }

            if (clicked) {
                OverlayService.showToastStatic("✓ Geklickt: " + letter);
                // Nach Verzögerung auf "Weiter" klicken
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    clickNextButton(speedMs);
                }, speedMs);
            }

        } catch (Exception e) {
            Log.e(TAG, "Klick Fehler: " + e.getMessage());
        } finally {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                analyzing = false;
            }, speedMs + 2000);
        }
    }

    // ── TEXT SUCHEN UND KLICKEN ───────────────────────────────

    private boolean findAndClick(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;

        CharSequence nodeText = node.getText();
        if (nodeText != null && nodeText.toString().toLowerCase().contains(text.toLowerCase().substring(0, Math.min(10, text.length())))) {
            if (node.isClickable()) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return true;
            }
            // Elternelement klicken
            AccessibilityNodeInfo parent = node.getParent();
            if (parent != null && parent.isClickable()) {
                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return true;
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            if (findAndClick(node.getChild(i), text)) return true;
        }
        return false;
    }

    // ── NACH POSITION KLICKEN (A=oben, D=unten) ───────────────

    private void clickByPosition(int position) {
        // Bildschirm in 4 Teile aufteilen für A,B,C,D
        float screenH = getResources().getDisplayMetrics().heightPixels;
        float screenW = getResources().getDisplayMetrics().widthPixels;

        // Antworten befinden sich meist in der unteren Hälfte
        float startY = screenH * 0.45f;
        float stepY = screenH * 0.12f;
        float clickY = startY + (position * stepY);
        float clickX = screenW * 0.5f;

        performClick(clickX, clickY);
        Log.d(TAG, "Klick bei Position " + position + ": x=" + clickX + " y=" + clickY);
    }

    // ── WEITER-BUTTON KLICKEN ─────────────────────────────────

    private void clickNextButton(int speedMs) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        // Suche nach typischen "Weiter" Texten
        String[] nextTexts = {"Weiter", "Next", "Nächste", "OK", "Bestätigen", "Fortfahren", "Continue", "Fertig"};

        for (String nextText : nextTexts) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(nextText);
            if (nodes != null && !nodes.isEmpty()) {
                AccessibilityNodeInfo node = nodes.get(0);
                if (node.isClickable()) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    OverlayService.showToastStatic("→ Weiter geklickt");
                    return;
                }
            }
        }

        // Falls kein "Weiter" gefunden: klicke unten auf dem Bildschirm
        float screenH = getResources().getDisplayMetrics().heightPixels;
        float screenW = getResources().getDisplayMetrics().widthPixels;
        performClick(screenW * 0.5f, screenH * 0.88f);
        Log.d(TAG, "Weiter: Klick unten auf Bildschirm");
    }

    // ── GESTE AUSFÜHREN ───────────────────────────────────────

    private void performClick(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path, 0, 100);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(stroke)
            .build();
        dispatchGesture(gesture, null, null);
    }

    // ── STATIC HELPER ─────────────────────────────────────────

    public static boolean isRunning() {
        return instance != null;
    }

    public static void triggerSolve(int speedMs) {
        if (instance != null) {
            new Handler(Looper.getMainLooper()).post(() -> instance.solveQuiz(speedMs));
        }
    }
}
