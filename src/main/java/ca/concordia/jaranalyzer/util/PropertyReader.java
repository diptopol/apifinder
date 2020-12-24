package ca.concordia.jaranalyzer.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * @author Diptopol
 * @since 11/7/2020 1:19 PM
 */
public class PropertyReader {

    private static Properties properties;

    static {
        InputStream inputStream;
        properties = new Properties();
        String fileName = "config.properties";

        try {
            inputStream = PropertyReader.class.getClassLoader().getResourceAsStream(fileName);
            properties.load(inputStream);

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }
}
