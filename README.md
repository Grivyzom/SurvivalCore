"# SurvivalCore" 


# 📋 Placeholders de SurvivalCore

SurvivalCore incluye un sistema completo de placeholders compatible con **PlaceholderAPI** para mostrar información del jugador en tiempo real.

## 🔧 Configuración

### Prerrequisitos
- **PlaceholderAPI** instalado y configurado
- **LuckPerms** (requerido para placeholders de rankup)

### Instalación
1. Instala PlaceholderAPI: `/papi download SurvivalCore`
2. Recarga PlaceholderAPI: `/papi reload`
3. Los placeholders estarán disponibles inmediatamente

## 📖 Uso General

**Formato:** `%score_<placeholder>%`

**Ejemplo:** `%score_farming_level%` → `15`

---

## 📚 Lista de Placeholders

### 👤 Información del Jugador
```
%score_farming_level%     # Nivel de agricultura (ej: 15)
%score_farming_xp%        # XP total de agricultura (ej: 45820)
%score_mining_level%      # Nivel de minería (ej: 12)
%score_mining_xp%         # XP total de minería (ej: 38500)
%score_total_score%       # XP total combinado (ej: 84320)
%score_birthday%          # Fecha de nacimiento (ej: 1995-03-15)
%score_gender%            # Género del jugador (ej: Masculino)
%score_country%           # País del jugador (ej: España)
```

### 🏦 Banco de XP
```
%score_banked_xp%         # XP almacenado en el banco (ej: 125000)
%score_bank_capacity%     # Capacidad máxima del banco (ej: 170000)
```

### 🏆 Sistema de Rankup
```
%score_rank%              # ID del rango actual (ej: soldado)
%score_rank_display%      # Nombre del rango (ej: Soldado Experimentado)
%score_next_rank%         # Siguiente rango (ej: Capitán)
%score_rank_order%        # Posición del rango (ej: 5)
%score_is_max_rank%       # Rango máximo alcanzado (true/false)
%score_total_ranks%       # Total de rangos disponibles (ej: 15)
%score_rankup_progress%   # Progreso hacia siguiente rango (ej: 67.3%)
%score_rankup_progress_bar% # Barra visual (ej: ██████▓▓▓▓)
```

### 📱 Redes Sociales
```
%score_discord%           # Usuario de Discord
%score_instagram%         # Usuario de Instagram  
%score_github%            # Usuario de GitHub
%score_tiktok%            # Usuario de TikTok
%score_twitch%            # Canal de Twitch
%score_kick%              # Canal de Kick
%score_youtube%           # Canal de YouTube
%score_social_count%      # Cantidad configuradas (ej: 3)
%score_has_socials%       # Tiene alguna configurada (true/false)
```

### 🔄 Estados de Redes Sociales (Para GUIs)
```
%score_discord_status%    # ✓ usuario#1234 o ✗ No configurado
%score_instagram_status%  # ✓ @mi_usuario o ✗ No configurado
%score_github_status%     # ✓ mi_github o ✗ No configurado
%score_tiktok_status%     # ✓ @mi_tiktok o ✗ No configurado
%score_twitch_status%     # ✓ mi_canal o ✗ No configurado
%score_kick_status%       # ✓ mi_kick o ✗ No configurado
%score_youtube_status%    # ✓ mi_canal o ✗ No configurado
```

### ⚡ Sistema de Género
```
%score_gender_cooldown%   # Tiempo para cambiar género (ej: 3d 5h)
%score_can_change_gender% # Puede cambiar género (true/false)
```

### 📊 Estadísticas Online
```
%score_player_level%      # Nivel de experiencia actual
%score_player_health%     # Salud actual (redondeada)
%score_player_food%       # Nivel de hambre actual
```

---

## 💡 Ejemplos de Uso

### 🏷️ TAB/Scoreboard
```yaml
lines:
  - "&6Nivel: &f%score_farming_level% &7| &6XP: &f%score_farming_xp%"
  - "&bRango: &f%score_rank_display%"
  - "&aProgreso: &f%score_rankup_progress%"
  - "&eBanco: &f%score_banked_xp%&7/&f%score_bank_capacity%"
```

### 💬 Chat/Prefijos
```yaml
format: "&7[%score_rank_display%&7] &f%player_name%: %message%"
```

### 📋 GUIs/Menús
```yaml
rank_info:
  material: DIAMOND
  name: "&6Tu Rango Actual"
  lore:
    - "&fRango: &e%score_rank_display%"
    - "&fSiguiente: &a%score_next_rank%"
    - "&fProgreso: &b%score_rankup_progress%"
    - ""
    - "%score_rankup_progress_bar%"
```

### 🌟 Scoreboard Completo
```yaml
scoreboard:
  title: "&6&lMI SERVIDOR"
  lines:
    - ""
    - "&6❤ &fSalud: &c%score_player_health%&7/&c20"
    - "&6🍖 &fHambre: &e%score_player_food%&7/&e20"
    - ""
    - "&6🌾 &fAgricultura: &aLv.%score_farming_level%"
    - "&6⛏ &fMinería: &bLv.%score_mining_level%"
    - ""
    - "&6🏆 &fRango: &e%score_rank_display%"
    - "&6📈 &fProgreso: &a%score_rankup_progress%"
    - ""
    - "&6💰 &fBanco: &f%score_banked_xp%"
    - "&6💎 &fCapacidad: &f%score_bank_capacity%"
    - ""
    - "&7servidor.com"
```

---

## ⚠️ Notas Importantes

- **Valores por defecto:** Si no hay datos, se muestran valores seguros (`0`, `No establecido`, etc.)
- **Manejo de errores:** En caso de error, se muestra `Error` o valores de respaldo
- **Rendimiento:** Optimizados para uso frecuente sin impacto en el servidor
- **Tiempo real:** Los valores se actualizan automáticamente sin necesidad de reinicios
- **Timeout:** Placeholders de rankup tienen timeout de 2 segundos para evitar lag

---

## 🔗 Plugins Compatibles

✅ **TAB** - Listas de jugadores y scoreboards  
✅ **AnimatedScoreboard** - Scoreboards animados  
✅ **CMI** - Sistema de información  
✅ **EssentialsX** - Formatos de chat  
✅ **LuckPerms** - Prefijos y sufijos  
✅ **Vault** - Sistemas de economía

---

*Para más información sobre configuración avanzada, consulta la [documentación completa](docs/placeholders.md).*