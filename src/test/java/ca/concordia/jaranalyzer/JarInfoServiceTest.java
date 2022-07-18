package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.service.JarInfoService;
import ca.concordia.jaranalyzer.util.DbUtils;
import org.junit.Test;

/**
 * @author Diptopol
 * @since 7/13/2022 8:27 PM
 */
public class JarInfoServiceTest {

    @Test
    public void testJarInfoExists() {
        JarInfoService jarInfoService = new JarInfoService();

        boolean isExists = jarInfoService.isJarExists("org.jfree", "jfreechart", "1.5.3");

        assert isExists;
    }

}
