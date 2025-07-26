# 📚 **Documentación - Sistema de Macetas Mágicas**

## 🎯 **Introducción**

El **Sistema de Macetas Mágicas** es una funcionalidad avanzada que permite a los jugadores colocar macetas especiales que pueden contener flores mágicas. Estas macetas irradian efectos de área continuos basados en el tipo de flor plantada y el nivel de la maceta.

---

## 🏗️ **Arquitectura del Sistema**

### **Estructura de Clases**

```
📦 gc.grivyzom.survivalcore.flowerpot
├── 🏺 MagicFlowerPot.java          # Factoría de macetas mágicas
├── 🎛️ MagicFlowerPotManager.java   # Gestor de macetas activas
└── 📊 MagicFlowerPotData.java      # Datos de macetas individuales

📦 gc.grivyzom.survivalcore.commands
└── ⚡ MagicFlowerPotCommand.java    # Comandos del sistema

📦 gc.grivyzom.survivalcore.listeners
└── 👂 MagicFlowerPotListener.java   # Eventos del sistema
```

---

## 🔧 **Componentes Principales**

### **1. MagicFlowerPot.java - Factoría de Macetas**

**Propósito**: Crear, manipular y validar macetas mágicas como ítems.

**Métodos Principales**:
```java
// Crear una maceta mágica de nivel específico
ItemStack createMagicFlowerPot(int level)

// Verificar si un ítem es una maceta mágica
boolean isMagicFlowerPot(ItemStack item)

// Obtener/establecer propiedades de la maceta
int getPotLevel(ItemStack item)
int getPotRange(ItemStack item)
String getContainedFlower(ItemStack item)
ItemStack setContainedFlower(ItemStack pot, String flowerId)

// Gestión de upgrades
ItemStack upgradePot(ItemStack pot)
boolean canUpgrade(ItemStack pot)
```

**Metadatos Persistentes**:
- `is_magic_flowerpot`: Marca como maceta mágica
- `pot_level`: Nivel de la maceta (1-5)
- `pot_range`: Rango de efectos en bloques
- `contained_flower`: ID de la flor contenida
- `pot_id`: ID único de la maceta

### **2. MagicFlowerPotManager.java - Gestor Central**

**Propósito**: Controlar todas las macetas activas en el servidor, aplicar efectos y gestionar partículas.

**Funcionalidades Clave**:

**Registro de Macetas**:
```java
// Registrar maceta colocada
void registerPot(Location location, String potId, int level, String flowerId)

// Desregistrar maceta removida
void unregisterPot(Location location)

// Verificar existencia
boolean hasPotAt(Location location)
```

**Sistema de Efectos**:
- **Tarea de Efectos**: Ejecuta cada 3 segundos (60 ticks)
- **Tarea de Partículas**: Ejecuta cada 1 segundo (20 ticks)
- **Detección de Jugadores**: Usa `getNearbyEntities` con filtro
- **Aplicación de Efectos**: Basada en tipo de flor y nivel de maceta

**Tipos de Efectos Disponibles**:
| Flor | Efecto | Duración | Escalable |
|------|--------|----------|-----------|
| `love_flower` | Regeneración | 4s | ✅ Sí |
| `healing_flower` | Curación Instantánea | Instantáneo | ❌ No |
| `speed_flower` | Velocidad | 4s | ✅ Sí |
| `strength_flower` | Fuerza | 4s | ✅ Sí |
| `night_vision_flower` | Visión Nocturna | 12s | ❌ No |

### **3. MagicFlowerPotData.java - Datos de Maceta**

**Propósito**: Almacenar información sobre macetas colocadas.

**Propiedades**:
```java
private final String potId;           // ID único
private final int level;              // Nivel (1-5)
private String flowerId;              // ID de flor actual
private long lastUpdate;              // Timestamp última actualización
private final Location location;      // Ubicación en el mundo
private long placedTime;              // Timestamp de colocación
```

**Métodos Útiles**:
```java
boolean hasFlower()                   // ¿Tiene flor plantada?
long getActiveTime()                  // Tiempo activa en ms
int getEffectRange()                  // Rango calculado según nivel
```

### **4. MagicFlowerPotListener.java - Gestión de Eventos**

**Propósito**: Manejar todas las interacciones de los jugadores con las macetas.

**Eventos Manejados**:

**Colocación de Macetas** (`BlockPlaceEvent`):
- ✅ Verificación de permisos (`survivalcore.flowerpot.place`)
- ✅ Validación de ubicación (superficie sólida + espacio libre)
- ✅ Límites por jugador
- ✅ Copia de metadatos del ítem al bloque
- ✅ Registro en el manager
- ✅ Efectos visuales y sonoros

**Rotura de Macetas** (`BlockBreakEvent`):
- ✅ Verificación de permisos (`survivalcore.flowerpot.break`)
- ✅ Cancelación de drops vanilla
- ✅ Creación de ítem con metadatos preservados
- ✅ Devolución de flor por separado si existe
- ✅ Desregistro del manager

