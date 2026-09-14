# IcarusChests — instruções permanentes do projeto

Convenções combinadas com o dono do projeto. Valem para toda sessão futura, não só para a mudança que estava em andamento quando foram escritas.

## 1. Todo item craftável novo vai para o Livro de Receitas

Sempre que um item novo puder ser craftado (upgrade, kit, mochila, o que for),
a receita dele **tem que** ser adicionada em
`src/main/java/dev/icaro/icaruschests/gui/RecipeBookRegistry.java`
(`buildAll()`). O plugin não lista as receitas do Bukkit automaticamente — se
não entrar aqui, o jogador não descobre como craftar, mesmo com a receita de
verdade registrada e funcionando.

## 2. Versionamento — MAJOR.MINOR.PATCH em `build.gradle`

Toda mudança publicada muda a `version` em `build.gradle` (`plugin.yml` lê
`${version}` de lá, não precisa mexer nos dois). Antes de cada commit que
altera comportamento do plugin, decida:

- **Correção de erro/bug** → sobe o terceiro número (`PATCH`): `2.0.0` → `2.0.1`.
- **Atualização pequena** (ajuste, melhoria pontual, não quebra nada) → sobe o
  segundo número e zera o terceiro (`MINOR`): `2.0.1` → `2.1.0`.
- **Atualização grande** (feature nova, mudança estrutural) → sobe o primeiro
  número e zera os outros dois (`MAJOR`): `2.1.0` → `3.0.0`.

Mudança só em documentação/comentário/teste sem afetar o jar publicado não
precisa de bump.

## 3. README

O `README.md` da raiz deve continuar refletindo o estado real do plugin —
tiers, upgrades, mochilas, comandos, etc. Ao adicionar uma feature grande o
suficiente para entrar aqui neste CLAUDE.md, considere se o README também
precisa de uma seção nova.
