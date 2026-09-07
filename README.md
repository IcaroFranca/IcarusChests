# 📦 IcarusChests

**Baús com tiers, upgrades encaixáveis e mochilas portáteis para servidores Paper — sem exigir nenhum mod do lado do jogador.**

[![Build](https://github.com/IcaroFranca/IcarusChests/actions/workflows/build.yml/badge.svg)](https://github.com/IcaroFranca/IcarusChests/actions/workflows/build.yml)
![Versão](https://img.shields.io/badge/vers%C3%A3o-2.2.0-blueviolet)
![Paper](https://img.shields.io/badge/Paper-1.21.x-2ecc71)
![Java](https://img.shields.io/badge/Java-21-orange)
![Bedrock](https://img.shields.io/badge/Bedrock-compat%C3%ADvel%20via%20Geyser-1abc9c)

Inspirado no mod *Sophisticated Storage*, mas construído do zero como um plugin puro de servidor: reaproveita o bloco de baú vanilla e diferencia cada tier através de uma GUI customizada — nada de resource pack, nada de instalar nada no cliente.

---

## ✨ Funcionalidades

- **6 tiers de baú**, do madeira ao Netherite, cada um craftável com um kit de upgrade consumível.
- **Baú duplo** de verdade: dois blocos adjacentes do mesmo tier viram uma única unidade lógica.
- **Upgrades encaixáveis**, instalados direto na GUI do baú:
  - 🔍 **Filtro** — restringe quais itens o baú aceita.
  - 📦 **Stack** — deixa um slot acumular muito além do limite normal do item (até 16x no tier Netherite), com a quantidade real sempre visível na lore.
- **Barra de controle** embutida em todo baú/mochila:
  - Rolagem quando a capacidade passa de 45 slots (sem paginar — a mesma janela desliza).
  - 🔎 **Buscar** — abre uma placa pra digitar o nome de um item (funciona em português e inglês) e traz os resultados pro topo.
  - ⚙ **Organizar** — alterna entre 3 modos de ordenação com um clique, sem abrir outro menu.
- **Mochilas portáteis** — a mesma experiência de armazenamento, só que carregada no inventário. Craftável, evoluível de tier mantendo o conteúdo, com os mesmos upgrades de Filtro/Stack. O tooltip mostra um preview de verdade do que está guardado dentro (protegido: clique direito no item é bloqueado em qualquer inventário, então o mecanismo nativo de inserir/retirar do Bundle nunca é acionado), e dá pra colorir craftando a mochila junto com qualquer corante (preserva conteúdo e tier — muda só a cor).
- **Compatível com Bedrock** (via Geyser/Floodgate): texturas de cabeça customizadas exportadas automaticamente pro Geyser, e um ajuste fino pra contornar como o cliente Bedrock valida stacks localmente.
- **Livro de Receitas** in-game — todo item craftável do plugin tem uma página mostrando exatamente como fazer.
- **Persistência em SQLite** (não YAML), assíncrona, com autosave periódico e migrações versionadas — nunca perde dado num update.

## 🧱 Progressão dos baús

| Tier | Capacidade | Slots de upgrade | Material do kit |
|---|---|---|---|
| Normal | 27 | 1 | — (tier inicial) |
| Cobre | 45 | 1 | 8× Lingote de Cobre |
| Ferro | 54 | 2 | 8× Lingote de Ferro |
| Ouro | 81 | 2 | 8× Lingote de Ouro |
| Diamante | 108 | 3 | 8× Diamante |
| Netherite | 135 | 4 | 4× Lingote de Netherite |

Cada tier evolui craftando um kit de upgrade e usando shift + botão direito no baú do tier anterior. Nenhum item se perde na evolução.

## 🎒 Progressão das mochilas

| Tier | Capacidade | Como craftar |
|---|---|---|
| Couro | 9 | 8× Couro + 1 Baú |
| Cobre | 18 | Mochila de Couro + 8× Lingote de Cobre |
| Ferro | 27 | Mochila de Cobre + 8× Lingote de Ferro |
| Ouro | 36 | Mochila de Ferro + 8× Lingote de Ouro |
| Diamante | 45 | Mochila de Ouro + 8× Diamante |
| Netherite | 54 | Mochila de Diamante + 8× Lingote de Netherite |

Segurar a mochila e clicar (qualquer clique — direito, esquerdo, no ar, num bloco ou até atacando) abre ela na hora. Evoluir de tier recrafta a mesma mochila com mais capacidade, sem perder o que já estava guardado dentro.

## ⌨️ Comandos

| Comando | Descrição | Permissão |
|---|---|---|
| `/icaruschests` (ou `/icaruschests ping`) | Checagem rápida — confirma que o plugin está online. | — |
| `/icaruschests info` | Mostra tier, capacidade e id do baú mirado. | `icaruschests.info` (padrão: todos) |
| `/icaruschests recipebook` | Entrega o Livro de Receitas. | `icaruschests.recipebook` (padrão: todos) |
| `/icaruschests give <tier> [jogador]` | Entrega um kit de upgrade de um tier. | `icaruschests.admin` (padrão: op) |
| `/icaruschests reload` | Recarrega `config.yml` sem reiniciar o servidor. | `icaruschests.admin` (padrão: op) |

Aliases: `/icarus`, `/ic`.

## ⚙️ Configuração

Tudo em `config.yml` — nenhuma dessas seções é obrigatória: sem uma textura configurada, o item cai num ícone vanilla de fallback (nunca quebra por falta de configuração).

```yaml
autosave-interval-seconds: 300

# Textura Base64 de cada cabeça customizada (pegue em minecraft-heads.com ou similar).
upgrade-kit-heads:
  copper: "..."
  iron: "..."
  # ...

upgrade-heads:
  filter: "..."
  stack_copper: "..."
  # ...

control-heads:
  search: "..."
  organize: "..."

backpack-heads:
  leather: "..."
  copper: "..."
  # ...
```

## 🛠️ Compilando

Requer JDK 21.

```bash
git clone https://github.com/IcaroFranca/IcarusChests.git
cd IcarusChests
./gradlew build        # gera o jar em build/libs/
./gradlew runServer    # sobe um servidor Paper de teste com o plugin já instalado
```

O build é validado no CI (GitHub Actions) a cada push, com testes reais contra SQLite de verdade — nada de mock.

## 🗺️ Arquitetura, em uma frase

Baú e mochila implementam a mesma interface (`StorageContainer`/`StorageTier`): toda a GUI, os cliques, os upgrades e a busca são escritos **uma única vez** contra essa interface — a diferença entre "um bloco no mundo" e "um item no inventário" fica isolada em como cada um é identificado e persistido, nunca em como funciona por dentro.

## 📋 Limitações conhecidas

- Hoppers não automatizam baús com tier (o inventário vanilla do bloco fica sempre vazio de propósito).
- Stacks acima do limite normal (upgrade Stack) não existem no protocolo do Bedrock — o cliente Bedrock vê o limite normal do item, mesmo que o baú guarde mais por dentro.
- Nenhuma receita do plugin (kits, upgrades, mochilas) aparece no livro de receitas *vanilla* do Minecraft — todas ficam só no Livro de Receitas próprio do IcarusChests (`/icaruschests recipebook`).

## 📜 Créditos

Inspirado no mod *Sophisticated Storage*. Desenvolvido para rodar inteiramente do lado do servidor, sem exigir nada do jogador além de um cliente vanilla.
