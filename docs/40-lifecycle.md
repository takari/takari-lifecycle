# The Takari Lifecycle

TEAM includes an optimized replacement for the Maven default lifecycle. The
Takari Lifecycle Plugin provides you access to a number of significant
advantages:

1. One plugin with a small set of dependencies provides equivalent functionality
   to five plugins with a large set of transitive dependencies. This reduces the
   download times to retrieve the needed components as well as the storage space
   requirements in your repositories.

2. The configuration for a number of aspects for your build is centralized to
   one plugin and simplified.

3. The reduced complexity of the plugins involved in the build, results in higher
   build performance on the command line and in the IDE.

4. The build is fully incremental, not only for your source code, but also for
   your resources, which in turn again speeds up development cycle and build
   times.

5. Dedicated IDE support brings the advantages of the lifecycle to your daily
   development work.

## Overview

The Takari lifecycle is implemented by a single Maven plugin that acts as build
extension and replaces the following Maven plugins:

* Maven Resources Plugin
* Maven Compiler Plugin
* Maven Jar Plugin
* Maven Install Plugin
* Maven Deploy Plugin

You can take advantage of all these replacements in your builds or pick and
choose.

## Activating the Lifecycle

In order to take advantage of the improved lifecycle, you have to activate it by
adding the takari-lifecycle-plugin as a build extension.

```markup
<build>
  <plugins>
    <plugin>
      <groupId>io.takari.maven.plugins</groupId>
      <artifactId>takari-lifecycle-plugin</artifactId>
      <extensions>true</extensions>
    </plugin>
  </plugins>
</build>
```

This is all the configuration necessary for projects with packaging `pom`. The
lifecycle bindings are altered so that the `takari-lifecycle-plugin` replaces the
install and deploy plugins in the respective lifecycle phases.

