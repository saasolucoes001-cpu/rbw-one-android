# Homologação WebView — 1.0.6

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

O usuário optou por continuar sem celular. Não há aparelho conectado nem emulador instalado. Não declarar os fluxos abaixo aprovados com base nos testes JVM/Chromium.

| Jornada | Estado |
| --- | --- |
| Atualizar sobre 1.0.5, preservando login/preferências | Certificado comparado; instalação física pendente |
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
