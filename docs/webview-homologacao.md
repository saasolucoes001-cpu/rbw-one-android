# Homologação do aplicativo WebView

## Escopo desta alteração

Manter as telas e regras do gestaoemp no site. Adicionar ao Android o salvamento de downloads gerados em memória, sem alterar o Supabase ou as permissões dos usuários.

O script é instalado antes dos scripts do site, somente na origem oficial e no frame principal. Intercepta links com atributo `download` e URLs `blob:https://rbwone.com.br/` ou `data:`. O site continua responsável por buscar os arquivos privados com a sessão existente. Nenhuma credencial é encaminhada a um navegador externo pela ponte.

O conteúdo segue em blocos de 48 KiB com confirmação, sequência e tamanho verificados, até 64 MiB por arquivo. O Android usa cache privado temporário e `ACTION_CREATE_DOCUMENT`; o usuário escolhe o destino. A transferência é cancelada em navegação de documento, fechamento da Activity ou inatividade. O cache é removido no sucesso, cancelamento e falha; sob encerramento abrupto do processo, arquivos abandonados há mais de um dia são removidos na próxima abertura.

## Limites conhecidos

- Alteração incluída na versão 1.0.5, publicada a pedido do usuário com homologação em aparelho físico pendente.
- Links HTTP(S) continuam seguindo o comportamento anterior, abrindo externamente. Downloads que dependem exclusivamente de cookies da WebView precisam de validação específica.
- Pré-visualizações `blob:` sem atributo `download`, `window.open`, impressão e downloads iniciados por frames externos não são convertidos em salvamento por esta ponte.
- Arquivos acima de 64 MiB mostram orientação para usar o navegador. Um download por vez.
- WebView precisa suportar `WEB_MESSAGE_LISTENER` e `DOCUMENT_START_SCRIPT`; o app informa quando é necessário atualizar o componente.
- O CI e a compilação sem `google-services.json` produzem APK de desenvolvimento sem push configurado. Não distribuir esse APK como versão de produção.

## Matriz em aparelho — ainda pendente

Usar contas e registros de teste, evitando assinaturas, pagamentos e aprovações reais. Registrar aparelho, Android, WebView, perfil, resultado e evidência em cada linha.

| Jornada | Critério de aprovação | Estado |
| --- | --- | --- |
| Arquivo privado do Storage | Salvar, abrir e comparar tamanho/hash ao original; nome preservado | Pendente |
| Relatório de acessos PDF e XLSX | Salvar e abrir; senha do documento preservada quando aplicável | Pendente |
| Exportação XLSX de clientes/inspeções | Planilha abre sem corrupção e contém os registros esperados | Pendente |
| Cancelar seletor e tentar novamente | Cancelamento sem arquivo temporário restante; novo download funciona | Pendente |
| Navegar/fechar durante transferência | Sem diálogo tardio nem entrega de arquivo de outra página | Pendente |
| Arquivo vazio, grande e acima do limite | Vazio preservado; grande íntegro; limite informado sem travar | Pendente |
| Destino indisponível/sem espaço | Erro visível; possibilidade de tentar novamente | Pendente |
| Upload, câmera, microfone e localização | Solicitar ao usar; recusa não impede navegação; anexos corretos | Pendente |
| Login, expiração, logout e troca de conta | Dados e notificações respeitam a sessão e as permissões | Pendente |
| Push aberto, segundo plano e tela bloqueada | Um aviso autorizado, som conforme preferência e destino correto | Pendente |
| Atualização assinada | Mesmo pacote/certificado; instalação e cancelamento preservam uso | Pendente |
| Perfis administrativo, cliente, colaborador e prestador | Consultas, formulários e gravações de teste equivalentes ao web | Pendente |

## Publicação

Após homologar, incrementar `versionCode`/`versionName`, compilar com Firebase e a assinatura existente, verificar assinatura e conteúdo do APK e publicar um arquivo com nome novo. Atualizar `web-latest.json` somente com tamanho e hash do artefato aprovado. Preservar os canais legado e de prévia nativa. Não substituir o conteúdo de APKs já publicados.
