# Homologação WebView — 1.0.8

## Validação física em 29/09/2026

Samsung Galaxy A25 (Android 16), pacote `br.com.rbwone.web`:

- Câmera do autenticador: permissão solicitada, leitor pronto e cliente ativo confirmado pelo serviço de câmera Android. Captura encerrada sem importar contas. Testado na 1.0.7.
- PDF de equipamentos: salvo pelo seletor Android e aberto no leitor Samsung Notes, que reconheceu nove páginas. Primeira página conferida visualmente. Dados e imagem de evidência permanecem locais.
- Microfone: a 1.0.7 falhava após conceder RECORD_AUDIO. O log Chromium registrava `Unable to select communication device!`. A 1.0.8 inclui MODIFY_AUDIO_SETTINGS; gravação de 17 segundos, geração de prévia, reprodução e descarte confirmados. Nenhuma mensagem de voz foi enviada.
- Atualização 1.0.7 → 1.0.8 instalada por ADB com `-r`, mesmo certificado, sem desinstalação. Versão/código 1.0.8/9 confirmados e login preservado. Isso não certifica o fluxo completo do atualizador automático.
- Release: 47 testes JVM, 35 testes Node aprovados; lint sem erros (12 avisos), APK release com Firebase e assinatura verificados.
- Localização aguarda autorização específica para as consultas externas do fluxo de veículos. Demais itens da matriz abaixo continuam abertos quando não cobertos pelas evidências acima.

## Histórico da matriz anterior

## Correções implementadas

| Fluxo | Implementação e evidência |
| --- | --- |
| Downloads gerados pelo site | Ponte restrita à origem oficial/frame principal. Testes JavaScript, JVM e Chromium com bytes reais, cliques de bibliotecas e revogação imediata de URL. |
| Downloads HTTPS autenticados | Cookies da origem oficial não seguem em redirecionamento externo. Testes de redirecionamento, autenticação negada, cancelamento e arquivo truncado. |
| Arquivos acima de 64 MiB | Até 512 MiB, streaming e tamanho desconhecido. Teste JVM grava e verifica 65 MiB sem alocar o arquivo inteiro em memória. |
| Pré-visualização blob | window.open(blob:) entrega um arquivo temporário a um visualizador Android; sem visualizador, oferece salvar. Acesso específico de leitura, revogado na navegação/logout/fechamento. |
| Impressão HTML | window.print() usa PrintManager. Relatórios document.write recebem janela com Imprimir / Salvar PDF e Fechar. Compilado e verificado por lint; impressão real pendente. |
| PDFs em iframe | Para blobs PDF identificados, oferece abrir no visualizador e tenta encaminhar print(). Homologação Android pendente. |
| Páginas externas | Novas janelas externas abrem no navegador por ação do usuário. Frames externos não recebem pontes privilegiadas. |

## Testes reproduzíveis

- `node --test scripts/web-downloads.test.mjs scripts/download.test.mjs`
- `gradlew.bat :app:testDebugUnitTest :app:lintRelease :app:assembleRelease`
- `node scripts/web-downloads.browser.cjs`

O último comando requer Playwright e Chrome; PLAYWRIGHT_MODULE pode indicar o caminho do módulo. Usa um perfil temporário e respostas simuladas; não acessa contas nem dados de produção.

## Pendências físicas

A matriz abaixo foi escrita antes da conexão do celular. Consulte a seção de 29/09/2026 para os testes físicos já realizados; os testes JVM/Chromium não substituem as jornadas restantes.

| Jornada | Estado |
| --- | --- |
| Atualizar sobre 1.0.6, preservando login/preferências | Mesmo certificado confirmado; instalação física pendente |
| Barras superior/inferior com gestos e três botões; retrato/paisagem com recorte | Pendente em aparelho |
| Abrir/fechar teclado repetidamente, sem folga residual e com campo visível | Pendente em aparelho |
| PDF, XLSX e anexo reais por perfil | Pendente em aparelho com conta de teste |
| Cancelamento, destino sem espaço e nova tentativa | Lógica testada; seletor físico pendente |
| Impressão, visualizador e retorno ao app | Pendente em aparelho |
| Câmera, microfone, localização e uploads | Pendente em aparelho |
| Login, troca de conta e logout | Testes de política existentes; jornada real pendente |
| Push aberto/segundo plano/tela bloqueada | Firebase compilado; entrega real pendente |
| Portais externos e iframes de terceiros | Pendente com acesso de teste ao serviço externo |

## Limites explícitos

- Até 512 MiB por arquivo, uma transferência por vez e armazenamento livre suficiente.
- HTTPS obrigatório. O app não inventa cabeçalhos de autenticação; o site continua responsável por buscar arquivos com seu SDK/token antes de entregar o blob.
- Pontes não são expostas a frames de terceiros. Seus blobs privados exigem uso do serviço externo no navegador ou integração autorizada.
- Pré-visualizações usam aplicativos Android instalados; a impressão de PDFs depende do visualizador. Sem aplicativo compatível, pode-se salvar.
- Previews ficam em cache privado e são removidos/revogados ao encerrar o contexto. Após morte abrupta do processo, temporários com mais de um dia são limpos na próxima abertura.
- Remoção de um documento de destino parcialmente salvo depende do suporte do provedor Android.
- Equivalência funcional de todos os módulos ainda exige contas de teste e jornadas reais.
