import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public class FileIndexer {

    private static Map<String, List<Path>> hashMap = new HashMap<>();
    private static Set<Path> sourceFiles = new HashSet<>();

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Использование:");
            System.out.println("  java FileIndexer <папка>              - найти дубликаты в папке");
            System.out.println("  java FileIndexer <папка1> <папка2>   - сравнить две папки (проверка бэкапа)");
            return;
        }

        String folder1 = args[0];
        if (args.length == 1) {
            findDuplicates(folder1);
        } else {
            String folder2 = args[1];
            compareFolders(folder1, folder2);
        }
    }

    private static void findDuplicates(String root) throws Exception {
        System.out.println("Сканируем папку: " + root);
        scanFolder(Paths.get(root), true);
        System.out.println("Найдено файлов: " + hashMap.values().stream().mapToInt(List::size).sum());

        boolean hasDup = false;
        for (Map.Entry<String, List<Path>> entry : hashMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                hasDup = true;
                System.out.println("\nДубликаты (хэш: " + entry.getKey() + "):");
                for (Path p : entry.getValue()) {
                    System.out.println("  " + p);
                }
            }
        }
        if (!hasDup) {
            System.out.println("Дубликатов не найдено.");
        }
    }

    private static void compareFolders(String src, String backup) throws Exception {
        System.out.println("Сравниваем папки:");
        System.out.println("  Источник: " + src);
        System.out.println("  Резерв:   " + backup);

        scanFolder(Paths.get(src), false);
        scanFolder(Paths.get(backup), true);

        List<Path> missing = new ArrayList<>();
        for (Path p : sourceFiles) {
            boolean found = false;
            for (List<Path> list : hashMap.values()) {
                for (Path b : list) {
                    if (b.getFileName().equals(p.getFileName()) && Files.size(b) == Files.size(p)) {
                        found = true;
                        break;
                    }
                }
                if (found) break;
            }
            if (!found) missing.add(p);
        }

        if (missing.isEmpty()) {
            System.out.println("\nРезервная копия полная – все файлы есть.");
        } else {
            System.out.println("\nОтсутствуют в резерве (" + missing.size() + " файлов):");
            for (Path p : missing) {
                System.out.println("  " + p);
            }
        }
    }

    private static void scanFolder(Path dir, boolean storeHashes) throws Exception {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            System.out.println("Ошибка: папка не существует или это не директория: " + dir);
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    scanFolder(entry, storeHashes);
                } else {
                    if (storeHashes) {
                        String hash = getHash(entry);
                        hashMap.computeIfAbsent(hash, k -> new ArrayList<>()).add(entry);
                    } else {
                        sourceFiles.add(entry);
                    }
                }
            }
        }
    }

    private static String getHash(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] buf = new byte[8192];
        try (InputStream is = Files.newInputStream(file)) {
            int n;
            while ((n = is.read(buf)) != -1) {
                md.update(buf, 0, n);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}