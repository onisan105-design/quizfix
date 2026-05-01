package com.quizai;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class QuizAccessibilityService extends AccessibilityService {

    private static final String TAG = "QuizAI";
    private static final String API_KEY = "sk-proj-bF8vouvtKx0Gj42Bh8Dp7-wRr3HxH3nuL-Xuvfp1nhXSJcrxm4uuq46RoL0Kdnt5Gno2Dq7gQwT3BlbkFJ7yclXABWtuVHMJmxx3ZawfnnwJ2wddycsqZtr4PoZB0ih2NO2C9rBkMOdhYICkX15EMFI7XT4Ahttps://api.openai.com/v1/chat/completions";

    public static QuizAccessibilityService instance;
    private static boolean analyzing = false;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Service verbunden");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}
    @Override public void onDestroy() { super.onDestroy(); instance = null; }

    public void solveQuiz(int speedMs) {
        if (analyzing) return;
        analyzing = true;
        OverlayService.showToastStatic("Lese Bildschirm...");

        // Text direkt vom Bildschirm lesen - kein Screenshot nötig!
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                String screenText = getAllText();
                Log.d(TAG, "Bildschirm Text: " + screenText);

                if (screenText.isEmpty()) {
                    OverlayService.showToastStatic("Kein Text gefunden!");
                    analyzing = false;
                    return;
                }

                Executors.newSingleThreadExecutor().execute(() -> sendToAI(screenText, speedMs));

            } catch (Exception e) {
                Log.e(TAG, "Fehler: " + e.getMessage());
                analyzing = false;
            }
        });
    }

    // Liest ALLEN Text vom aktuellen Bildschirm
    private String getAllText() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "";
        StringBuilder sb = new StringBuilder();
        collectText(root, sb);
        return sb.toString();
    }

    private void collectText(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null) return;
        if (node.getText() != null && node.getText().length() > 0) {
            sb.append(node.getText().toString()).append("\n");
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectText(node.getChild(i), sb);
        }
    }

    // Sendet Text an OpenAI
    private void sendToAI(String screenText, int speedMs) {
        try {
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content",
                "Du siehst den Text eines Quiz auf einem Handy-Bildschirm.\n" +
                "Finde die Frage und alle Antwortmöglichkeiten.\n" +
                "Welche Antwort ist richtig?\n\n" +
                "BILDSCHIRM TEXT:\n" + screenText + "\n\n" +
                "Antworte NUR als JSON: {\"answer\":\"Exakter Antworttext\",\"confidence\":90}"
            );

            JSONArray messages = new JSONArray();
            messages.put(message);

            JSONObject body = new JSONObject();
            body.put("model", "gpt-4o");
            body.put("max_tokens", 150);
            body.put("messages", messages);

            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            os.close();

            byte[] resp = conn.getInputStream().readAllBytes();
            String respStr = new String(resp, StandardCharsets.UTF_8);

            JSONObject root = new JSONObject(respStr);
            String text = root.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
            text = text.replace("```json","").replace("```","").trim();

            JSONObject result = new JSONObject(text);
            String answer = result.getString("answer");
            int conf = result.optInt("confidence", 0);

            Log.d(TAG, "Antwort: " + answer);
            OverlayService.showToastStatic("Antwort: " + answer);
            OverlayService.updateResult("✓", answer, conf);

            // Klicke die Antwort
            new Handler(Looper.getMainLooper()).postDelayed(() ->
                clickAnswer(answer, speedMs), 300);

        } catch (Exception e) {
            Log.e(TAG, "AI Fehler: " + e.getMessage());
            OverlayService.showToastStatic("Fehler: " + e.getMessage());
            analyzing = false;
        }
    }

    private void clickAnswer(String answer, int speedMs) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                boolean clicked = findAndClick(root, answer);
                if (clicked) {
                    OverlayService.showToastStatic("✓ Geklickt!");
                    new Handler(Looper.getMainLooper()).postDelayed(this::clickNext, speedMs);
                } else {
                    OverlayService.showToastStatic("Antwort nicht gefunden!");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Klick Fehler: " + e.getMessage());
        } finally {
            new Handler(Looper.getMainLooper()).postDelayed(() -> analyzing = false, speedMs + 2000);
        }
    }

    private boolean findAndClick(AccessibilityNodeInfo node, String answer) {
        if (node == null) return false;
        CharSequence t = node.getText();
        if (t != null) {
            String nodeText = t.toString().toLowerCase().trim();
            String answerLow = answer.toLowerCase().trim();
            // Prüfe ob Text übereinstimmt (auch teilweise)
            if (nodeText.contains(answerLow.substring(0, Math.min(10, answerLow.length()))) ||
                answerLow.contains(nodeText.substring(0, Math.min(10, nodeText.length())))) {
                if (node.isClickable()) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return true;
                }
                // Versuche Elternelement
                AccessibilityNodeInfo parent = node.getParent();
                if (parent != null && parent.isClickable()) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return true;
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (findAndClick(node.getChild(i), answer)) return true;
        }
        return false;
    }

    private void clickNext() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        // Suche nach Weiter-Buttons
        String[] nextWords = {"Weiter", "Next", "OK", "Auflösung", "Fortfahren", "Continue", "→", ">"};
        for (String w : nextWords) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(w);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo n : nodes) {
                    if (n.isClickable()) {
                        n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        OverlayService.showToastStatic("→ Weiter!");
                        return;
                    }
                }
            }
        }
        // Klicke unten auf dem Bildschirm
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
