/**
 * 
 */
package org.pidster.boot.loader;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Set;

/**
 * @author pidster
 *
 */
public class ClassLoaderFactory {

    public static ClassLoader create(File dir, ClassLoader parent) {
        URL[] urls = new URL[0];

        try {
            File[] files = dir.listFiles();
            Set<URL> items = new HashSet<URL>();
            for (File file : files) {
                URL url = file.toURI().toURL();
                items.add(url);
            }

            urls = new URL[items.size()];
            urls = items.toArray(urls);

        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Bad URL found when loading resources", e);
        }

        return new URLClassLoader(urls);
    }

}
