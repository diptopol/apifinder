package ca.concordia.apifinder.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;

/**
 * @author Diptopol
 * @since 11/7/2020 1:19 PM
 */
public class PropertyReader {

    private static final Logger logger = LoggerFactory.getLogger(PropertyReader.class);

    private static final Properties properties;

    static {
        logger.info("Load Config properties");

        properties = new Properties();
        String fileName = "config.properties";

        try {
            properties.load(PropertyReader.class.getClassLoader().getResourceAsStream(fileName));
        } catch (IOException ex) {
            logger.error("Couldn't load config properties", ex);
        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }
}
