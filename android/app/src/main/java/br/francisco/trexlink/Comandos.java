package br.francisco.trexlink;

import android.app.NotificationManager;
import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

/** Executa os comandos que chegam do relogio e devolve JSON {ok, msg, bateria}. */
public final class Comandos {
    private static Context ctx;
    private static volatile boolean lanternaLigada = false;
    private static String idFlash;
    private static boolean iniciado = false;
    private static final Handler main = new Handler(Looper.getMainLooper());

    private Comandos() {}

    static synchronized void init(Context c) {
        ctx = c.getApplicationContext();
        if (iniciado) return;
        iniciado = true;
        try {
            CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                Boolean f = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer face = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                if (Boolean.TRUE.equals(f) && face != null && face == CameraCharacteristics.LENS_FACING_BACK) {
                    idFlash = id;
                    break;
                }
            }
            cm.registerTorchCallback(new CameraManager.TorchCallback() {
                @Override
                public void onTorchModeChanged(String cameraId, boolean enabled) {
                    if (cameraId.equals(idFlash)) lanternaLigada = enabled;
                }
            }, main);
        } catch (Exception ignored) {}
    }

    static String executar(String cmd) {
        boolean ok = true;
        String msg;
        try {
            switch (cmd) {
                case "STATUS":
                    msg = status();
                    break;
                case "LANTERNA":
                    msg = lanterna();
                    break;
                case "DND":
                    msg = naoPerturbe();
                    break;
                case "SOM_NORMAL":
                    msg = som(AudioManager.RINGER_MODE_NORMAL, "Som normal");
                    break;
                case "SOM_VIBRAR":
                    msg = som(AudioManager.RINGER_MODE_VIBRATE, "Modo vibrar");
                    break;
                case "SOM_SILENCIO":
                    msg = som(AudioManager.RINGER_MODE_SILENT, "Modo silencioso");
                    break;
                case "BLOQUEAR": {
                    AcessService a = AcessService.instancia;
                    if (a == null) { ok = false; msg = "Ative a Acessibilidade do TRexLink"; }
                    else msg = a.bloquear() ? "Tela bloqueada" : "Nao consegui bloquear";
                    break;
                }
                case "CAMERA": {
                    AcessService a = AcessService.instancia;
                    if (a == null) { ok = false; msg = "Ative a Acessibilidade do TRexLink"; }
                    else { main.post(a::abrirCamera); msg = "Camera aberta"; }
                    break;
                }
                case "FOTO":
                    msg = foto(0);
                    break;
                case "FOTO5":
                    msg = foto(5);
                    break;
                case "ALARME_PARAR":
                case "ALARME_ADIAR": {
                    NotifService n = NotifService.instancia;
                    if (n == null) { ok = false; msg = "Ative o acesso a notificacoes do TRexLink"; }
                    else msg = n.alarme(cmd.equals("ALARME_PARAR"));
                    break;
                }
                default:
                    ok = false;
                    msg = "Comando desconhecido";
            }
        } catch (SecurityException e) {
            ok = false;
            msg = "Falta permissao (Nao Perturbe)";
        } catch (Exception e) {
            ok = false;
            msg = "Erro: " + e.getClass().getSimpleName();
        }
        try {
            JSONObject j = new JSONObject();
            j.put("ok", ok);
            j.put("msg", msg);
            j.put("bateria", bateria());
            return j.toString();
        } catch (Exception e) {
            return "{\"ok\":false,\"msg\":\"erro\"}";
        }
    }

    private static String status() {
        StringBuilder sb = new StringBuilder("Conectado");
        if (AcessService.instancia == null) sb.append(" | sem Acessib.");
        if (NotifService.instancia == null) sb.append(" | sem Notif.");
        return sb.toString();
    }

    private static int bateria() {
        BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    private static String lanterna() throws Exception {
        if (idFlash == null) return "Flash nao encontrado";
        CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
        boolean novo = !lanternaLigada;
        cm.setTorchMode(idFlash, novo);
        lanternaLigada = novo;
        return novo ? "Lanterna ligada" : "Lanterna desligada";
    }

    private static String naoPerturbe() {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        boolean ligado = nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL;
        int alvo = ligado ? NotificationManager.INTERRUPTION_FILTER_ALL : NotificationManager.INTERRUPTION_FILTER_PRIORITY;
        if (nm.isNotificationPolicyAccessGranted()) {
            nm.setInterruptionFilter(alvo);
        } else if (NotifService.instancia != null) {
            NotifService.instancia.requestInterruptionFilter(
                    ligado ? NotifService.INTERRUPTION_FILTER_ALL : NotifService.INTERRUPTION_FILTER_PRIORITY);
        } else {
            return "Falta permissao (Nao Perturbe)";
        }
        return ligado ? "Nao Perturbe desligado" : "Nao Perturbe ligado";
    }

    private static String som(int modo, String ok) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        am.setRingerMode(modo);
        return ok;
    }

    private static String foto(int segundos) {
        final AcessService a = AcessService.instancia;
        if (a == null) return "Ative a Acessibilidade do TRexLink";
        final boolean aberta = a.cameraAberta();
        if (!aberta) main.post(a::abrirCamera);
        long espera = (aberta ? 0 : 2000) + segundos * 1000L;
        main.postDelayed(a::disparar, espera + 150);
        if (segundos > 0) return "Foto em " + segundos + " s";
        return aberta ? "Foto tirada" : "Abrindo camera e tirando foto";
    }
}
