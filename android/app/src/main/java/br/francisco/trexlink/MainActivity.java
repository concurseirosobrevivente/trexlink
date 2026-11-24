package br.francisco.trexlink;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Assistente de configuracao: usado uma vez. Mostra as etapas em ordem, destaca a proxima
 * pendente e, quando tudo estiver pronto, diz que o app pode ser fechado.
 */
public class MainActivity extends Activity {
    static final String PACOTE_ZEPP = "com.huami.watch.hmwatchmanager";

    private static final int VERDE = Color.rgb(46, 125, 50);
    private static final int AZUL = Color.rgb(25, 103, 210);
    private static final int CINZA = Color.rgb(110, 110, 110);
    private static final int FUNDO = Color.rgb(245, 246, 248);

    private LinearLayout lista;
    private SharedPreferences prefs;
    private int dp;

    /** Uma etapa do assistente. */
    private static final class Etapa {
        final String titulo, explicacao, botao;
        final Runnable acao;
        final boolean feita;
        final String chaveManual; // != null: etapa sem verificacao automatica (usuario marca "Ja fiz")
        final boolean mostrarRestrita;

        Etapa(String titulo, String explicacao, String botao, Runnable acao, boolean feita,
              String chaveManual, boolean mostrarRestrita) {
            this.titulo = titulo;
            this.explicacao = explicacao;
            this.botao = botao;
            this.acao = acao;
            this.feita = feita;
            this.chaveManual = chaveManual;
            this.mostrarRestrita = mostrarRestrita;
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Bridge.start(this);
        prefs = getSharedPreferences("assistente", MODE_PRIVATE);
        dp = (int) getResources().getDisplayMetrics().density;

        lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        lista.setPadding(16 * dp, 28 * dp, 16 * dp, 28 * dp);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(FUNDO);
        sv.addView(lista);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        montar();
    }

    // ---------------------------------------------------------------- etapas

    private List<Etapa> etapas() {
        String pkg = getPackageName();
        List<Etapa> e = new ArrayList<>();

        boolean notifPost = Build.VERSION.SDK_INT < 33
                || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
        e.add(new Etapa("Avisos do TRexLink",
                "Permite que o TRexLink mostre o aviso \"Despertador tocando\", que o Zepp repassa ao relógio.",
                "Permitir",
                () -> {
                    if (prefs.getBoolean("pediu_notif", false)) {
                        // Depois de uma recusa o Android pode nao mostrar o pedido de novo: abre a configuracao.
                        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        i.putExtra(Settings.EXTRA_APP_PACKAGE, pkg);
                        abrir(i);
                    } else {
                        prefs.edit().putBoolean("pediu_notif", true).apply();
                        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
                    }
                },
                notifPost, null, false));

        String listeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean leitor = listeners != null
                && listeners.contains(new ComponentName(this, NotifService.class).flattenToString());
        e.add(new Etapa("Acesso às notificações",
                "Usado para parar e adiar o despertador. Na lista, ative \"TRexLink\".",
                "Abrir configuração",
                () -> abrir(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)),
                leitor, null, true));

        String acess = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean acc = acess != null && acess.contains(pkg);
        e.add(new Etapa("Acessibilidade",
                "Usada para disparar a câmera, bloquear a tela e deslizar o botão do despertador. "
                        + "Em Acessibilidade, procure \"TRexLink\" (pode estar em Aplicativos instalados) e ative.",
                "Abrir configuração",
                () -> abrir(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
                acc, null, true));

        NotificationManager nm = getSystemService(NotificationManager.class);
        e.add(new Etapa("Não Perturbe",
                "Permite ligar e desligar o Não Perturbe do celular pelo relógio. Na lista, ative \"TRexLink\".",
                "Abrir configuração",
                () -> abrir(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)),
                nm.isNotificationPolicyAccessGranted(), null, false));

