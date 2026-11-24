package br.francisco.trexlink;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Le a notificacao do alarme (Relogio da Samsung) e aperta Dispensar/Soneca. Tambem liga o Nao Perturbe.
 * Quando o despertador comeca a tocar, publica uma notificacao comum do TRexLink, que o Zepp App
 * repassa ao relogio (o Zepp nao repassa a notificacao de alarme da Samsung).
 */
public class NotifService extends NotificationListenerService {
    public static volatile NotifService instancia;

    static final String[] PACOTES_ALARME = {
            "com.sec.android.app.clockpackage",
            "com.google.android.deskclock"
    };
    static final String[] PARAR = {"dispensar", "descartar", "parar", "desligar", "fechar", "dismiss", "stop"};
    static final String[] ADIAR = {"soneca", "adiar", "snooze"};

    private static final String CANAL = "alarme_relogio";
    private static final int ID_AVISO = 4242;
    /** Chave da notificacao do alarme que esta tocando (para so cancelar o aviso quando ELA sumir). */
    private volatile String chaveTocando;

    @Override
    public void onListenerConnected() {
        instancia = this;
        Bridge.start(this);
    }

    @Override
    public void onListenerDisconnected() {
        instancia = null;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !ehAlarme(sbn.getPackageName())) return;
        if (Notification.CATEGORY_ALARM.equals(sbn.getNotification().category)
                || temAcaoDeAlarme(sbn.getNotification())) {
            boolean novo = !sbn.getKey().equals(chaveTocando);
            chaveTocando = sbn.getKey();
            if (novo) avisarRelogio();
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null || !ehAlarme(sbn.getPackageName())) return;
        if (!sbn.getKey().equals(chaveTocando)) return; // outra notificacao do Relogio (ex.: "proximo alarme")
        chaveTocando = null;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(ID_AVISO);
    }

    private void avisarRelogio() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CANAL) == null) {
            NotificationChannel c = new NotificationChannel(CANAL, "Despertador tocando",
                    NotificationManager.IMPORTANCE_HIGH);
            c.setDescription("Aviso enviado ao relogio quando o despertador toca");
            nm.createNotificationChannel(c);
        }
        Notification n = new Notification.Builder(this, CANAL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Despertador tocando")
                .setContentText("Abra o TRexLink no relogio e toque em Parar alarme")
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build();
        try {
            nm.notify(ID_AVISO, n);
        } catch (Exception ignored) {}
    }

    private static boolean temAcaoDeAlarme(Notification n) {
        if (n.actions == null) return false;
        for (Notification.Action a : n.actions) {
            if (a == null || a.title == null) continue;
            String t = a.title.toString().toLowerCase(Locale.ROOT);
            for (String k : ADIAR) if (t.contains(k)) return true;
        }
        return false;
    }

    /** @return mensagem para o relogio */
    public String alarme(boolean parar) {
        String[] alvo = parar ? PARAR : ADIAR;
        List<String> vistos = new ArrayList<>();
        StatusBarNotification[] ativas = null;
        try {
            ativas = getActiveNotifications();
        } catch (Exception ignored) {}
        if (ativas != null) {
            for (StatusBarNotification sbn : ativas) {
                if (!ehAlarme(sbn.getPackageName())) continue;
                Notification n = sbn.getNotification();
                if (n.actions == null) continue;
                for (Notification.Action a : n.actions) {
                    if (a == null || a.title == null) continue;
                    String t = a.title.toString().toLowerCase(Locale.ROOT);
                    vistos.add(a.title.toString());
                    for (String chave : alvo) {
                        if (t.contains(chave)) {
                            try {
                                a.actionIntent.send();
                                return parar ? "Alarme parado" : "Alarme adiado";
                            } catch (Exception e) {
                                break;
                            }
                        }
                    }
                }
            }
        }
        // Plano B: tela cheia do despertador -> deslizar o botao (a Samsung exige deslizar)
        AcessService ac = AcessService.instancia;
        if (ac != null) {
            String r = ac.deslizarAlarme(PACOTES_ALARME, alvo);
            if (r != null) return parar ? "Alarme parado" : "Alarme adiado";
            String textos = ac.textosNaTela(PACOTES_ALARME);
            if (!textos.isEmpty()) return "Tela: " + textos;
        }
        if (vistos.isEmpty()) return "Nenhum alarme tocando";
        return "Botoes vistos: " + String.join(", ", vistos);
    }

    static boolean ehAlarme(String pkg) {
        for (String p : PACOTES_ALARME) if (p.equals(pkg)) return true;
        return false;
    }
}