**Interacción con Macetas** (`PlayerInteractEvent`):
- ✅ Click derecho con mano vacía → Mostrar información
- ✅ Click derecho con flor mágica → Plantar flor
- ✅ Reemplazo automático de flores existentes
- ✅ Verificación de permisos (`survivalcore.flowerpot.use`)

### **5. MagicFlowerPotCommand.java - Comandos**

**Propósito**: Proporcionar interfaz de comandos para gestionar el sistema.

**Subcomandos Disponibles**:

```bash
/flowerpot give <jugador> [nivel] [cantidad]
```
- **Permiso**: `survivalcore.flowerpot.give`
- **Función**: Dar macetas mágicas a jugadores
- **Parámetros**:
    - `jugador`: Nombre del jugador objetivo
    - `nivel`: 1-5 (opcional, predeterminado: 1)
    - `cantidad`: 1-64 (opcional, predeterminado: 1)

```bash
/flowerpot info
```
- **Permiso**: `survivalcore.flowerpot.use`
- **Función**: Mostrar información de la maceta en mano
- **Información mostrada**:
    - Nivel y rango actual
    - ID único
    - Flor contenida (si existe)
    - Estado de la maceta
    - Información de upgrade disponible

```bash
/flowerpot stats
```
- **Permiso**: `survivalcore.flowerpot.admin`
- **Función**: Mostrar estadísticas del sistema
- **Estadísticas mostradas**:
    - Total de macetas activas
    - Macetas con flores vs vacías
    - Distribución por nivel

```bash
/flowerpot reload
```
- **Permiso**: `survivalcore.flowerpot.admin`
- **Función**: Recargar configuración del sistema

```bash
/flowerpot help
```
- **Permiso**: Ninguno
- **Función**: Mostrar ayuda del sistema

---

## ⚙️ **Sistema de Configuración**

### **Archivo: `magic_flowerpot.yml`**

**Configuración General**:
```yaml
settings:
  max_level: 5                    # Nivel máximo de macetas
  max_pots_per_player: 10         # Límite por jugador
  enable_effects: true            # Habilitar efectos
  enable_particles: true          # Habilitar partículas
  effect_interval: 60             # Intervalo efectos (ticks)
  particle_interval: 20           # Intervalo partículas (ticks)
```

**Configuración por Nivel**:
```yaml
levels:
  1:
    range: 3                      # Rango en bloques
    effect_multiplier: 1.0        # Multiplicador de efectos
  2:
    range: 5
    effect_multiplier: 1.2
  # ... hasta nivel 5
```

**Configuración de Efectos**:
```yaml
effects:
  base_duration: 80               # Duración base (ticks)
  duration_per_level: 1.2         # Multiplicador por nivel
  
  flowers:
    love_flower:
      effect_type: "REGENERATION"
      base_amplifier: 0
      scales_with_pot_level: true
```

---

## 🎮 **Mecánicas de Juego**

### **Flujo de Uso Básico**

1. **Obtención**:
    - Admin da maceta: `/flowerpot give <jugador> [nivel]`
    - Jugador recibe ítem con metadatos persistentes

2. **Colocación**:
    - Jugador coloca maceta en superficie sólida
    - Sistema verifica permisos y restricciones
    - Maceta se registra y comienza a mostrar partículas "idle"

3. **Plantado de Flor**:
    - Jugador hace click derecho con flor mágica
    - Sistema valida flor y reemplaza si es necesario
    - Maceta activa efectos de área continuos

4. **Efectos Activos**:
    - Cada 3 segundos aplica efectos a jugadores en rango
    - Cada 1 segundo muestra partículas temáticas
    - Efectos varían según tipo de flor y nivel de maceta

5. **Información**:
    - Click derecho con mano vacía muestra detalles
    - Comando `/flowerpot info` para análisis detallado

6. **Rotura**:
    - Jugador rompe maceta (con permisos)
    - Devuelve maceta con metadatos + flor por separado
    - Sistema desregistra automáticamente

### **Sistema de Niveles**

| Nivel | Rango | Multiplicador | Partículas Extra |
|-------|-------|---------------|------------------|
| 1 | 3 bloques | 1.0x | +3 |
| 2 | 5 bloques | 1.2x | +4 |
| 3 | 7 bloques | 1.4x | +5 |
| 4 | 9 bloques | 1.6x | +6 |
| 5 | 11 bloques | 2.0x | +7 |

### **Sistema de Partículas**

**Maceta Vacía**:
- Tipo: `VILLAGER_HAPPY`
- Cantidad: 2 partículas
- Indica que espera una flor

**Maceta Activa**:
- Partículas específicas por flor
- Cantidad: 3 + nivel de maceta
- Partículas de área ocasionales (30% probabilidad)

**Partículas por Flor**:
- `love_flower`: ❤️ `HEART`
- `healing_flower`: 😊 `VILLAGER_HAPPY`
- `speed_flower`: ⚡ `CRIT`
- `strength_flower`: ✨ `CRIT_MAGIC`
- `night_vision_flower`: 📚 `ENCHANTMENT_TABLE`

---

## 🔐 **Sistema de Permisos**

