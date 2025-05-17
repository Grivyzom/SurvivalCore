package gc.grivyzom.survivalcore.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class JSONManager {
    private File archivo;
    private Map<String, UserData> cache;
    private Gson gson;

    public JSONManager(File archivo) {
        this.archivo = archivo;
        this.gson = new Gson();
        this.cache = new HashMap<>();
        cargarDatos();
    }

    // Carga los datos desde el archivo JSON a la caché.
    public void cargarDatos() {
        if (!archivo.exists()) {
            try {
                archivo.createNewFile();
                guardarDatos();
            } catch (Exception e) {
                Bukkit.getLogger().severe("Error al crear el archivo JSON: " + e.getMessage());
            }
        }
        try (FileReader reader = new FileReader(archivo)) {
            Type type = new TypeToken<Map<String, UserData>>() {}.getType();
            Map<String, UserData> datos = gson.fromJson(reader, type);
            cache = datos != null ? datos : new HashMap<>();
        } catch (Exception e) {
            Bukkit.getLogger().severe("Error al cargar datos desde JSON: " + e.getMessage());
        }
    }

    // Guarda los datos de la caché en el archivo JSON.
    public void guardarDatos() {
        try (FileWriter writer = new FileWriter(archivo)) {
            gson.toJson(cache, writer);
        } catch (Exception e) {
            Bukkit.getLogger().severe("Error al guardar datos en JSON: " + e.getMessage());
        }
    }

    public UserData getUserData(String uuid) {
        return cache.get(uuid);
    }

    public void setUserData(String uuid, UserData data) {
        cache.put(uuid, data);
        guardarDatos();
    }
}
