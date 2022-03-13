package ca.concordia.jaranalyzer.util;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class Utility {

	private static final Logger logger = LoggerFactory.getLogger(Utility.class);

    public static void downloadUsingStream(String urlStr, String file) throws IOException {
        URL url = new URL(urlStr);
        BufferedInputStream bis = new BufferedInputStream(url.openStream());
        FileOutputStream fis = new FileOutputStream(file);
        byte[] buffer = new byte[1024];
        int count = 0;
        while ((count = bis.read(buffer, 0, 1024)) != -1) {
            fis.write(buffer, 0, count);
        }
        fis.close();
        bis.close();
    }

    public static String getJarName(String url) {
        url = url.replace('\\', '/');
        return url.substring(url.lastIndexOf('/') + 1);
    }

    public static List<String> getFiles(String directory, String type) {
        List<String> jarFiles = new ArrayList<String>();
        File dir = new File(directory);
        if (dir.listFiles() != null)
            for (File file : dir.listFiles()) {
                if (file.isDirectory()) {
                    jarFiles.addAll(getFiles(file.getAbsolutePath(), type));
                } else if (file.getAbsolutePath().toLowerCase().endsWith((type.toLowerCase()))) {
                    jarFiles.add(file.getAbsolutePath());
                }
            }
        return jarFiles;
    }

    public static Path getJarStoragePath() {
        return Path.of(PropertyReader.getProperty("jar.storage.directory"))
                .resolve(PropertyReader.getProperty("jar.storage.filename"));
    }

    public static Element getchild(Element classElement, String name) {
        try {
            List<Element> elementChildren = classElement.getChildren();

            for (int temp = 0; temp < elementChildren.size(); ++temp) {
                Element element = elementChildren.get(temp);
                if (element.getName().equals(name)) {
                    return element;
                }
            }
        } catch (Exception ex) {
            logger.error("No child found under:" + name, ex);
        }

        return null;
    }

    public static Set<String> listOfJavaProjectLibraryFromEffectivePom(String pomContent) {
        Set<String> versionLibraries = new HashSet<>();
        Set<String> projectVersions = new HashSet<>();

        try {
            SAXBuilder saxBuilder = new SAXBuilder();
            Document document = saxBuilder.build(new ByteArrayInputStream(pomContent.getBytes(StandardCharsets.UTF_8)));

            Element root = document.getRootElement();

            // get public properties for library version
            HashMap<String, String> propertiesList = new HashMap<>();
            try {
                Element properties = getchild(root, "properties");
                List<Element> propertiesListNode = properties.getChildren();

                for (int temp = 0; temp < propertiesListNode.size(); temp++) {
                    Element property = propertiesListNode.get(temp);
                    propertiesList.put("${" + property.getName() + "}", property.getValue());
                }
            } catch (Exception ex) {
				logger.error("No child found", ex);
            }

            List<Element> projectNodes = root.getChildren();

            if (!projectNodes.stream().allMatch(e -> e.getName().equals("project"))) {
                projectNodes = Arrays.asList(root);
            }

            for (Element project : projectNodes) {
                String grId = getchild(project, "groupId").getValue();
                String artID = getchild(project, "artifactId").getValue();
                String vrsn = getchild(project, "version").getValue();
                projectVersions.add(String.join(":", grId, artID, vrsn));


                Element dependencyManagement = getchild(project, "dependencyManagement");
                Element dependencies = null;
                if (dependencyManagement != null) {
                    dependencies = getchild(dependencyManagement, "dependencies");
                } else {
                    //dependencies may lives under root
                    dependencies = getchild(project, "dependencies");
                }

                if (dependencies != null) {
                    List<Element> dependencytList = dependencies.getChildren();
                    for (int temp = 0; temp < dependencytList.size(); temp++) {
                        Element dependency = dependencytList.get(temp);
                        List<Element> librariesList = dependency.getChildren();
                        String groupId = "";
                        String artifactId = "";
                        String version = "";
                        for (int temp1 = 0; temp1 < librariesList.size(); temp1++) {
                            Element libraryInfo = librariesList.get(temp1);
                            if (libraryInfo.getName().equals("groupId"))
                                groupId = libraryInfo.getValue();
                            if (libraryInfo.getName().equals("artifactId"))
                                artifactId = libraryInfo.getValue();
                            if (libraryInfo.getName().equals("version")) {
                                version = libraryInfo.getValue();
                                if (version.startsWith("${")) {
                                    version = propertiesList.get(version);
                                }
                            }
                        }
                        String libraryLink = groupId + ":" + artifactId + ":" + version;
                        versionLibraries.add(libraryLink);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error", e);
        }

        return versionLibraries.stream().filter(x -> !projectVersions.contains(x)).collect(Collectors.toSet());
    }

}
