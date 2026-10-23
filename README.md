# Minimal Shell 0.1.0

Launcher Android minimalista inspirado em interfaces textuais como Oasis Launcher.

## Recursos deste preview
- Home própria e seleção como launcher padrão
- Lista/busca de aplicativos instalados
- Recentes baseados em UsageStats
- Central de notificações via NotificationListenerService
- Abrir e dispensar notificações
- Central de controle com rede, Bluetooth, tela, som e brilho
- Central de ajustes com permissões especiais e atalhos do sistema
- Tema escuro minimalista integrado

## Limitações sem root
O APK não substitui SystemUI, Quickstep/Overview real, lockscreen ou o painel rápido nativo. Essas áreas exigem privilégios de sistema/ROM.

## Build
```bash
gradle assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`
