# Homologação WebView — 1.0.10

## Impressão em popup e Epson — 29/09/2026

- Na 1.0.9, Gerar Assinatura → Imprimir abria a janela nativa, mas Imprimir / Salvar PDF não acionava o serviço Android. A espera por `onPageFinished` mantinha o pedido pendente.
- Na 1.0.10, o mesmo fluxo com `Teste-RBW-Android` abriu o serviço e salvou PDF A4 de uma página (476.075 bytes). Documento renderizado: assinatura completa, sem recorte. Nenhum e-mail ou cadastro foi enviado.
- Instalação 1.0.9 → 1.0.10 por ADB `-r`, mesmo certificado, login Lucas preservado. 47 testes JVM e 38 Node aprovados; lint zero erros, 12 avisos; actionlint aprovado.
- Impressão física: o PDF anterior de duas páginas da Ficha de Registro, sem dados de funcionário, foi enviado pelo notebook à EPSON L3150 Series. Windows registrou duas páginas e o usuário confirmou o recebimento. Essa etapa não certifica impressão direta Android → Epson, nem a legibilidade física de todos os campos.
- PDFs e imagens da validação permanecem locais. Não foram incluídos no repositório.

## Impressão HTML e abertura de notificação — 29/09/2026

- Toque em uma notificação Android de teste abriu o RBW One e a tela Gerenciar Chamados com a sessão preservada. A abertura automática do painel de detalhes do registro não foi confirmada nesta observação.
- Na Ficha de Registro, Pré-visualizar → Imprimir abriu o serviço Android, mas o PDF original ficou limitado a uma página: o workspace com altura fixa recortava a ficha antes de Dados do Emprego.
- Correção frontend gestaoemp PR 948 publicada, build `64fd3ab76f754bce4e1976c0e22c33047e403609`. As regras de impressão liberam a altura dos ancestrais da ficha e ocultam a navegação apenas durante a impressão dessa página.
- Repetição no Android 1.0.9 após aceitar atualização web: PDF de 110.279 bytes e duas páginas, com Dados do Emprego e Local de Trabalho presentes. Última página renderizada e conferida visualmente. Nenhum registro de empregado foi criado ou alterado.
- Durante a operação, permanência da tela ligada por USB foi ativada a pedido do usuário. Valor original 0 restaurado ao encerrar; tempo de bloqueio e autenticação não foram alterados.

## Complemento de arquivos e impressão — 29/09/2026

- XLSX exportado anteriormente aberto no Microsoft Excel do Galaxy A25. Arquivo reconhecido e aba Financeiro exibida. A consulta exportada não tinha registros; a conta do Excel apresentou restrição própria de edição (somente leitura). Nenhum arquivo foi editado ou enviado para serviço externo.
- PDF de nove páginas aberto no Samsung Notes, opção Imprimir acionada e serviço de impressão Android exibindo papel A4 e nove páginas. Destino Salvar como PDF concluído: novo arquivo local de 26.835 bytes, reaberto por um leitor PDF e confirmado com nove páginas.
- Retorno ao RBW One preservou a sessão e o texto de busca. A jornada acima valida impressão pelo visualizador; não certifica uma impressora física, window.print do HTML ou todas as variantes de iframe.

## Entrega push — 29/09/2026

- Usuário autorizou explicitamente três notificações de homologação, destinadas somente à própria conta. Criadas apenas entradas identificadas como teste na central de notificações, vinculadas a uma rota já autorizada; nenhum chamado ou outro registro de negócio foi alterado.
- Entrega confirmada pelo NotificationManager Android para os três IDs distintos: aplicativo em primeiro plano, aplicativo em segundo plano com launcher ativo e tela bloqueada com Keyguard ativo. A evidência não depende apenas da aceitação pelo FCM.
- Fila do servidor registrou envio na primeira tentativa em todos os casos; tempo entre criação e envio de aproximadamente 54, 40 e 52 segundos, respectivamente, com agendamento de um minuto.
- Canal Android `rbw_updates_bem_te_vi_v1`, importância 3 e visibilidade privada. Não houve medição auditiva nesta etapa; o som já havia sido confirmado pelo usuário anteriormente.

## Atualização e rotação — 29/09/2026

