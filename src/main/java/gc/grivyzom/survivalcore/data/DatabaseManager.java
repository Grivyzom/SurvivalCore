package gc.grivyzom.survivalcore.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseManager {
    private final String url;
    private final String username;
    private final String password;
    private final Gson gson = new Gson();
    private final Map<String, UserData> cache = new ConcurrentHashMap<>();

    // SQL para creación de tablas
    private static final String SQL_CREATE_USERS =
            "CREATE TABLE IF NOT EXISTS users (" +
                    "uuid CHAR(36) NOT NULL PRIMARY KEY, " +
                    "nombre VARCHAR(64) NOT NULL, " +
                    "cumpleanos DATE, " +
                    "genero ENUM('Masculino','Femenino','Otro') DEFAULT 'Otro', " +
                    "pais VARCHAR(64), " +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    private static final String SQL_CREATE_STATS =
            "CREATE TABLE IF NOT EXISTS user_stats (" +
                    "uuid CHAR(36) NOT NULL PRIMARY KEY, " +
                    "farming_level INT UNSIGNED NOT NULL DEFAULT 1, " +
                    "farming_xp BIGINT UNSIGNED NOT NULL DEFAULT 0, " +    // Cambiado a BIGINT
                    "mining_level INT UNSIGNED NOT NULL DEFAULT 1, " +
                    "mining_xp BIGINT UNSIGNED NOT NULL DEFAULT 0, " +     // Cambiado a BIGINT
                    "FOREIGN KEY (uuid) REFERENCES users(uuid) ON DELETE CASCADE" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";


    private static final String SQL_CREATE_ABILITIES =
            "CREATE TABLE IF NOT EXISTS user_abilities (" +
                    "id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                    "uuid CHAR(36) NOT NULL, " +
                    "ability_name VARCHAR(100) NOT NULL, " +
                    "level INT UNSIGNED NOT NULL DEFAULT 1, " +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                    "UNIQUE KEY uk_ability (uuid, ability_name), " +
                    "FOREIGN KEY (uuid) REFERENCES users(uuid) ON DELETE CASCADE" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    private static final String SQL_CREATE_MASTERIES =
            "CREATE TABLE IF NOT EXISTS user_masteries (" +
                    "id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                    "uuid CHAR(36) NOT NULL, " +
                    "mastery_name VARCHAR(100) NOT NULL, " +
                    "level INT UNSIGNED NOT NULL DEFAULT 1, " +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                    "UNIQUE KEY uk_mastery (uuid, mastery_name), " +
                    "FOREIGN KEY (uuid) REFERENCES users(uuid) ON DELETE CASCADE" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    // Ahora incluye capacity_xp
    private static final String SQL_CREATE_BANK = "CREATE TABLE IF NOT EXISTS xp_bank ("
            + "uuid CHAR(36) NOT NULL PRIMARY KEY, "
            + "banked_xp BIGINT UNSIGNED NOT NULL DEFAULT 0, "
            + "capacity_xp BIGINT UNSIGNED NOT NULL DEFAULT 170000, " // 2 500 niveles
            + "FOREIGN KEY (uuid) REFERENCES users(uuid) ON DELETE CASCADE"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";



    public DatabaseManager(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
        // Crear esquema
        try (Connection conn = DriverManager.getConnection(url, username, password);
             Statement stmt = conn.createStatement()) {
            stmt.execute(SQL_CREATE_USERS);
            stmt.execute(SQL_CREATE_STATS);
            stmt.execute(SQL_CREATE_ABILITIES);
            stmt.execute(SQL_CREATE_MASTERIES);
            stmt.execute(SQL_CREATE_BANK);
            try (ResultSet rs = stmt.executeQuery(
                    "SHOW COLUMNS FROM xp_bank LIKE 'capacity_xp'")) {
                if (!rs.next()) {
                    stmt.execute(
                            "ALTER TABLE xp_bank " +
                                    "ADD COLUMN capacity_xp BIGINT UNSIGNED NOT NULL DEFAULT 68000"
                    );
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error al crear tablas: " + e.getMessage());
        }
        // Cargar cache inicial
        loadCache();
    }

    private Connection newConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    // Carga usuarios, stats, habilidades y masteries en memoria
    public void loadCache() {
        String sql = "SELECT * FROM users";
        try (Connection conn = newConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String uuid = rs.getString("uuid");
                UserData data = new UserData(
                        uuid,
                        rs.getString("nombre"),
                        rs.getDate("cumpleanos") != null ? rs.getDate("cumpleanos").toString() : null,
                        rs.getString("genero"),
                        rs.getString("pais")
                );
                loadStats(data);
                loadAbilities(data);
                loadMasteries(data);
                cache.put(uuid, data);
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error al cargar cache: " + e.getMessage());
        }
    }

    private void loadStats(UserData data) throws SQLException {
        String sql = "SELECT farming_level, farming_xp, mining_level, mining_xp FROM user_stats WHERE uuid = ?";
        try (Connection conn = newConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, data.getUuid());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    data.setFarmingLevel(rs.getInt("farming_level"));
                    data.setFarmingXP(rs.getLong("farming_xp"));    // Cambiado a getLong
                    data.setMiningLevel(rs.getInt("mining_level"));
                    data.setMiningXP(rs.getLong("mining_xp"));      // Cambiado a getLong
                }
            }
        }
    }

    private void loadAbilities(UserData data) throws SQLException {
        String sql = "SELECT ability_name, level FROM user_abilities WHERE uuid = ?";
        try (Connection conn = newConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, data.getUuid());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    data.getAbilities().put(rs.getString("ability_name"), rs.getInt("level"));
                }
            }
        }
    }

    private void loadMasteries(UserData data) throws SQLException {
        String sql = "SELECT mastery_name, level FROM user_masteries WHERE uuid = ?";
        try (Connection conn = newConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, data.getUuid());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    data.getMasteryLevels().put(rs.getString("mastery_name"), rs.getInt("level"));
                }
            }
        }
    }

    public UserData getUserData(String uuid) {
        if (cache.containsKey(uuid)) return cache.get(uuid);
        OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
        UserData data = new UserData(uuid, op.getName(), null, "Otro", null);
        saveUserData(data);
        cache.put(uuid, data);
        return data;
    }

    public void saveUserData(UserData data) {
        String upsertUser =
                "INSERT INTO users (uuid,nombre,cumpleanos,genero,pais) VALUES (?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE nombre=VALUES(nombre), cumpleanos=VALUES(cumpleanos), genero=VALUES(genero), pais=VALUES(pais)";
        String upsertStats =
                "INSERT INTO user_stats (uuid,farming_level,farming_xp,mining_level,mining_xp) VALUES (?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE farming_level=VALUES(farming_level), farming_xp=VALUES(farming_xp), mining_level=VALUES(mining_level), mining_xp=VALUES(mining_xp)";
        String deleteAbilities = "DELETE FROM user_abilities WHERE uuid = ?";
        String insertAbility  = "INSERT INTO user_abilities (uuid,ability_name,level) VALUES (?,?,?)";
        String deleteMasteries = "DELETE FROM user_masteries WHERE uuid = ?";
        String insertMastery   = "INSERT INTO user_masteries (uuid,mastery_name,level) VALUES (?,?,?)";

        try (Connection conn = newConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pu = conn.prepareStatement(upsertUser)) {
                pu.setString(1, data.getUuid());
                pu.setString(2, data.getNombre());
                pu.setDate(3, data.getCumpleaños() != null ? Date.valueOf(data.getCumpleaños()) : null);
                pu.setString(4, data.getGenero());
                pu.setString(5, data.getPais());
                pu.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(upsertStats)) {
                ps.setString(1, data.getUuid());
                ps.setInt(2, data.getFarmingLevel());
                ps.setLong(3, data.getFarmingXP());      // Cambiado a setLong
                ps.setInt(4, data.getMiningLevel());
                ps.setLong(5, data.getMiningXP());       // Cambiado a setLong
                ps.executeUpdate();
            }
            try (PreparedStatement pd = conn.prepareStatement(deleteAbilities)) {
                pd.setString(1, data.getUuid()); pd.executeUpdate();
            }
            try (PreparedStatement pa = conn.prepareStatement(insertAbility)) {
                for (Map.Entry<String,Integer> e : data.getAbilities().entrySet()) {
                    pa.setString(1, data.getUuid());
                    pa.setString(2, e.getKey());
                    pa.setInt(3, e.getValue());
                    pa.addBatch();
                }
                pa.executeBatch();
            }
            try (PreparedStatement pd = conn.prepareStatement(deleteMasteries)) {
                pd.setString(1, data.getUuid()); pd.executeUpdate();
            }
            try (PreparedStatement pm = conn.prepareStatement(insertMastery)) {
                for (Map.Entry<String,Integer> e : data.getMasteryLevels().entrySet()) {
                    pm.setString(1, data.getUuid());
                    pm.setString(2, e.getKey());
                    pm.setInt(3, e.getValue());
                    pm.addBatch();
                }
                pm.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error al guardar datos del usuario " + data.getUuid() + ": " + e.getMessage());
        }
    }

    /** Devuelve XP almacenada. */
    public long getBankedXp(String uuid) {
        String sql = "SELECT banked_xp FROM xp_bank WHERE uuid = ?";
        try (Connection conn = newConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("banked_xp");
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error al obtener xp_bank: " + e.getMessage());
        }
        return 0;
    }

    /** Devuelve la capacidad máxima (en puntos XP). */
    public long getBankCapacity(String uuid) {
        String sql = "SELECT capacity_xp FROM xp_bank WHERE uuid = ?";
        try (Connection conn = newConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("capacity_xp");
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error getBankCapacity: " + e.getMessage());
        }
        return 68_000L;
    }

    /** Actualiza capacidad del banco (para upgrades). */
    public boolean upgradeBankCapacity(String uuid, long newCapXp) {
        String sql = "INSERT INTO xp_bank (uuid, banked_xp, capacity_xp) VALUES (?,0,?) "
                + "ON DUPLICATE KEY UPDATE capacity_xp = VALUES(capacity_xp)";
        try (Connection conn = newConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setLong(2, newCapXp);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error upgradeBankCapacity: " + e.getMessage());
            return false;
        }
    }

    /**
     * Suma XP al banco del jugador de forma segura.
     * @return true si la actualización tuvo éxito, false en caso de cualquier error.
     */
    /** Guarda XP de forma transaccional, evitando pasar de capacity. */
    public boolean updateBankedXp(String uuid, long amount) {
        try (Connection conn = newConnection()) {
            conn.setAutoCommit(false);

            long banked, capacity;
            try (PreparedStatement sel = conn.prepareStatement(
                    "SELECT banked_xp, capacity_xp FROM xp_bank WHERE uuid = ? FOR UPDATE")) {
                sel.setString(1, uuid);
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) {
                        banked   = rs.getLong("banked_xp");
                        capacity = rs.getLong("capacity_xp");
                    } else {
                        // crea fila si no existe
                        banked = 0; capacity = 68_000L;
                        try (PreparedStatement ins = conn.prepareStatement(
                                "INSERT INTO xp_bank (uuid, banked_xp, capacity_xp) VALUES (?,?,?)")) {
                            ins.setString(1, uuid);
                            ins.setLong(2, banked);
                            ins.setLong(3, capacity);
                            ins.executeUpdate();
                        }
                    }
                }
            }

            if (banked + amount > capacity) {
                conn.rollback();
                return false;
            }

            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE xp_bank SET banked_xp = banked_xp + ? WHERE uuid = ?")) {
                upd.setLong(1, amount);
                upd.setString(2, uuid);
                upd.executeUpdate();
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error updateBankedXp: " + e.getMessage());
            return false;
        }
    }


    /**
     * Resta XP del banco de forma segura.
     * @return true si la resta tuvo éxito (había suficiente XP), false si no.
     */
    public boolean withdrawBankedXp(String uuid, long amount) {
        String sql =
                "UPDATE xp_bank SET banked_xp = banked_xp - ? " +
                        "WHERE uuid = ? AND banked_xp >= ?";
        try (Connection conn = newConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, amount);
            ps.setString(2, uuid);
            ps.setLong(3, amount);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error al retirar xp_bank: " + e.getMessage());
            return false;
        }
    }

    public long addXpCapped(String uuid, long amountXp) {
        try (Connection conn = newConnection()) {
            conn.setAutoCommit(false);

            long banked, capacity;
            try (PreparedStatement sel = conn.prepareStatement(
                    "SELECT banked_xp, capacity_xp FROM xp_bank WHERE uuid = ? FOR UPDATE")) {
                sel.setString(1, uuid);
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) {
                        banked   = rs.getLong("banked_xp");
                        capacity = rs.getLong("capacity_xp");
                    } else {
                        // fila nueva
                        banked = 0; capacity = 68_000L;
                        try (PreparedStatement ins = conn.prepareStatement(
                                "INSERT INTO xp_bank (uuid, banked_xp, capacity_xp) VALUES (?,?,?)")) {
                            ins.setString(1, uuid);
                            ins.setLong(2, banked);
                            ins.setLong(3, capacity);
                            ins.executeUpdate();
                        }
                    }
                }
            }

            long freeXp   = capacity - banked;
            long toStore  = Math.min(amountXp, freeXp);
            if (toStore <= 0) { conn.rollback(); return 0; }

            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE xp_bank SET banked_xp = banked_xp + ? WHERE uuid = ?")) {
                upd.setLong(1, toStore);
                upd.setString(2, uuid);
                upd.executeUpdate();
            }
            conn.commit();
            return toStore;          // cuánta XP se guardó realmente
        } catch (SQLException ex) {
            Bukkit.getLogger().severe("addXpCapped: " + ex.getMessage());
            return 0;
        }
    }

    /**
     * Cierra recursos si fuese necesario (no hay conexión persistente).
     */
    public void close() {
        // No hay conexión persistente para cerrar
    }
}
