package gc.grivyzom.survivalcore.util;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validador de redes sociales
 * Valida y formatea URLs y nombres de usuario de diferentes redes sociales
 *
 * @author Brocolitx
 */
public class SocialMediaValidator {

    private final Main plugin;
    private final Map<String, List<Pattern>> patterns = new HashMap<>();

    public SocialMediaValidator(Main plugin) {
        this.plugin = plugin;
        loadPatterns();
    }

    /**
     * Carga los patrones de validación desde la configuración
     */
    private void loadPatterns() {
        // Patrones por defecto si no hay configuración

        // Discord
        patterns.put("discord", List.of(
                Pattern.compile("^[a-zA-Z0-9_.]{2,32}#[0-9]{4}$"),  // Usuario#1234
                Pattern.compile("^https?://discord\\.com/users/[0-9]+$"),  // URL de perfil
                Pattern.compile("^@[a-zA-Z0-9_.]{2,32}$"),  // @usuario
                Pattern.compile("^[a-zA-Z0-9_.]{2,32}$")  // usuario simple
        ));

        // Instagram
        patterns.put("instagram", List.of(
                Pattern.compile("^@[a-zA-Z0-9_.]{1,30}$"),  // @usuario
                Pattern.compile("^[a-zA-Z0-9_.]{1,30}$"),  // usuario
                Pattern.compile("^https?://(www\\.)?instagram\\.com/[a-zA-Z0-9_.]+/?$"),  // URL completa
                Pattern.compile("^instagram\\.com/[a-zA-Z0-9_.]+/?$")  // URL sin protocolo
        ));

        // GitHub
        patterns.put("github", List.of(
                Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9-]{0,38}$"),  // usuario (puede tener guiones)
                Pattern.compile("^@[a-zA-Z0-9][a-zA-Z0-9-]{0,38}$"),  // @usuario
                Pattern.compile("^https?://github\\.com/[a-zA-Z0-9][a-zA-Z0-9-]+/?$"),  // URL completa
                Pattern.compile("^github\\.com/[a-zA-Z0-9][a-zA-Z0-9-]+/?$")  // URL sin protocolo
        ));

        // TikTok
        patterns.put("tiktok", List.of(
                Pattern.compile("^@[a-zA-Z0-9_.]{2,24}$"),  // @usuario
                Pattern.compile("^[a-zA-Z0-9_.]{2,24}$"),  // usuario
                Pattern.compile("^https?://(www\\.)?tiktok\\.com/@[a-zA-Z0-9_.]+/?$"),  // URL completa
                Pattern.compile("^tiktok\\.com/@[a-zA-Z0-9_.]+/?$")  // URL sin protocolo
        ));

        // Twitch
        patterns.put("twitch", List.of(
                Pattern.compile("^[a-zA-Z0-9_]{4,25}$"),  // usuario
                Pattern.compile("^@[a-zA-Z0-9_]{4,25}$"),  // @usuario
                Pattern.compile("^https?://(www\\.)?twitch\\.tv/[a-zA-Z0-9_]+/?$"),  // URL completa
                Pattern.compile("^twitch\\.tv/[a-zA-Z0-9_]+/?$")  // URL sin protocolo
        ));

        // Kick
        patterns.put("kick", List.of(
                Pattern.compile("^[a-zA-Z0-9_]{4,25}$"),  // usuario
                Pattern.compile("^@[a-zA-Z0-9_]{4,25}$"),  // @usuario
                Pattern.compile("^https?://kick\\.com/[a-zA-Z0-9_]+/?$"),  // URL completa
                Pattern.compile("^kick\\.com/[a-zA-Z0-9_]+/?$")  // URL sin protocolo
        ));

