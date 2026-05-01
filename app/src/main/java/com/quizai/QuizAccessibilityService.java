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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;

public class QuizAccessibilityService extends AccessibilityService {

    private static final String TAG = "QuizAI";
    private static final String API_KEY = "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";

    public static QuizAccessibilityService instance;
    private static boolean analyzing = false;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        OverlayService.showToastStatic("QuizAI Service aktiv!");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}
    @Override public void onDestroy() { super.onDestroy(); instance = null; }

    public void solveQuiz(int speedMs) {
        if (analyzing) {
            OverlayService.showToastStatic("Bereits am Arbeiten...");
            return;
        }
        analyzing = true;
        OverlayService.showToastStatic("Schritt 1: Lese Bildschirm...");

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                String screenText = getAllText();

                if (screenText.isEmpty()) {
                    OverlayService.showToastStatic("FEHLER: Kein Text auf Bildschirm!");
                    analyzing = false;
                    return;
                }

                OverlayService.showToastStatic("Schritt 2: Sende an KI... (" + screenText.length() + " Zeichen)");
                Executors.newSingleThreadExecutor().execute(() -> sendToAI(screenText, speedMs));

            } catch (Exception e) {
                OverlayService.showToastStatic("FEHLER: " + e.getMessage());
                analyzing = false;
            }
        });
    }

    private String getAllText() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "";
        StringBuilder sb = new StringBuilder();
        collectText(root, sb);
        return sb.toString().trim();
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

    private void sendToAI(String screenText, int speedMs) {
        try {
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content",
                "Quiz auf Handy. Welche Antwort ist richtig?\n\n" +
                "TEXT:\n" + screenText + "\n\n" +
                "Nur JSON: {\"answer\":\"Antworttext\",\"confidence\":90}"
            );

            JSONArray messages = new JSONArray();
            messages.put(message);

            JSONObject body = new JSONObject();
            body.put("model", "gpt-4o");
            body.put("max_tokens", 100);
            body.put("messages", messages);

            URL url = new URL("https://api.openai.com/v1/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
            conn.setDoOutput(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(30000);

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            os.close();

            int responseCode = conn.getResponseCode();

            if (responseCode != 200) {
                // Fehler lesen
                BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
                StringBuilder errSb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) errSb.append(line);
                OverlayService.showToastStatic("API Fehler " + responseCode + ": " + errSb.toString().substring(0, Math.min(100, errSb.length())));
                analyzing = false;
                return;
            }

            BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder respSb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) respSb.append(line);
            String respStr = respSb.toString();

            JSONObject root = new JSONObject(respStr);
            String text = root.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
            text = text.replace("```json","").replace("```","").trim();

            JSONObject result = new JSONObject(text);
            String answer = result.getString("answer");
            int conf = result.optInt("confidence", 0);

            OverlayService.showToastStatic("Schritt 3: Antwort = " + answer);
            OverlayService.updateResult("✓", answer, conf);

            new Handler(Looper.getMainLooper()).postDelayed(() ->
                clickAnswer(answer, speedMs), 500);

        } catch (Exception e) {
            OverlayService.showToastStatic("FEHLER: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            Log.e(TAG, "Fehler", e);
            analyzing = false;
        }
    }

    private void clickAnswer(String answer, int speedMs) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                boolean clicked = findAndClick(root, answer);
                if (clicked) {
                    OverlayService.showToastStatic("Schritt 4: Geklickt!");
                    new Handler(Looper.getMainLooper()).postDelayed(this::clickNext, speedMs);
                } else {
                    OverlayService.showToastStatic("Antwort nicht klickbar gefunden!");
                }
            }
        } catch (Exception e) {
            OverlayService.showToastStatic("Klick Fehler: " + e.getMessage());
        } finally {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                analyzing = false;
                OverlayService.showToastStatic("Fertig! Bereit fuer naechste Frage.");
            }, speedMs + 2000);
        }
    }

    private boolean findAndClick(AccessibilityNodeInfo node, String answer) {
        if (node == null) return false;
        CharSequence t = node.getText();
        if (t != null) {
            String nodeText = t.toString().toLowerCase().trim();
            String answerLow = answer.toLowerCase().trim();
            int len = Math.min(8, Math.min(nodeText.length(), answerLow.length()));
            if (len > 0 && (nodeText.contains(answerLow.substring(0, len)) ||
                answerLow.contains(nodeText.substring(0, len)))) {
                if (node.isClickable()) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return true;
                }
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
        String[] nextWords = {"Weiter","Next","OK","Aufloesung","Auflösung","Fortfahren","Continue","Fertig","→"};
        for (String w : nextWords) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(w);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo n : nodes) {
                    if (n.isClickable()) {
                        n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        OverlayService.showToastStatic("Schritt 5: Weiter!");
                        return;
                    }
                }
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
