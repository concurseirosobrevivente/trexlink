# TRexLink

Controle do celular Samsung (Galaxy S25 FE) pelo Amazfit T-Rex 3 Pro 48mm.

- `android/`: app do celular (servidor local em 127.0.0.1:8765, notificacoes, acessibilidade)
- `watch/`: Mini App do relogio (Zepp OS, API 3.0) que manda os comandos pelo Zepp App
- `.github/workflows/build.yml`: compila o APK a cada envio e publica em Releases

Comandos: STATUS, FOTO, FOTO5, CAMERA, ALARME_PARAR, ALARME_ADIAR, DND,
SOM_NORMAL, SOM_VIBRAR, SOM_SILENCIO, LANTERNA, BLOQUEAR.
