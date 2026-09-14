# 📦 IcarusChests

**Baús com tiers, upgrades encaixáveis e mochilas portáteis para servidores Paper — sem exigir nenhum mod do lado do jogador.**

[![Build](https://github.com/IcaroFranca/IcarusChests/actions/workflows/build.yml/badge.svg)](https://github.com/IcaroFranca/IcarusChests/actions/workflows/build.yml)
![Versão](https://img.shields.io/badge/vers%C3%A3o-7.0.0-blueviolet)
![Paper](https://img.shields.io/badge/Paper-1.21.x-2ecc71)
![Java](https://img.shields.io/badge/Java-21-orange)
![Bedrock](https://img.shields.io/badge/Bedrock-compat%C3%ADvel%20via%20Geyser-1abc9c)

Inspirado no mod *Sophisticated Storage*, mas construído do zero como um plugin puro de servidor: reaproveita o bloco de baú vanilla e diferencia cada tier através de uma GUI customizada — nada de resource pack, nada de instalar nada no cliente.

---

## ✨ Funcionalidades

- **6 tiers de baú**, do Normal ao Netherite, cada um craftável com um kit de upgrade consumível.
- **Baú do IcarusChests** — 1× Baú + 1× Redstone craftam o kit que dá origem a tudo: uma cabeça customizada (igual a qualquer outro kit do plugin) que, usada com shift + botão direito num baú comum já colocado, o transforma num baú tier Normal de verdade. Um baú comum colocado sozinho (craft vanilla de 8 tábuas, loot, o que for) continua 100% vanilla até isso acontecer — o plugin nunca converte um baú existente por conta própria, nem os gerados pela geração de mundo. Quebrar um baú do IcarusChests sempre devolve esse kit (além de qualquer kit de upgrade de tier já aplicado) — nunca é um gasto sem volta.
- **Baú duplo** de verdade: dois blocos adjacentes do mesmo tier viram uma única unidade lógica.
- **Upgrades encaixáveis**, instalados direto na GUI do baú:
  - 🔍 **Filtro** — restringe quais itens o baú aceita.
  - 📦 **Stack** — deixa um slot acumular muito além do limite normal do item (até 16x no tier Netherite), com a quantidade real sempre visível na lore.
- **Hopper alimenta o baú** — um hopper (ou hopper minecart, ou um dropper jogando num hopper) embaixo/ao lado de um baú com tier consegue empurrar itens pra dentro dele normalmente, respeitando Filtro e o limite do Stack instalado. Puxar itens de dentro do baú com hopper continua não suportado (decisão deliberada, não uma falta de implementação).
- **Barra de controle** embutida em todo baú/mochila:
  - Rolagem quando a capacidade passa de 45 slots (sem paginar — a mesma janela desliza).
  - 🔎 **Buscar** — abre uma placa pra digitar o nome de um item (funciona em português e inglês) e traz os resultados pro topo.
  - ⚙ **Organizar** — alterna entre 3 modos de ordenação com um clique, sem abrir outro menu.
- **Mochilas portáteis** — a mesma experiência de armazenamento, só que carregada no inventário. Craftável, evoluível de tier mantendo o conteúdo, com os mesmos upgrades de Filtro/Stack. Cada mochila é sempre uma cabeça customizada (nunca um Embrulho/Bundle vanilla) — decisão deliberada pra eliminar de vez a classe de bugs de sumiço/duplicação que um Bundle real, com inventário próprio do cliente, podia causar. Não dá mais pra colorir a mochila.
- **Compatível com Bedrock** (via Geyser/Floodgate): texturas de cabeça customizadas exportadas automaticamente pro Geyser, e um ajuste fino pra contornar como o cliente Bedrock valida stacks localmente.
- **Livro de Receitas** in-game — um menu mostra o ícone de toda receita do plugin de uma vez; clique em qualquer item pra ver exatamente como craftar.
- **Persistência em SQLite** (não YAML), assíncrona, com autosave periódico e migrações versionadas — nunca perde dado num update.

## 🧱 Progressão dos baús

| Tier | Capacidade | Slots de upgrade | Como conseguir |
|---|---|---|---|
| Normal | 27 | 1 | Baú do IcarusChests (1× Baú + 1× Redstone) — shift + botão direito num baú comum |
| Cobre | 45 | 1 | Kit de Upgrade: Cobre (8× Lingote de Cobre) |
| Ferro | 54 | 2 | Kit de Upgrade: Ferro (8× Lingote de Ferro) |
| Ouro | 81 | 2 | Kit de Upgrade: Ouro (8× Lingote de Ouro) |
| Diamante | 108 | 3 | Kit de Upgrade: Diamante (8× Diamante) |
| Netherite | 135 | 4 | Kit de Upgrade: Netherite (4× Lingote de Netherite) |

Cada tier acima do Normal evolui craftando o kit de upgrade correspondente e usando shift + botão direito no baú do tier anterior. Nenhum item se perde na evolução.

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
| `/icaruschests recipebook` | Abre o menu do Livro de Receitas. | `icaruschests.recipebook` (padrão: todos) |
| `/icaruschests give <tier> [jogador]` | Entrega um kit de upgrade de um tier. | `icaruschests.admin` (padrão: op) |
| `/icaruschests reload` | Recarrega `config.yml` sem reiniciar o servidor. | `icaruschests.admin` (padrão: op) |

Aliases: `/icarus`, `/ic`.

## ⚙️ Configuração

Tudo em `config.yml` — nenhuma dessas seções é obrigatória: sem uma textura configurada, o item cai num ícone padrão (vanilla, no caso de kits/botões; a cabeça customizada que o próprio plugin já traz pronta, no caso das mochilas — nunca quebra por falta de configuração).

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

chest-starter-head: "..."
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

- Hoppers só automatizam a entrada de itens num baú com tier, nunca a saída — puxar itens de dentro dele com hopper continua bloqueado (o inventário vanilla do bloco fica sempre vazio de propósito).
- Stacks acima do limite normal (upgrade Stack) não existem no protocolo do Bedrock — o cliente Bedrock vê o limite normal do item, mesmo que o baú guarde mais por dentro.
- Nenhuma receita do plugin (kits, upgrades, mochilas) aparece no livro de receitas *vanilla* do Minecraft — todas ficam só no Livro de Receitas próprio do IcarusChests (`/icaruschests recipebook`).
- Um baú do IcarusChests nunca se une a um baú comum (vanilla mesclaria os dois visualmente, misturando um inventário vanilla de verdade com o inventário do baú tageado, que fica sempre vazio de propósito) — colocar um baú comum do lado de um já tageado é bloqueado, e transformar um baú que já está do lado de um comum também é bloqueado, com uma mensagem explicando o motivo.

## 📜 Créditos

Inspirado no mod *Sophisticated Storage*. Desenvolvido para rodar inteiramente do lado do servidor, sem exigir nada do jogador além de um cliente vanilla.
