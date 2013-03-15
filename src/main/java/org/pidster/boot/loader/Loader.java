/*
 * Copyright 2013 The Original Author
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.pidster.boot.loader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author pidster
 *
 */
public class Loader implements Runnable {

    private static final String PROPERTIES = System.getProperty("boot.properties.file", "boot.properties");

    private static final Logger log = Logger.getLogger(Loader.class.getName());

    private final String[] args;

    /**
     * @param args
     * 
     */
    public Loader(String[] args) {
        this.args = args;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {

        String version = Version.getVersion();
        log.info("Starting " + Loader.class.getSimpleName() + " v" + version);

        // Check properties file exists, or throw exception
        File propertiesFile = new File(PROPERTIES);
        if (propertiesFile.exists() && propertiesFile.isFile()) {
            loadSystemProperties(propertiesFile);
            // Minimal properties can be set inline, the file isn't a strict requirement
        }
        else if (!propertiesFile.isFile()) {
            log.warning(String.format("'%s' is not a valid properties file", PROPERTIES));
        }

        // find boot prefix
        String bootPrefix = System.getProperty("boot.prefix");
        if (bootPrefix == null) {
            throw new IllegalStateException("The system property 'boot.prefix' MUST be set");
        }

        log.config("boot.prefix=" + bootPrefix);

        for (int i=0; i<args.length; i++) {
            String argName = String.format("%s.init.arg%d", bootPrefix, i);
            System.setProperty(argName, args[i]);
        }

        logProperties(bootPrefix);

        // derive HOME directory from prefix property
        String homeDir = System.getProperty(String.format("%s.home", bootPrefix));
        if (homeDir == null) {
            throw new IllegalStateException(String.format("The system property '%s' MUST be set", String.format("%s.home", bootPrefix)));
        }

        File home = new File(homeDir);
        if (!home.exists() || !home.isDirectory()) {
            throw new IllegalArgumentException(String.format("'%s' is not a valid home directory", homeDir));
        }

        // Create application classpath from JARs
        URL[] array = jarFinder(homeDir);
        Class<?> init = load(bootPrefix, array);

        // type detection and initialization
        try {
            initialize(bootPrefix, init);
        } catch (InterruptedException e) {
            log.log(Level.WARNING, "Interrupted...", e);
        }
    }

    /**
     * Load application properties from a file into System properties
     *
     * @param propertiesFile
     *
     */
    private void loadSystemProperties(File propertiesFile) {

        log.config("Loading properties from " + propertiesFile.getAbsolutePath());

        try (InputStream in = new FileInputStream(propertiesFile)) {
            Properties properties = new Properties();
            properties.load(in);
            System.getProperties().putAll(properties);
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException(e);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * @param bootPrefix
     */
    private void logProperties(String bootPrefix) {
        Set<String> propertyNames = System.getProperties().stringPropertyNames();
        StringBuilder s = new StringBuilder();
        s.append("Application properties:\n");
        for (String propertyName : propertyNames) {
            if (propertyName.startsWith(bootPrefix)) {
                s.append(" ");
                s.append(propertyName);
                s.append("=");
                s.append(System.getProperty(propertyName));
                s.append("\n");
            }
        }
        log.config(s.toString());
    }

    /**
     * @param homeDir
     * @return array of URL
     */
    private URL[] jarFinder(String homeDir) {
        File libDir = new File(homeDir, "lib");
        File[] jars = libDir.listFiles(new JarFileFilter());

        URL[] array = new URL[0];

        try {
            Set<URL> jarURLs = new HashSet<URL>();
            for (File jar : jars) {
                URL url = jar.toURI().toURL();
                jarURLs.add(url);
            }

            array = new URL[jarURLs.size()];
            array = jarURLs.toArray(array);

        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Bad URL found when loading jars", e);
        }

        return array;
    }

    /**
     * @param bootPrefix
     * @param array
     * @return class
     */
    private Class<?> load(String bootPrefix, URL[] array) {
        String initClassName = System.getProperty(String.format("%s.init", bootPrefix));

        log.info("Initialising and starting: " + initClassName);

        try (URLClassLoader loader = new URLClassLoader(array)) {
            Thread.currentThread().setContextClassLoader(loader);
            return loader.loadClass(initClassName);
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalArgumentException("Classloading problem when loading libs", e);
        }
    }

    /**
     * @param bootPrefix
     * @param initClass
     */
    private void initialize(String bootPrefix, Class<?> initClass) throws InterruptedException {
        if (Thread.class.isAssignableFrom(initClass)) {
            startThread(bootPrefix, initClass);
        }
        else if (Runnable.class.isAssignableFrom(initClass)) {
            startRunnable(bootPrefix, initClass);
        }
        else if (Callable.class.isAssignableFrom(initClass)) {
            startCallable(bootPrefix, initClass);
        }
        else {
            startMain(initClass);
        }
    }

    /**
     * @param bootPrefix
     * @param raw
     */
    private void startThread(String bootPrefix, Class<?> raw) throws InterruptedException {
        try {
            Class<? extends Thread> clazz = raw.asSubclass(Thread.class);
            Class<?>[] threadArgs = { String.class };
            Constructor<? extends Thread> constructor = clazz.getConstructor(threadArgs);

            Thread thread = constructor.newInstance(bootPrefix);
            thread.start();
            thread.join();
        } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new LoaderException(e);
        }
    }

    /**
     * @param bootPrefix
     * @param raw
     */
    private void startRunnable(String bootPrefix, Class<?> raw) throws InterruptedException {
        try {
            Class<? extends Runnable> clazz = raw.asSubclass(Runnable.class);
            Runnable runnable = clazz.newInstance();

            Thread thread = new Thread(runnable, bootPrefix);
            thread.start();
            thread.join();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new LoaderException(e);
        }
    }

    /**
     * @param bootPrefix
     * @param raw
     */
    private void startCallable(String bootPrefix, Class<?> raw) throws InterruptedException {
        try {
            @SuppressWarnings("rawtypes")
            Class<? extends Callable> clazz = raw.asSubclass(Callable.class);

            Callable<?> callable = clazz.newInstance();
            callable.call();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new LoaderException(e);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @param clazz
     */
    private void startMain(Class<?> clazz) throws InterruptedException {
        try {
            Method main = clazz.getMethod("main", new Class<?>[] { String[].class });
            main.invoke(null, (Object[]) args);

        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new LoaderException(e);
        }
    }

}