        PowerManager pm = getSystemService(PowerManager.class);
        e.add(new Etapa("Bateria do TRexLink",
                "Evita que o Android feche o TRexLink em segundo plano.",
                "Permitir",
                () -> abrir(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + pkg))),
                pm.isIgnoringBatteryOptimizations(pkg), null, false));

        e.add(new Etapa("Bateria do Zepp",
                "Se o Zepp for fechado em segundo plano, o relógio desconecta. Na tela que abrir, "
                        + "toque em Bateria e escolha \"Sem restrições\".",
                "Abrir o Zepp nas configurações",
                () -> abrir(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + PACOTE_ZEPP))),
                prefs.getBoolean("zepp_bateria", false), "zepp_bateria", false));

        e.add(new Etapa("Aviso no relógio",
                "No Zepp: Perfil → seu relógio → Notificações e lembretes → Notificações de apps. "
                        + "Marque \"TRexLink\" na lista. Ele só aparece lá depois do primeiro aviso; "
                        + "se não estiver, volte a esta etapa depois do primeiro teste de despertador.",
                "Abrir o Zepp",
                this::abrirZepp,
                prefs.getBoolean("zepp_notif", false), "zepp_notif", false));
        return e;
    }

    // ---------------------------------------------------------------- tela

    private void montar() {
        lista.removeAllViews();
        List<Etapa> etapas = etapas();
        int feitas = 0;
        for (Etapa et : etapas) if (et.feita) feitas++;
        boolean tudo = feitas == etapas.size();

        TextView titulo = texto("TRexLink", 28, Color.BLACK, true);
        titulo.setGravity(Gravity.CENTER);
        lista.addView(titulo);

        TextView sub = texto(tudo
                        ? "Tudo pronto ✅\nPode fechar este app. Ele trabalha sozinho em segundo plano."
                        : "Configuração inicial: " + feitas + " de " + etapas.size() + " etapas",
                16, tudo ? VERDE : CINZA, tudo);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 6 * dp, 0, 18 * dp);
        lista.addView(sub);

        lista.addView(barra(feitas, etapas.size()));

        boolean proximaMarcada = false;
        for (int i = 0; i < etapas.size(); i++) {
            Etapa et = etapas.get(i);
            boolean proxima = !et.feita && !proximaMarcada;
            if (proxima) proximaMarcada = true;
            lista.addView(cartao(i + 1, et, proxima));
        }

        // Rodape
        TextView ponte = texto((Bridge.rodando() ? "● Ponte com o relógio ativa" : "○ Ponte com o relógio parada"),
                13, Bridge.rodando() ? VERDE : Color.rgb(198, 40, 40), false);
        ponte.setGravity(Gravity.CENTER);
        ponte.setPadding(0, 20 * dp, 0, 8 * dp);
        lista.addView(ponte);

        Button teste = botaoSecundario("Testar: acender a lanterna");
        TextView res = texto("", 13, CINZA, false);
        res.setGravity(Gravity.CENTER);
        teste.setOnClickListener(v -> new Thread(() -> {
            String r = Comandos.executar("LANTERNA");
            runOnUiThread(() -> res.setText(r));
        }).start());
        lista.addView(teste);
        lista.addView(res);
    }

    private View cartao(int n, Etapa et, boolean proxima) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(16 * dp, 14 * dp, 16 * dp, 14 * dp);
        GradientDrawable fundo = new GradientDrawable();
        fundo.setColor(Color.WHITE);
        fundo.setCornerRadius(14 * dp);
        fundo.setStroke(proxima ? 2 * dp : dp, proxima ? AZUL : Color.rgb(225, 227, 230));
        c.setBackground(fundo);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 10 * dp;
        c.setLayoutParams(lp);

        String marca = et.feita ? "✅  " : n + ".  ";
        TextView t = texto(marca + et.titulo, 17, et.feita ? VERDE : Color.BLACK, true);
        c.addView(t);

        // Etapas concluidas ficam recolhidas (so o titulo), com opcao discreta de refazer.
        if (et.feita) {
            if (et.chaveManual != null) {
                TextView refazer = texto("Desmarcar", 13, CINZA, false);
                refazer.setPadding(0, 6 * dp, 0, 0);
                refazer.setOnClickListener(v -> {
                    prefs.edit().putBoolean(et.chaveManual, false).apply();
                    montar();
                });
                c.addView(refazer);
            }
            return c;
        }

        TextView ex = texto(et.explicacao, 15, CINZA, false);
        ex.setPadding(0, 6 * dp, 0, 10 * dp);
        c.addView(ex);

        Button b = proxima ? botaoPrimario(et.botao) : botaoSecundario(et.botao);
        b.setOnClickListener(v -> et.acao.run());
        c.addView(b);

        if (et.chaveManual != null) {
            Button feito = botaoSecundario("Já fiz");
            feito.setOnClickListener(v -> {
                prefs.edit().putBoolean(et.chaveManual, true).apply();
                montar();
            });
            c.addView(feito);
        }

        if (et.mostrarRestrita) {
            TextView dica = texto("Se o Android disser \"Configuração restrita\": abra os detalhes do TRexLink, "
                    + "toque nos três pontos no canto superior direito e escolha \"Permitir configurações restritas\". "
                    + "Depois volte aqui.", 13, CINZA, false);
            dica.setPadding(0, 10 * dp, 0, 4 * dp);
            c.addView(dica);
            Button det = botaoSecundario("Abrir detalhes do TRexLink");
            det.setOnClickListener(v -> abrir(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()))));
            c.addView(det);
        }
        return c;
    }

    private View barra(int feitas, int total) {
        LinearLayout trilho = new LinearLayout(this);
        GradientDrawable tf = new GradientDrawable();
        tf.setColor(Color.rgb(225, 227, 230));
        tf.setCornerRadius(4 * dp);
        trilho.setBackground(tf);
        trilho.setWeightSum(total);
        trilho.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 8 * dp));

        View cheio = new View(this);
        GradientDrawable cf = new GradientDrawable();
        cf.setColor(feitas == total ? VERDE : AZUL);
        cf.setCornerRadius(4 * dp);
        cheio.setBackground(cf);
        cheio.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, feitas));
        trilho.addView(cheio);
        return trilho;
    }

    // ---------------------------------------------------------------- utilitarios

    private TextView texto(String s, int sp, int cor, boolean negrito) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(cor);
        if (negrito) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button botaoPrimario(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        GradientDrawable g = new GradientDrawable();
        g.setColor(AZUL);
        g.setCornerRadius(24 * dp);
        b.setBackground(g);
        b.setLayoutParams(margemBotao());
        return b;
    }

    private Button botaoSecundario(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(AZUL);
        b.setTextSize(15);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setStroke(dp, AZUL);
        g.setCornerRadius(24 * dp);
        b.setBackground(g);
        b.setLayoutParams(margemBotao());
        return b;
    }

    private LinearLayout.LayoutParams margemBotao() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 48 * dp);
        lp.topMargin = 6 * dp;
        return lp;
    }

    private void abrir(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void abrirZepp() {
        Intent i = getPackageManager().getLaunchIntentForPackage(PACOTE_ZEPP);
        if (i != null) {
            startActivity(i);
        } else {
            abrir(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + PACOTE_ZEPP)));
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        montar();
    }
}
