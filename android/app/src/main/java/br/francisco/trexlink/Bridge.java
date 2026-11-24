package br.francisco.trexlink;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Servidor HTTP minimo em 127.0.0.1:8765 (so aceita conexoes do proprio celular).
 * O Zepp App (lado do Mini App do relogio) faz GET /cmd?c=COMANDO.
 */
public final class Bridge {
    public static final int PORTA = 8765;
    private static final String TAG = "TRexLink";
    private static Thread thread;
    private static Context app;

    private Bridge() {}

    public static synchronized void start(Context ctx) {
        app = ctx.getApplicationContext();
        Comandos.init(app);
        if (thread != null && thread.isAlive()) return;
        thread = new Thread(Bridge::loop, "TRexLinkHttp");
        thread.setDaemon(true);
        thread.start();
    }

    public static boolean rodando() {
        return thread != null && thread.isAlive();
    }

    private static void loop() {
        while (true) {
            try (ServerSocket ss = new ServerSocket(PORTA, 16, InetAddress.getByName("127.0.0.1"))) {
                ss.setReuseAddress(true);
                Log.i(TAG, "escutando em 127.0.0.1:" + PORTA);
                while (true) {
                    Socket s = ss.accept();
                    new Thread(() -> atender(s), "TRexLinkReq").start();
                }
            } catch (Exception e) {
                Log.e(TAG, "servidor caiu, reiniciando", e);
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            }
        }
    }

    private static void atender(Socket s) {
        try (Socket sock = s) {
            sock.setSoTimeout(5000);
            BufferedReader r = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
            String linha = r.readLine(); // ex.: GET /cmd?c=LANTERNA HTTP/1.1
            String cmd = "STATUS";
            if (linha != null) {
                String[] partes = linha.split(" ");
                if (partes.length >= 2) {
                    String caminho = partes[1];
                    int i = caminho.indexOf("c=");
                    if (i >= 0) {
                        String v = caminho.substring(i + 2);
                        int amp = v.indexOf('&');
                        if (amp >= 0) v = v.substring(0, amp);
                        cmd = URLDecoder.decode(v, "UTF-8").trim().toUpperCase();
                    }
                }
            }
            String corpo = Comandos.executar(cmd);
            byte[] b = corpo.getBytes(StandardCharsets.UTF_8);
            OutputStream out = sock.getOutputStream();
            String cab = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: application/json; charset=utf-8\r\n"
                    + "Access-Control-Allow-Origin: *\r\n"
                    + "Content-Length: " + b.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(cab.getBytes(StandardCharsets.UTF_8));
            out.write(b);
            out.flush();
        } catch (Exception e) {
            Log.e(TAG, "erro ao atender", e);
        }
    }
}
