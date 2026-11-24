import { BaseSideService } from '@zeppos/zml/base-side'

// Roda dentro do Zepp App no celular. As requisicoes HTTP do relogio
// (this.httpRequest na pagina) sao executadas aqui e vao para o app
// TRexLink do celular, que escuta em http://127.0.0.1:8765
AppSideService(
  BaseSideService({
    onInit() {},
    onRun() {},
    onDestroy() {},
  }),
)
