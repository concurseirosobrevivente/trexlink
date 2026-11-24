package br.francisco.trexlink;

import android.app.Application;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // O processo sobe quando o Android liga o servico de notificacoes ou de
        // acessibilidade; nesse momento o servidor local comeca a escutar.
        Bridge.start(this);
    }
}
