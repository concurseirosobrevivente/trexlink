package br.francisco.trexlink;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Camera (abrir e disparar), bloqueio de tela e plano B do alarme. */
public class AcessService extends AccessibilityService {
    public static volatile AcessService instancia;

    static final String[] PACOTES_CAMERA = {"com.sec.android.app.camera"};
    private static final String[] IDS_OBTURADOR = {
            "com.sec.android.app.camera:id/shutter_button",
            "com.sec.android.app.camera:id/center_button",
            "com.sec.android.app.camera:id/capture_button"
    };
    private static final String[] TEXTOS_OBTURADOR = {
            "tirar foto", "capturar", "obturador", "disparador", "take picture", "shutter", "capture"
    };

    @Override
    protected void onServiceConnected() {
        instancia = this;
        Bridge.start(this);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instancia = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    public boolean bloquear() {
        return performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    public boolean cameraAberta() {
        return pacoteNaFrente(PACOTES_CAMERA);
    }

    public void abrirCamera() {
        Intent i = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        i.setPackage("com.sec.android.app.camera");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            Intent j = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            j.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(j);
        }
    }

    /** Aperta o botao de foto da camera da Samsung. */
    public String disparar() {
        for (AccessibilityNodeInfo raiz : raizes(PACOTES_CAMERA)) {
            for (String id : IDS_OBTURADOR) {
                List<AccessibilityNodeInfo> nos = raiz.findAccessibilityNodeInfosByViewId(id);
                if (nos != null) for (AccessibilityNodeInfo n : nos) {
                    if (clicar(n)) return "Foto tirada";
                }
            }
            AccessibilityNodeInfo n = procurar(raiz, TEXTOS_OBTURADOR);
            if (n != null && clicar(n)) return "Foto tirada";
        }
        // Plano B: toque na posicao do botao (centro embaixo em retrato, direita no meio em paisagem)
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float x, y;
        if (dm.heightPixels >= dm.widthPixels) {
            x = dm.widthPixels / 2f;
            y = dm.heightPixels * 0.845f;
        } else {
            x = dm.widthPixels * 0.90f;
            y = dm.heightPixels / 2f;
        }
        tocar(x, y);
        return "Foto (toque na tela)";
    }

    /**
     * Despertador da Samsung: o botao (X para dispensar, zZ para soneca) so funciona deslizando.
     * Acha o botao pelo texto e arrasta do centro dele ate perto da borda mais distante.
     * @return "ok" se deslizou, null se nao achou o botao
     */
    public String deslizarAlarme(String[] pacotes, String[] textos) {
        for (AccessibilityNodeInfo raiz : raizes(pacotes)) {
            AccessibilityNodeInfo n = procurar(raiz, textos);
            if (n == null) continue;
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            if (r.width() <= 0 || r.height() <= 0) continue;
            DisplayMetrics dm = getResources().getDisplayMetrics();
            float x = r.exactCenterX();
            float y = r.exactCenterY();
            float destinoX = (x < dm.widthPixels / 2f) ? dm.widthPixels * 0.95f : dm.widthPixels * 0.05f;
            float destinoY = (y > dm.heightPixels / 2f) ? y - dm.heightPixels * 0.15f : y + dm.heightPixels * 0.15f;
            deslizar(x, y, destinoX, destinoY, 450);
            return "ok";
        }
        return null;
    }

    /** Textos visiveis na tela do app indicado (para diagnostico). */
    public String textosNaTela(String[] pacotes) {
        List<String> t = new ArrayList<>();
        for (AccessibilityNodeInfo raiz : raizes(pacotes)) coletar(raiz, t);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.size() && i < 8; i++) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(t.get(i));
        }
        return sb.toString();
    }

    private static void coletar(AccessibilityNodeInfo n, List<String> t) {
        if (n == null || t.size() >= 8) return;
        CharSequence s = n.getText() != null ? n.getText() : n.getContentDescription();
        if (s != null && s.length() > 0 && s.length() < 30) t.add(s.toString());
        for (int i = 0; i < n.getChildCount(); i++) coletar(n.getChild(i), t);
    }

    private void deslizar(float x1, float y1, float x2, float y2, long ms) {
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, ms))
                .build();
        dispatchGesture(g, null, null);
    }

    // ---------- utilitarios ----------

    private boolean pacoteNaFrente(String[] pacotes) {
        AccessibilityNodeInfo r = getRootInActiveWindow();
        return r != null && r.getPackageName() != null && contem(pacotes, r.getPackageName().toString());
    }

    private List<AccessibilityNodeInfo> raizes(String[] pacotes) {
        List<AccessibilityNodeInfo> lista = new ArrayList<>();
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r != null && r.getPackageName() != null && contem(pacotes, r.getPackageName().toString())) {
                    lista.add(r);
                }
            }
        } catch (Exception ignored) {}
        AccessibilityNodeInfo ativa = getRootInActiveWindow();
        if (lista.isEmpty() && ativa != null && ativa.getPackageName() != null
                && contem(pacotes, ativa.getPackageName().toString())) {
            lista.add(ativa);
        }
        return lista;
    }

    private static boolean contem(String[] arr, String v) {
        for (String s : arr) if (s.equals(v)) return true;
        return false;
    }

    private static AccessibilityNodeInfo procurar(AccessibilityNodeInfo n, String[] textos) {
        if (n == null) return null;
        String t = "";
        if (n.getText() != null) t += n.getText().toString();
        if (n.getContentDescription() != null) t += " " + n.getContentDescription().toString();
        t = t.toLowerCase(Locale.ROOT);
        if (!t.isEmpty()) {
            for (String chave : textos) if (t.contains(chave)) return n;
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo achado = procurar(n.getChild(i), textos);
            if (achado != null) return achado;
        }
        return null;
    }

    private boolean clicar(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo atual = n;
        for (int i = 0; i < 5 && atual != null; i++) {
            if (atual.isClickable() && atual.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            atual = atual.getParent();
        }
        Rect r = new Rect();
        n.getBoundsInScreen(r);
        if (r.width() > 0 && r.height() > 0) {
            tocar(r.exactCenterX(), r.exactCenterY());
            return true;
        }
        return false;
    }

    private void tocar(float x, float y) {
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 60))
                .build();
        dispatchGesture(g, null, null);
    }
}