### **Permisos Básicos**
```yaml
survivalcore.flowerpot.use          # Comandos básicos
survivalcore.flowerpot.place        # Colocar macetas
survivalcore.flowerpot.break        # Romper macetas  
survivalcore.flowerpot.interact     # Plantar flores
```

### **Permisos Administrativos**
```yaml
survivalcore.flowerpot.give         # Dar macetas
survivalcore.flowerpot.admin        # Comandos admin
survivalcore.flowerpot.unlimited    # Sin límites
survivalcore.flowerpot.bypass       # Saltarse restricciones
```

### **Permisos Consolidados**
```yaml
survivalcore.flowerpot.*            # Todos los básicos
survivalcore.flowerpot.admin.*      # Todos los admin
```

---

## 🛡️ **Seguridad y Validaciones**

### **Validaciones de Colocación**
- ✅ Superficie sólida debajo
- ✅ Espacio libre arriba
- ✅ Permisos del jugador
- ✅ Límites por jugador/chunk
- ✅ Mundos permitidos

### **Validaciones de Interacción**
- ✅ Verificación de maceta mágica registrada
- ✅ Validación de flores mágicas (metadatos)
- ✅ Permisos específicos por acción
- ✅ Cooldowns y límites

### **Seguridad de Datos**
- ✅ Metadatos persistentes en ítems
- ✅ Validación de integridad al cargar
- ✅ Limpieza automática de macetas destruidas
- ✅ Logs de todas las acciones importantes

### **Optimizaciones de Rendimiento**
- ✅ `ConcurrentHashMap` para acceso seguro
- ✅ Verificación de chunks cargados
- ✅ Filtrado eficiente de jugadores cercanos
- ✅ Tareas asíncronas para efectos

---

## 🔧 **Integración con Main.java**

### **Campos Agregados**
```java
private MagicFlowerPotManager magicFlowerPotManager;
```

### **Inicialización**
```java
// En initManagers()
magicFlowerPotManager = new MagicFlowerPotManager(this);
```

### **Registro de Comandos**
```java
// En registerCommands()
registerCommand("flowerpot", new MagicFlowerPotCommand(this));
```

### **Registro de Listeners**
```java
// En registerListeners()  
pm.registerEvents(new MagicFlowerPotListener(this), this);
```

### **Cleanup**
```java
// En onDisable()
if (magicFlowerPotManager != null) magicFlowerPotManager.shutdown();
```

### **Getter Público**
```java
public MagicFlowerPotManager getMagicFlowerPotManager() {
    return magicFlowerPotManager;
}
```

---

## 📝 **Comandos en plugin.yml**

```yaml
flowerpot:
  description: Gestión de Macetas Mágicas
  usage: |
    /flowerpot give <jugador> [nivel] [cantidad] - Dar macetas
    /flowerpot info - Ver información de maceta en mano
    /flowerpot stats - Ver estadísticas (admin)
    /flowerpot reload - Recargar configuración (admin)
    /flowerpot help - Mostrar ayuda
  aliases: [maceta, magicpot, mpot]
  permission: survivalcore.flowerpot.use
  permission-message: "§cNo tienes permisos para usar este comando."
```

---

## 🚀 **Preparación para Flores Mágicas**

El sistema está **completamente preparado** para la implementación de flores mágicas:

### **Detección Automática**
- ✅ Verificación por metadatos `is_magic_flower`
- ✅ Obtención de ID de flor `flower_id`
- ✅ Sistema de efectos configurable por tipo

### **Efectos Preparados**
- ✅ 5 tipos de flores predefinidas
- ✅ Sistema escalable para nuevos tipos
- ✅ Efectos configurables por archivo YAML

### **Integración Lista**
- ✅ Listener detecta flores automáticamente
- ✅ Manager aplica efectos según configuración
- ✅ Partículas y sonidos específicos por flor

---

## 🎯 **Próximos Pasos Sugeridos**

1. **Implementar Flores Mágicas**:
    - Crear clase `MagicFlower.java`
    - Implementar listener para creación
    - Configurar recetas de crafting

2. **Sistema de Crafting**:
    - Recetas para macetas de diferentes niveles
    - Recetas para flores mágicas
    - Integración con sistema de experiencia

3. **Persistencia Avanzada**:
    - Guardar macetas en base de datos
    - Sistema de respaldo automático
    - Carga al reiniciar servidor

4. **Optimizaciones**:
    - Sistema de chunks cargados
    - Límites dinámicos por TPS
    - Caching de efectos frecuentes

---

## ✅ **Estado Actual**

- ✅ **Sistema Base**: Completamente implementado
- ✅ **Comandos**: Funcionales con autocompletado
- ✅ **Permisos**: Sistema completo implementado
- ✅ **Eventos**: Todos los casos cubiertos
- ✅ **Efectos**: Sistema básico operativo
- ✅ **Configuración**: Archivo completo preparado
- ⏳ **Flores Mágicas**: Preparado para implementación
- ⏳ **Crafting**: Pendiente de desarrollo

El sistema está **listo para producción** y **preparado para expansión** con las flores mágicas.