[//]: # (TBD is this enough docs for the pom packaging or do we need to mention anything else, related to shadow POM packaging Jason mentioned to talk to Igor about,)

Additionally projects with packaging `jar` have to be switched to use the
`takari-jar` packaging.

```markup
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.takari.lifecycle.its.basic</groupId>
  <artifactId>basic</artifactId>
  <version>1.0</version>
  <packaging>takari-jar</packaging>
```

The `takari-jar` packaging defines new lifecycle bindings for your build and
replaces the default plugins for the `jar` packaging with their Takari
counterparts. The Maven resources, compiler, jar, install and deploy plugins
are replaced. Using the `takari-jar` packaging is the easiest way to adopt all
the new features.

Alternatively you can use only a specific part, e.g. the new compiler goals only,
by using the default `jar` packaging with the `takari-lifecycle-plugin` added.
If you choose this approach, you will need to deactivate (or skip) the plugin you
want to replace to avoid interference problems between the takari lifeycle and
the default plugin.

An example for using the `jar` packaging, but replacing the default compiler
with the takari lifecycle support can be configured by adding the
`takari-lifecycl-plugin` and explicitly configuring the goals you want to
execute:

```markup
<build>
  <plugins>
    <plugin>
      <groupId>io.takari.maven.plugins</groupId>
      <artifactId>takari-lifecycle-plugin</artifactId>
      <executions>
        <execution>
          <id>compile</id>
          <goals>
            <goal>compile</goal>
            <goal>testCompile</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
```

and deactivating the Maven compiler plugin:

```markup
<build>
  ....
  <pluginManagement>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.3</version>
        <configuration>
          <skip>true</skip>
          <skipMain>true</skipMain>
        </configuration>
      </plugin>
    </plugins>
  </pluginManagement>
```

In a similar manner you can configure to use the other goals of the takari
lifecycle plugin, replacing specific parts of your default build.

## Observing the Takari Lifecycle on the Log

Once you have activated the Takari lifecycle, the build log will show all the
invocations of the specific goals. For a project with packaging `pom` this
will mainly affect the install and deploy invocations

```
[INFO] --- takari-lifecycle-plugin:x.y.z:install (default-install) @ pom-only ---
[INFO] Performing incremental build
[INFO] Installing .../pom.xml to /~/.m2/repository/.../SNAPSHOT/pom-only-1.0.0-SNAPSHOT.pom
[INFO]
[INFO] --- takari-lifecycle-plugin:x.y.z:deploy (default-deploy) @ pom-only ---
[INFO] Performing incremental build
...
Uploaded: http://.../1.0.0-SNAPSHOT/pom-only-1.0.0-20140731.183927-2.pom (2 KB at 8 KB/sec)
```

A project with packaging `takari-jar` will log the lifecycle plugin invocations
for the resources, compilation and packaging related goals as well.

Upon first invocation each goal will be performed as usual, executing all steps
e.g., compiling all files or copying and filtering all resources.

```
[INFO] --- takari-lifecycle-plugin:x.y.z:process-resources (default-process-resources) @ simple-jar ---
[INFO] Previous incremental build state does not exist, performing full build
```

Subsequent builds, however will be able to access the information about prior
builds and execute incrementally.

```
$ mvn compile
[INFO] --- takari-lifecycle-plugin:x.y.z:process-resources (default-process-resources) @ simple-jar ---
[INFO] Performing incremental build
[INFO]
[INFO] --- takari-lifecycle-plugin:x.y.z:compile (default-compile) @ simple-jar ---
[INFO] Performing incremental build
[INFO] Skipped compilation, all 1 sources are up to date
```

Note that a `clean` invocation removes the state information and re-establishes
a clean slate.

## Configuring Resource Filtering and Processing

The Takari lifecycle supports the resource configuration just like the Maven
resources plugin e.g.,

```
<build>
  <resources>
    <resource>
      <directory>src/main/resources</directory>
      <filtering>true</filtering>
    </resource>
  </resources>
```

In contrast to the Maven resources plugin it however supports incremental
resource processing in terms of copying and filtering. It detects any property
changes as well as any resource changes and incrementally reprocesses the
affected files only as shown in the log

```
[INFO] --- takari-lifecycle-plugin:x.y.z:process-resources (default-process-resources) @ simple-jar ---
[INFO] Performing incremental build
```

Property changes are sources from the pom file as well as the user settings
file.

## Compiler Configuration

The Takari lifecycle compiler integration replaces the Maven compiler plugin to
compile main and test source code. It will automatically be used if you use
project packaging of `takari-jar`.

The compiler integration supports a number of configuration parameters. The
source and target parameters allow you to set the respective parameters for the
compiler.

For example, the following configuration can be used to compile Java 1.8 source
code to Java 1.8 compatible class files

```
<plugins>
  <plugin>
    <groupId>io.takari.maven.plugins</groupId>
    <artifactId>takari-lifecycle-plugin</artifactId>
    <configuration>
      <source>1.8</source>
    </configuration>
```

Alternatively the property `maven.compiler.source`. Following is a list of all
compiler related configuration options

`compilerId (maven.compiler.compilerId)`
: The default value of 'javac' will invoke the Java compiler of the installed
JDK. 'forked-javac' will fork a new process or 'jdt' will use the Eclipse
JDT compiler.

`debug (maven.compiler.debug)`
: Configures the amount of debug information in the output class files. The
default is 'all' or 'true' and includes all available debug information. The
opposite is 'none' or 'false', excluding everything. Fine grained control is
possible by using a comma separated list of parameters including 'source'
(source file debugging information), 'lines' (line number debugging information)
and 'vars' (local variable debugging information).

`encoding (encoding)`
: The -encoding argument for the Java compiler.

`meminitial (maven.compiler.meminitial)`
: The initial size, in megabytes, of the memory allocation pool e.g., '64'.

`maxmem (maven.compiler.maxmem)`
: The maximum size, in megabytes, of the memory allocation pool, e.g, '128'.

`source (maven.compiler.source)`
: The Java source level argument passed to the compiler.

`target (maven.compiler.source)`
: The Java target level argument passed to the compiler.

`verbose (maven.compiler.verbose)`
: Controls the verbosity of the compiler output, defaulting to 'false'. 'true'
activates verbose output.

`showWarnings (maven.compiler.showWarnings)`
: toggles the display of warning messages from the compiler

The `compile` goal supports specifying `includes` and `excludes` and the
`testCompile` supports the equivalent `testIncludes` and `testExcludes`.

## Note on annotation processing

Please note that in the Takari Lifecycle annotation processing is turned off by default, so be aware of the `<proc/>` element when you need annotation processing. If, for example, you're using an annotation processor like Lombok then make sure you setup the configuration appropriately like the example below.

```
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.takari.project</groupId>
  <artifactId>project-with-lombok</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <packaging>takari-jar</packaging>

  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <version>1.14.8</version>
      <scope>provided</scope>
    </dependency>
  </dependencies>
  
  <build>
    <plugins>
      <plugin>
        <groupId>io.takari.maven.plugins</groupId>
        <artifactId>takari-lifecycle-plugin</artifactId>
        <version>1.10.1</version>
        <extensions>true</extensions>
        <configuration>
          <proc>proc</proc>
        </configuration>
      </plugin>
    </plugins>
  </build>  
</project>
```

## Enforcing Dependency Usage during Compilation

The Takari lifecycle allows you to make the compilation of your sources stricter regarding the usage of dependencies
and the packages in dependencies.

The Maven compiler plugin adds all packages from the dependencies to the compilation and test compilation classpaths
taking the rules for `scope` into account. This means that even internal classes of a dependency, that are not meant to
be used become available and can be used.

In addition it adds all the transitive dependencies of the declared dependencies to the classpath. While this behaviour
is convenient to some degree, more complex projects experience effects that are considered surprising due to this rule.
E.g. a project can declare a simple dependency with a large tree of transitive dependencies and developers can start
using these classes without explicitly declaring the dependencies. When this project is then used potentially unwanted
transitive dependencies appear in the consuming project. This causes larger deployment components like a larger
WAR file with bigger footprint in terms of startup time, contained components to manage regarding security and license
characteristics and so on.

The Takari lifecycle introduces a new configuration parameter called `accessRulesViolation`, which is set to `ignore` by
default. You can activate it by setting it to `error` in the plugin configuration. In addition you need to use
the `jdt` compiler:

```
<plugin>
  <groupId>io.takari.maven.plugins</groupId>
  <artifactId>takari-lifecycle-plugin</artifactId>
  <extensions>true</extensions>
  <configuration>
    <accessRulesViolation>error</accessRulesViolation>
    <compilerId>jdt</compilerId>
  </configuration>
```

Once you have activated the validation, access rule violations will cause a build error. Transitive
dependencies are no longer available on the classpath and usage of any classes from them will result in compilation
failures. You will need to declare all used dependencies in your project explicitly and therefore make a conscious
decision about their usage.

All packages from these dependencies are made available on the classpath, unless they are packaged as OSGi bundles. for
OSGi bundles, the OSGI metadata in the JAR manifest file is honoured. Only packages declared as exported in the
manifest of a dependency are available on the compilation classpath.

If you are working with a project and want to take advantage of this feature, but do not want to go through the effort
of creating an OSGi bundle, you can declare the exported packages of a project for the access rule validation in an `export-package`
file. This file has to be located in `META-INF/takari/` and contains package names that should be exported. Each line
should contain one package name and all exported packages have to be added to the file. In a typical Maven project you
can achieve this by adding the file as `src/main/resources/META-INF/takari/export-package`.

The simplest way to create this file is to use the Takari lifecycle. It will automatically create the file by using the `export-package`
goal in the `process-classess` lifecycle phase. By default, all packages, except packages named `**/internal/**` or `**/impl/**`
, are exported automatically. The `exportIncludes` and `exportExcludes` configuration parameters can be used to further 
control:

```
<plugin>
  <groupId>io.takari.maven.plugins</groupId>
  <artifactId>takari-lifecycle-plugin</artifactId>
  <extensions>true</extensions>
  <configuration>
    <exportExcludes>
      <exportExclude>**/private/**</exportExclude>
      <exportExclude>**/legacy/**</exportExclude>
    </exportExcludes>
  </configuration>
```

Using the configuration above any packages in `**/internal/**` or `**/impl/**` are exported, since these default values 
are overridden. If you still want them to be excluded, you can simply add these patterns to the configuration.

## Packaging jars Archives

Creating source and test

Part of the jar mojo configuration

```
<plugin>
  <groupId>io.takari.maven.plugins</groupId>
  <artifactId>takari-lifecycle-plugin</artifactId>
  <extensions>true</extensions>
  <configuration>
    <sourceJar>true</sourceJar>
    <testJar>true</testJar>
  </configuration>
```

* mainJar
* sourceJar
* testJar
* archive (and all the nested stuff)


## Installing and Deploying Artifacts

The takari lifecycle plugin transparently replaces the install and the deploy
plugins as you can see from this sample output of running deploy on a `pom`
packing project.

```
[INFO] --- takari-lifecycle-plugin:x.y.z:install (default-install) @ pom-only ---
[INFO] Installing .../pom.xml to ~/.m2/repository/.../pom-only-1.0.pom
[INFO]
[INFO] --- takari-lifecycle-plugin:x.y.z:deploy (default-deploy) @ pom-only ---
Uploading: http://.../pom-only-1.0.pom
Uploaded: http://.../pom-only-1.0.pom (2 KB at 4.5 KB/sec)
Downloading: http://.../maven-metadata.xml
Uploading: http://.../maven-metadata.xml
Uploaded: http://.../maven-metadata.xml (311 B at 1.6 KB/sec)
```

On a jar packaging project the pom and jar files are installed and deployed as usual:

```
[INFO] --- takari-lifecycle-plugin:x.y.z:install (default-install) @ simple-jar ---
[INFO] Installing .../simple-jar-1.0-SNAPSHOT.jar to ~/.m2/repository/.../simple-jar-1.0-SNAPSHOT.jar
[INFO] Installing .../pom.xml to ~/.m2/repository/.../simple-jar-1.0-SNAPSHOT.pom
[INFO]
[INFO] --- takari-lifecycle-plugin:x.y.z:deploy (default-deploy) @ simple-jar ---
Downloading: http://.../maven-metadata.xml
Uploading: http://.../simple-jar-1.0-20140620.221731-1.jar
Uploaded: http://.../simple-jar-1.0-20140620.221731-1.jar (2 KB at 9.2 KB/sec)
Uploading: http://.../simple-jar-1.0-20140620.221731-1.pom
Uploaded: http://.../simple-jar-1.0-20140620.221731-1.pom (2 KB at 13.4 KB/sec)
Downloading: http://.../maven-metadata.xml
Uploading: http://.../maven-metadata.xml
Uploaded: http://.../maven-metadata.xml (781 B at 11.7 KB/sec)
Uploading: http://.../maven-metadata.xml
Uploaded: http://.../maven-metadata.xml (295 B at 4.8 KB/sec)
```

## Installing Eclipse m2e Integration

The incremental build behavior of the takari lifecycle is supported by an
extension to the Maven support for Eclipse, m2e. This extensions will
automatically be installed when you import a Maven project that have
the takari-lifecycle-plugin configured.

Alternatively you can install it manually by choosing Help - Install New
Software and adding another software site using the following URL

```
https://repository.takari.io/content/sites/m2e.extras/takari-team/0.1.0/N/LATEST/
```

Once the available components are loaded, you will be able to select
the Takari Build Lifecycle and proceed with the install through the dialogs.
After a restart of Eclipse the incremental build support will be available.