- Atualização real 1.0.8 → 1.0.9 iniciada pelo aviso do próprio aplicativo. Download, instalador Android e verificação Play Protect concluídos. O instalador confirmou a atualização; pacote instalado com versionName 1.0.9 e versionCode 10. Não houve instalação por ADB nessa jornada.
- Sessão administrativa preservada após abrir a versão atualizada, sem novo login.
- Na 1.0.8, girar a tela recriava o WebView e apagava o texto da busca. Na 1.0.9, o texto `inspec` permaneceu após retrato → paisagem → retrato. Isso valida a mudança de orientação, não a preservação após morte do processo.
- Teclado aberto/fechado em dois ciclos: campo visível e área WebView retornando de `[0,77][1080,1108]` para `[0,77][1080,2298]`, sem folga residual. Rotação automática original restaurada.
- Frontend/PWA: publicação do gestaoemp PR 945 confirmada pelo Lovable. Atualização web aceita também no Android. Modal Novo Teste verificado em produção a 320 px, com clientWidth e scrollWidth iguais a 279 px; nenhum teste ou registro criado.
- APK público 1.0.9: 4.210.058 bytes, SHA-256 `068f3ac0e6411e5fe62b4f91f2e12b62fc81e3f184295f2bef1c36397df8af8d`. Build final: 47 testes JVM e 35 Node aprovados, lint sem erros (12 avisos).

## Cobertura atual e pendências

Os estados desta seção substituem os rótulos antigos da matriz histórica abaixo.

| Jornada | Evidência e limite |
| --- | --- |
| Atualizador automático e sessão | Concluído para 1.0.8 → 1.0.9 no Galaxy A25/Android 16. |
| Rotação, teclado e barras | Retrato/paisagem e dois ciclos de teclado concluídos com navegação por gestos; três botões e outros aparelhos ainda não verificados. |
| PDF e XLSX | PDF salvo e aberto no leitor; XLSX salvo e aberto no Excel. Edição depende da conta do Excel; anexos por outros perfis ainda não verificados. |
| Cancelamento de download | Cancelamento e nova tentativa no seletor verificados; destino sem espaço não simulado no aparelho. |
| Câmera, microfone e localização | Validações descritas abaixo concluídas; upload real ainda não realizado. |
| Impressão | PDF pelo visualizador, Ficha de Registro pelo WebView e popup document.write da assinatura validados. Duas páginas recebidas na Epson via notebook. Impressão direta Android → Epson e demais variantes de iframe ainda não homologadas. |
| Autenticação | Sessão preservada nas atualizações; troca de conta/logout e outros perfis ainda não exercitados. |
| Push | Entrega real confirmada no Galaxy A25: aberto, segundo plano e tela bloqueada. Não cobre encerramento forçado, longos períodos de economia de bateria ou todos os fabricantes. |
| Rotas e portais externos | Revisão das telas iniciais não certifica todos os formulários, registros dinâmicos, perfis e serviços externos. Exigem dados/contas de teste apropriados. |

## Validação física em 29/09/2026

Samsung Galaxy A25 (Android 16), pacote `br.com.rbwone.web`:

- Câmera do autenticador: permissão solicitada, leitor pronto e cliente ativo confirmado pelo serviço de câmera Android. Captura encerrada sem importar contas. Testado na 1.0.7.
- PDF de equipamentos: salvo pelo seletor Android e aberto no leitor Samsung Notes, que reconheceu nove páginas. Primeira página conferida visualmente. Dados e imagem de evidência permanecem locais.
- Microfone: a 1.0.7 falhava após conceder RECORD_AUDIO. O log Chromium registrava `Unable to select communication device!`. A 1.0.8 inclui MODIFY_AUDIO_SETTINGS; gravação de 17 segundos, geração de prévia, reprodução e descarte confirmados. Nenhuma mensagem de voz foi enviada.
- Atualização 1.0.7 → 1.0.8 instalada por ADB com `-r`, mesmo certificado, sem desinstalação. Versão/código 1.0.8/9 confirmados e login preservado. Isso não certifica o fluxo completo do atualizador automático.
- Release: 47 testes JVM, 35 testes Node aprovados; lint sem erros (12 avisos), APK release com Firebase e assinatura verificados.
- Localização: após autorização explícita do usuário para Open-Meteo/OpenStreetMap, a permissão temporária foi concedida. A primeira consulta usou a cidade padrão enquanto a permissão era respondida; após atualizar, o Android registrou FINE_LOCATION e a tela exibiu clima da cidade detectada. Nenhuma reserva ou vistoria foi criada. Demais itens da matriz abaixo continuam abertos quando não cobertos pelas evidências acima.

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
