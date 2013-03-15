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

/**
 * @author pidster
 *
 */
public class Loader implements Runnable {

    private static final String PROPERTIES = System.getProperty("application.properties");

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

        // Check properties file exists, or throw exception
        File propertiesFile = new File(PROPERTIES);
        if (!propertiesFile.exists()) {
            throw new IllegalArgumentException(String.format("'%s' is not a valid properties file", PROPERTIES));
        }

        loadSystemProperties(propertiesFile);

        // find application name and derive HOME directory
        String applicationName = System.getProperty("application.name");
        String homeDir = System.getProperty(String.format("%s.home"), applicationName);
        File home = new File(homeDir);
        if (!home.exists()) {
            throw new IllegalArgumentException(String.format("'%s' is not a valid home directory for", PROPERTIES));
        }

        // Create application classpath from JARs
        URL[] array = jarFinder(homeDir);
        Class<?> init = load(applicationName, array);

        // type detection and initialization
        try {
            initialize(applicationName, init);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Load application properties from a file into System properties
     *
     * @param propertiesFile
     *
     */
    private void loadSystemProperties(File propertiesFile) {
        try (InputStream in = new FileInputStream(propertiesFile)) {
            Properties properties = new Properties();
            properties.load(in);
            System.setProperties(properties);
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException(e);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
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
     * @param applicationName
     * @param array
     * @return class
     */
    private Class<?> load(String applicationName, URL[] array) {
        String initClassName = System.getProperty(String.format("%s.init"), applicationName);

        try (URLClassLoader loader = new URLClassLoader(array)) {
            Thread.currentThread().setContextClassLoader(loader);
            return loader.loadClass(initClassName);
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalArgumentException("Classloading problem when loading libs", e);
        }
    }

    /**
     * @param applicationName
     * @param initClass
     */
    private void initialize(String applicationName, Class<?> initClass) throws InterruptedException {
        if (Thread.class.isAssignableFrom(initClass)) {
            startThread(applicationName, initClass);
        }
        else if (Runnable.class.isAssignableFrom(initClass)) {
            startRunnable(applicationName, initClass);
        }
        else if (Callable.class.isAssignableFrom(initClass)) {
            startCallable(applicationName, initClass);
        }
        else {
            startMain(initClass);
        }
    }

    /**
     * @param applicationName
     * @param raw
     */
    private void startThread(String applicationName, Class<?> raw) throws InterruptedException {
        try {
            Class<? extends Thread> clazz = raw.asSubclass(Thread.class);
            Class<?>[] threadArgs = { String.class };
            Constructor<? extends Thread> constructor = clazz.getConstructor(threadArgs);

            Thread thread = constructor.newInstance(applicationName);
            thread.start();
            thread.join();
        } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new LoaderException(e);
        }
    }

    /**
     * @param applicationName
     * @param raw
     */
    private void startRunnable(String applicationName, Class<?> raw) throws InterruptedException {
        try {
            Class<? extends Runnable> clazz = raw.asSubclass(Runnable.class);
            Runnable runnable = clazz.newInstance();

            Thread thread = new Thread(runnable, applicationName);
            thread.start();
            thread.join();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new LoaderException(e);
        }
    }

    /**
     * @param applicationName
     * @param raw
     */
    private void startCallable(String applicationName, Class<?> raw) throws InterruptedException {
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
