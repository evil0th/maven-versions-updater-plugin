package com.sahara.intellij.plugin.mvn.utils;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.junit.Test;

/**
 * @author liao
 * Create on 2020/5/12 16:43
 */
public class MavenTest {

    /**
     * test ComparableVersion
     * {@link org.apache.maven.artifact.versioning.ComparableVersion#main}
     */
    @Test
    public void test() {
        String[] args = {"1.2.7", "1.2-SNAPSHOT"};
        System.out.println("Display parameters as parsed by Maven (in canonical form) and comparison result:");
        ComparableVersion prev = null;
        int i = 1;
        for (String version : args) {
            ComparableVersion c = new ComparableVersion(version);
            if (prev != null) {
                int compare = prev.compareTo(c);
                System.out.println("   " + prev.toString() + ' '
                        + ((compare == 0) ? "==" : ((compare < 0) ? "<" : ">")) + ' ' + version);
            }
            System.out.println(String.valueOf(i++) + ". " + version + " == " + c.getCanonical());
            prev = c;
        }
    }
}
