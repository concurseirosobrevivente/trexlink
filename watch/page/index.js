import { BasePage } from '@zeppos/zml/base-page'
import { createWidget, widget, align, prop, text_style } from '@zos/ui'

const URL = 'http://127.0.0.1:8765/cmd?c='

// [rotulo, comando, cor]
const BOTOES = [
  ['Foto', 'FOTO', 0x1e88e5],
  ['Foto em 5 s', 'FOTO5', 0x1565c0],
  ['Abrir camera', 'CAMERA', 0x0d47a1],
  ['Parar alarme', 'ALARME_PARAR', 0xc62828],
  ['Adiar alarme', 'ALARME_ADIAR', 0xef6c00],
  ['Nao Perturbe', 'DND', 0x6a1b9a],
  ['Som normal', 'SOM_NORMAL', 0x2e7d32],
  ['Vibrar', 'SOM_VIBRAR', 0x558b2f],
  ['Silencio', 'SOM_SILENCIO', 0x37474f],
  ['Lanterna', 'LANTERNA', 0xf9a825],
  ['Bloquear tela', 'BLOQUEAR', 0x455a64],
  ['Atualizar status', 'STATUS', 0x00838f],
]

Page(
  BasePage({
    state: { status: null, busy: false },

    build() {
      const W = 480
      const BW = 340
      const BH = 76
      const GAP = 14
      let y = 70

      this.state.status = createWidget(widget.TEXT, {
        x: 60,
        y: 20,
        w: 360,
        h: 110,
        color: 0xffffff,
        text_size: 26,
        align_h: align.CENTER_H,
        align_v: align.CENTER_V,
        text_style: text_style.WRAP,
        text: 'Conectando...',
      })
      y = 140

      BOTOES.forEach(([rotulo, cmd, cor]) => {
        createWidget(widget.BUTTON, {
          x: (W - BW) / 2,
          y,
          w: BW,
          h: BH,
          radius: 38,
          normal_color: cor,
          press_color: 0x000000,
          text: rotulo,
          text_size: 30,
          click_func: () => this.enviar(cmd),
        })
        y += BH + GAP
      })

      // espaco final para rolar ate o ultimo botao em tela redonda
      createWidget(widget.FILL_RECT, { x: 0, y, w: W, h: 120, color: 0x000000 })

      this.enviar('STATUS')
    },

    mostrar(t) {
      if (this.state.status) this.state.status.setProperty(prop.TEXT, t)
    },

    enviar(cmd) {
      if (this.state.busy) return
      this.state.busy = true
      this.mostrar('Enviando...')
      this.httpRequest({ method: 'get', url: URL + cmd, timeout: 8000 })
        .then((res) => {
          let b = res && res.body
          if (typeof b === 'string') {
            try { b = JSON.parse(b) } catch (e) {}
          }
          if (b && typeof b === 'object') {
            const bat = b.bateria != null ? 'Cel ' + b.bateria + '%' : ''
            this.mostrar((b.msg || 'OK') + '\n' + bat)
          } else {
            this.mostrar('Resposta vazia')
          }
        })
        .catch((e) => {
          this.mostrar('Sem conexao com o app do celular')
        })
        .finally(() => {
          this.state.busy = false
        })
    },
  }),
)
