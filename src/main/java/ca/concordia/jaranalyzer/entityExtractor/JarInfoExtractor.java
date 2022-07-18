package ca.concordia.jaranalyzer.entityExtractor;

import ca.concordia.jaranalyzer.entity.ClassInfo;
import ca.concordia.jaranalyzer.entity.JarInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

/**
 * @author Diptopol
 * @since 7/13/2022 6:47 PM
 */
public class JarInfoExtractor {

    private static final Logger logger = LoggerFactory.getLogger(JarInfoExtractor.class);

    public static JarInfo getJarInfo(String groupId, String artifactId, String version, JarFile jarFile) {
        logger.info("Processing JarInfo of {}:{}:{}", groupId, artifactId, version);

        JarInfo jarInfo = new JarInfo(groupId, artifactId, version);

        List<ClassInfo> classInfoList = ClassInfoExtractor.getClassInfoList(jarFile);
        jarInfo.setClassInfoList(classInfoList);

        return jarInfo;
    }

    public static JarInfo getJarInfo(String groupId, String artifactId, String version, ZipFile jmodFile) {
        logger.info("Processing JarInfo of {}:{}:{}", groupId, artifactId, version);

        JarInfo jarInfo = new JarInfo(groupId, artifactId, version);

        List<ClassInfo> classInfoList = ClassInfoExtractor.getClassInfoList(jmodFile);
        jarInfo.setClassInfoList(classInfoList);

        return jarInfo;
    }

}
