package ca.concordia.jaranalyzer.util;

import io.vavr.control.Try;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Map;

public class FileUtils {

    public static String readFile(Path p){
        return Try.of(() -> new String(Files.readAllBytes(p))).getOrElse("");
    }

    public static Path createFolderIfAbsent(Path p){
        if(!p.toFile().exists())
            Try.of(() -> Files.createDirectories(p)).onFailure(e -> e.printStackTrace());
        return p;
    }

    public static Path materializeFile(Path p, String content){
        createFolderIfAbsent(p.getParent().toAbsolutePath());
        return Try.of(() -> Files.write(p.toAbsolutePath(), content.getBytes(), StandardOpenOption.CREATE))
                .onFailure(e -> e.printStackTrace())
                .getOrElse(p);
    }

    public static void materializeAtBase(Path basePath, Map<Path,String> fileContent) {
        createFolderIfAbsent(basePath);
        fileContent.forEach((k,v) -> materializeFile(basePath.resolve(k), v));
    }

    public static void deleteDirectory(Path p) {
        try {
            Files.walk(p).sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
