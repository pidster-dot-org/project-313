/**
 * 
 */
package org.pidster.boot.loader;

import static java.util.jar.Attributes.Name.SPECIFICATION_VERSION;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * @author pidster
 *
 */
public class Version {

    public static final String getVersion() {

        String className = Version.class.getSimpleName() + ".class";
        String classPath = Version.class.getResource(className).toString();

        if (!classPath.startsWith("jar")) {
            return "<unknown> (not a jar)";
        }

        String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) + "/META-INF/MANIFEST.MF";

        try (InputStream is = new URL(manifestPath).openStream()) {
            Manifest manifest = new Manifest(is);
            Attributes attr = manifest.getMainAttributes();
            return attr.getValue(SPECIFICATION_VERSION);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
