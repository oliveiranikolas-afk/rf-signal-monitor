# Monitor de Sinal RF (real)

App Android nativo que lê o **RSSI real** (força de sinal) da rede Wi-Fi à
qual o celular está conectado, usando a API pública `WifiManager` do
Android — sem simulação, sem números aleatórios.

## O que ele faz de verdade

- Lê o RSSI (em dBm) a cada ~1 segundo.
- Mantém uma janela das últimas 20 leituras e calcula o desvio-padrão.
- Quando o desvio-padrão passa de um limiar (3 dB, ajustável no código),
  marca "variação acima do limiar" e registra no log.

Isso é uma técnica real, usada em pesquisas de baixo custo de **detecção de
presença** (não identificação de forma/posição): movimento perto do
roteador ou do celular pode causar pequenas flutuações no sinal por
multipath fading.

## O que ele NÃO faz

- **Não gera imagem, silhueta ou posição** de pessoas ou objetos.
- **Não vê através de paredes** no sentido literal.
- É **impreciso**: outros fatores (forno de micro-ondas, Bluetooth,
  congestionamento de rede, o próprio usuário segurando o celular)
  também mudam o RSSI e geram falsos positivos. Sem hardware
  especializado (CSI dedicado), não dá pra separar essas causas.

## Como compilar e instalar

1. Instale o [Android Studio](https://developer.android.com/studio).
2. Abra esta pasta (`RfSignalMonitor`) como projeto (`File > Open`).
3. Deixe o Gradle sincronizar (baixa as dependências automaticamente).
4. Conecte seu POCO X7 Pro por USB com "Depuração USB" ativada
   (Configurações > Sobre o telefone > toque 7x em "Versão MIUI/HyperOS"
   para liberar Opções do desenvolvedor).
5. Clique em "Run" (▶) no Android Studio, selecionando seu aparelho.
6. No app, conceda a permissão de localização quando solicitado — o
   Android exige isso para liberar leitura de dados de Wi-Fi, mesmo que
   o app não use GPS.
7. Toque em "Iniciar monitoramento".

## Aba "Visualização 3D"

Uma cena 3D (Three.js, dentro de um WebView) onde ondas se propagam a
partir de um "emissor" (representando o celular) e rebatem num
"obstáculo".

- **O que é real:** a velocidade, brilho e turbulência das ondas são
  movidos pelos valores de RSSI e desvio-padrão lidos de verdade na aba
  "Gráfico" — quando o app detecta variação acima do limiar, surgem
  ondas vermelhas mais rápidas e turbulentas.
- **O que é ilustrativo:** a posição do "obstáculo" (o bloco laranja) é
  **fixa**, definida no código (`rf_3d.html`), não calculada a partir do
  sinal. Não existe forma de calcular a posição real de um obstáculo
  usando apenas o RSSI de um único rádio — isso exigiria múltiplas
  antenas com ângulo de chegada (AoA/AoD), presente só em hardware
  especializado, não em smartphones comuns.

Essa aba precisa de conexão com a internet (só na primeira vez que
carrega, para baixar a biblioteca Three.js do CDN). Se quiser que
funcione 100% offline, dá pra baixar o arquivo `three.min.js` e
referenciá-lo localmente em `app/src/main/assets/`.

## Ajustando a sensibilidade

No arquivo `MainActivity.kt`, os valores no topo controlam o
comportamento:

```kotlin
private const val WINDOW_SIZE = 20              // quantas leituras entram na janela
private const val VARIATION_THRESHOLD_DB = 3.0  // sensibilidade do alarme
```

Diminuir `VARIATION_THRESHOLD_DB` deixa mais sensível (mais falsos
positivos); aumentar deixa mais "preguiçoso".
