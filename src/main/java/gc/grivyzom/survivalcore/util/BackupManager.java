package gc.grivyzom.survivalcore.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BackupManager {
    private final JavaPlugin plugin;
    private final String backupDir;
    private final String dbHost, dbName, dbUser, dbPassword;
    private final int dbPort;

    public BackupManager(JavaPlugin plugin, String dbHost, int dbPort, String dbName, String dbUser, String dbPassword) {
        this.plugin = plugin;
        this.backupDir = plugin.getDataFolder().getAbsolutePath() + "/backups/";
        this.dbHost = dbHost;
        this.dbPort = dbPort;
        this.dbName = dbName;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;

        File backupFolder = new File(backupDir);
        if (!backupFolder.exists()) {
            backupFolder.mkdirs();
        }
    }

    public void performBackup() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String backupName = "backup_" + timestamp + ".sql";
            File backupFile = new File(backupDir, backupName);

            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "mysqldump",
                        "-h", dbHost,
                        "-P", String.valueOf(dbPort),
                        "-u", dbUser,
                        "-p" + dbPassword,
                        dbName
                );

                pb.redirectOutput(backupFile);
                pb.redirectErrorStream(true);

                Process process = pb.start();
                int processComplete = process.waitFor();

                if (processComplete == 0) {
                    Bukkit.getLogger().info("Backup creado exitosamente: " + backupName);
                    manageBackupFiles();
                } else {
                    Bukkit.getLogger().severe("El proceso de backup terminó con errores.");
                }

            } catch (IOException | InterruptedException e) {
                Bukkit.getLogger().severe("Error al crear el backup: " + e.getMessage());
            }
        });
    }


    private void manageBackupFiles() {
        File dir = new File(backupDir);
        File[] backups = dir.listFiles((d, name) -> name.endsWith(".sql"));

        if (backups != null && backups.length > 2) {
            // Ordenar por fecha y eliminar el más antiguo
            File oldest = backups[0];
            for (File f : backups) {
                if (f.lastModified() < oldest.lastModified()) {
                    oldest = f;
                }
            }
            if (oldest.delete()) {
                Bukkit.getLogger().info("Backup antiguo eliminado: " + oldest.getName());
            }
        }
    }
}