        // YouTube
        patterns.put("youtube", List.of(
                Pattern.compile("^@[a-zA-Z0-9_.-]{3,30}$"),  // @usuario (handle)
                Pattern.compile("^[a-zA-Z0-9_.-]{3,30}$"),  // usuario
                Pattern.compile("^https?://(www\\.)?youtube\\.com/@[a-zA-Z0-9_.-]+/?$"),  // URL con handle
                Pattern.compile("^https?://(www\\.)?youtube\\.com/(c/|channel/|user/)[a-zA-Z0-9_.-]+/?$"),  // URLs antiguas
                Pattern.compile("^youtube\\.com/@[a-zA-Z0-9_.-]+/?$")  // URL sin protocolo
        ));
    }

    /**
     * Valida si un valor es válido para una red social específica
     */
    public boolean isValid(String network, String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        List<Pattern> networkPatterns = patterns.get(network.toLowerCase());
        if (networkPatterns == null) {
            return false;
        }

        String trimmedValue = value.trim();

        // Verificar contra todos los patrones de la red
        for (Pattern pattern : networkPatterns) {
            if (pattern.matcher(trimmedValue).matches()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Formatea el valor para almacenamiento consistente
     */
    public String format(String network, String value) {
        if (value == null) return null;

        String trimmed = value.trim();

        switch (network.toLowerCase()) {
            case "discord" -> {
                // Si es solo un nombre de usuario sin #, dejarlo como está
                if (!trimmed.contains("#") && !trimmed.contains("discord.com")) {
                    if (trimmed.startsWith("@")) {
                        return trimmed;
                    }
                    return trimmed;
                }
                return trimmed;
            }

            case "instagram" -> {
                // Si no tiene @, agregarlo
                if (!trimmed.startsWith("@") && !trimmed.contains("instagram.com")) {
                    return "@" + trimmed;
                }
                // Si es URL, extraer el usuario
                if (trimmed.contains("instagram.com/")) {
                    String username = extractFromUrl(trimmed, "instagram.com/");
                    return "@" + username;
                }
                return trimmed;
            }

            case "github" -> {
                // Si es URL, extraer el usuario
                if (trimmed.contains("github.com/")) {
                    return extractFromUrl(trimmed, "github.com/");
                }
                // Remover @ si lo tiene
                if (trimmed.startsWith("@")) {
                    return trimmed.substring(1);
                }
                return trimmed;
            }

            case "tiktok" -> {
                // Si no tiene @, agregarlo
                if (!trimmed.startsWith("@") && !trimmed.contains("tiktok.com")) {
                    return "@" + trimmed;
                }
                // Si es URL, extraer el usuario
                if (trimmed.contains("tiktok.com/@")) {
                    String username = extractFromUrl(trimmed, "tiktok.com/@");
                    return "@" + username;
                }
                return trimmed;
            }

            case "twitch" -> {
                // Si es URL, extraer el usuario
                if (trimmed.contains("twitch.tv/")) {
                    return extractFromUrl(trimmed, "twitch.tv/");
                }
                // Remover @ si lo tiene
                if (trimmed.startsWith("@")) {
                    return trimmed.substring(1);
                }
                return trimmed;
            }

            case "kick" -> {
                // Si es URL, extraer el usuario
                if (trimmed.contains("kick.com/")) {
                    return extractFromUrl(trimmed, "kick.com/");
                }
                // Remover @ si lo tiene
                if (trimmed.startsWith("@")) {
                    return trimmed.substring(1);
                }
                return trimmed;
            }

            case "youtube" -> {
                // Si es URL con @, mantener el @
                if (trimmed.contains("youtube.com/@")) {
                    String username = extractFromUrl(trimmed, "youtube.com/@");
                    return "@" + username;
                }
                // Si es URL con /c/ o /channel/
                if (trimmed.contains("youtube.com/c/")) {
                    return extractFromUrl(trimmed, "youtube.com/c/");
                }
                if (trimmed.contains("youtube.com/channel/")) {
                    return extractFromUrl(trimmed, "youtube.com/channel/");
                }
                // Si no tiene @, agregarlo para handles modernos
                if (!trimmed.startsWith("@") && !trimmed.contains("youtube.com")) {
                    return "@" + trimmed;
                }
                return trimmed;
            }

            default -> {
                return trimmed;
            }
        }
    }

    /**
     * Extrae el nombre de usuario de una URL
     */
    private String extractFromUrl(String url, String domain) {
        int index = url.indexOf(domain);
        if (index == -1) return url;

        String username = url.substring(index + domain.length());

        // Remover trailing slash si existe
        if (username.endsWith("/")) {
            username = username.substring(0, username.length() - 1);
        }

        // Remover query parameters si existen
        if (username.contains("?")) {
            username = username.substring(0, username.indexOf("?"));
        }

        // Remover anchor si existe
        if (username.contains("#")) {
            username = username.substring(0, username.indexOf("#"));
        }

        return username;
    }

    /**
     * Obtiene una URL clickeable para una red social
     */
    public String getClickableUrl(String network, String value) {
        if (value == null || value.isEmpty()) return null;

        switch (network.toLowerCase()) {
            case "discord" -> {
                // Discord no tiene URLs de perfil públicas consistentes
                return value;
            }
            case "instagram" -> {
                String username = value.startsWith("@") ? value.substring(1) : value;
                return "https://instagram.com/" + username;
            }
            case "github" -> {
                String username = value.startsWith("@") ? value.substring(1) : value;
                return "https://github.com/" + username;
            }
            case "tiktok" -> {
                String username = value.startsWith("@") ? value : "@" + value;
                return "https://tiktok.com/" + username;
            }
            case "twitch" -> {
                String username = value.startsWith("@") ? value.substring(1) : value;
                return "https://twitch.tv/" + username;
            }
            case "kick" -> {
                String username = value.startsWith("@") ? value.substring(1) : value;
                return "https://kick.com/" + username;
            }
            case "youtube" -> {
                if (value.startsWith("@")) {
                    return "https://youtube.com/" + value;
                }
                return "https://youtube.com/@" + value;
            }
            default -> {
                return value;
            }
        }
    }
}