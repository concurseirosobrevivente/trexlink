package br.francisco.trexlink;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Tela unica so para conceder as permissoes (usada uma vez). */
public class MainActivity extends Activity {
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Bridge.start(this);

        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        l.setPadding(pad, pad * 2, pad, pad);

        TextView titulo = new TextView(this);
        titulo.setText("TRexLink");
        titulo.setTextSize(26);
        titulo.setGravity(Gravity.CENTER);
        l.addView(titulo);

        status = new TextView(this);
        status.setTextSize(17);
        status.setPadding(0, pad, 0, pad);
        l.addView(status);

        botao(l, "1. Acesso a notificacoes", new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        botao(l, "2. Acessibilidade", new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        botao(l, "3. Nao Perturbe", new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        Intent bat = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName()));
        botao(l, "4. Bateria sem restricao", bat);

        Button teste = new Button(this);
        teste.setText("Testar lanterna");
        teste.setOnClickListener(v -> new Thread(() -> {
            String r = Comandos.executar("LANTERNA");
            runOnUiThread(() -> status.setText(status.getText() + "\n\nTeste: " + r));
        }).start());
        l.addView(teste);

        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
    }

    private void botao(LinearLayout l, String texto, Intent i) {
        Button btn = new Button(this);
        btn.setText(texto);
        btn.setOnClickListener(v -> {
            try { startActivity(i); } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        l.addView(btn);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String pkg = getPackageName();
        String listeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean notif = listeners != null && listeners.contains(new ComponentName(this, NotifService.class).flattenToString());
        String acess = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean acc = acess != null && acess.contains(pkg);
        NotificationManager nm = getSystemService(NotificationManager.class);
        boolean dnd = nm.isNotificationPolicyAccessGranted();
        PowerManager pm = getSystemService(PowerManager.class);
        boolean bat = pm.isIgnoringBatteryOptimizations(pkg);

        status.setText(
                linha("Notificacoes", notif) + "\n"
                + linha("Acessibilidade", acc) + "\n"
                + linha("Nao Perturbe", dnd) + "\n"
                + linha("Bateria sem restricao", bat) + "\n"
                + linha("Ponte com o relogio (porta " + Bridge.PORTA + ")", Bridge.rodando()));
        status.setTextColor(notif && acc && dnd && bat ? Color.rgb(46, 125, 50) : Color.rgb(198, 40, 40));
    }

    private static String linha(String nome, boolean ok) {
        return (ok ? "OK  " : "FALTA  ") + nome;
    }
}
