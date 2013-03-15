
# Project 313

A configurable Java JAR loader and bootstrap.

## A Java bootstrap JAR loader

This library demonstrates a solution for the common application requirement to load classes from JARs found in a lib directory, thus avoiding the need to manually specify classes on the command line.  User application code is decoupled from the external mechanism used to define the classpath and start the application.

The loader uses system properties to specify configuration.  These properties can be loaded from an external properties file, they are then added to the System properties object, before the target application class is started.

The loader will discover JARs and resources in the `lib` sub-directory of the specified application `<appname>.home` directory and load them into a new classloader, before using this classloader to initialise the classname specified in the `<appname>.init` property.  Various class types and methods are handled automatically for the init class, (e.g. `Thread`, `Runnable`, a static `main` method).

### Quickstart

Relying on the default properties file name, the following will start an application:

    java -cp boot-loader.jar org.pidster.boot.loader.Main arg1 arg2 arg3

The properties file, (default name `boot.properties`), should contain:

    boot.prefix=myapp
    myapp.home=/path/to/myapp
    myapp.init=org.myapp.Main

### Alternative Configuration

The following system properties will also configure the loader:

    java -Dboot.prefix=myapp -Dmyapp.home=/path/to/myapp -Dmyapp.init=org.myapp.Main -cp boot-loader.jar org.pidster.boot.loader.Main arg1 arg2 arg3

An alternative name for the properties file can be specified:

    java -Dboot.properties.file=myboot.properties -cp boot-loader.jar org.pidster.boot.loader.Main arg1 arg2 arg3
