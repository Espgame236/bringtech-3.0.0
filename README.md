# BringTech FT600

Aplicativo Android com painel inspirado na interface da FuelTech FT600, que se conecta a um scanner OBD2 Bluetooth (ELM327) e mostra os dados do veículo em tempo real.

## Recursos

- Painel estilo FT600 com RPM, temperatura e outros parâmetros que o carro disponibilizar pela OBD2
- Busca e conexão com o scanner dentro do app (Bluetooth clássico e BLE), com pedido de permissão ao abrir
- Leitura e limpeza de códigos de falha (DTC) e apagar a luz do painel (MIL)
- Aba **Gravar datalog**: botão de iniciar/parar que salva as leituras em arquivo CSV no celular, para comparar antes e depois de alterações no carro

## Requisitos

- Android 8 ou superior (testado no Android 13)
- Scanner OBD2 Bluetooth compatível com ELM327

## Como gerar o APK

O APK é compilado automaticamente pelo GitHub Actions:

1. Abra a aba **Ações** e escolha **Build APK**
2. Toque em **Run workflow**
3. Ao terminar, baixe o APK em **Artifacts**

## Estrutura

- `MainActivity.kt`: tela principal e abas
- `FtView.kt`: desenho do painel estilo FT600
- `FtList.kt`: lista de parâmetros
- `Elm.kt`: comunicação com o scanner ELM327

## Aviso

Projeto pessoal, sem ligação com a FuelTech. Use o app com segurança e nunca opere o celular enquanto dirige.
