package br.francisco.trexlink;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Le a notificacao do alarme (Relogio da Samsung) e aperta Dispensar/Soneca. Tambem liga o Nao Perturbe. */
public class NotifService extends NotificationListenerService {
    public static volatile NotifService instancia;

    private static final String[] PACOTES_ALARME = {
            "com.sec.android.app.clockpackage",
            "com.google.android.deskclock"
    };
    private static final String[] PARAR = {"dispensar", "descartar", "parar", "desligar", "fechar", "dismiss", "stop"};
    private static final String[] ADIAR = {"soneca", "adiar", "snooze"};

    @Override
    public void onListenerConnected() {
        instancia = this;
        Bridge.start(this);
    }

    @Override
    public void onListenerDisconnected() {
        instancia = null;
    }

    /** @return mensagem para o relogio */
    public String alarme(boolean parar) {
        String[] alvo = parar ? PARAR : ADIAR;
        List<String> vistos = new ArrayList<>();
        StatusBarNotification[] ativas;
        try {
            ativas = getActiveNotifications();
        } catch (Exception e) {
            return "Sem acesso as notificacoes";
        }
        if (ativas == null) return "Sem acesso as notificacoes";
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
                            return "Falhou ao apertar: " + a.title;
                        }
                    }
                }
            }
        }
        // Plano B: tela do alarme aberta -> acessibilidade procura o botao pelo texto
        AcessService ac = AcessService.instancia;
        if (ac != null && ac.clicarTexto(PACOTES_ALARME, alvo)) {
            return parar ? "Alarme parado" : "Alarme adiado";
        }
        if (vistos.isEmpty()) return "Nenhum alarme tocando";
        return "Botoes vistos: " + String.join(", ", vistos);
    }

    private static boolean ehAlarme(String pkg) {
        for (String p : PACOTES_ALARME) if (p.equals(pkg)) return true;
        return false;
    }
}
