# Takari Maven Lifecycle

[![Maven Central](https://img.shields.io/maven-central/v/io.takari.maven.plugins/takari-lifecycle-plugin.svg?label=Maven%20Central)](https://search.maven.org/artifact/io.takari.maven.plugins/takari-lifecycle-plugin)
[![Verify](https://github.com/takari/takari-lifecycle/actions/workflows/ci.yml/badge.svg)](https://github.com/takari/takari-lifecycle/actions/workflows/ci.yml)
[![Reproducible Builds](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/jvm-repo-rebuild/reproducible-central/master/content/io/takari/maven/plugins/takari-lifecycle-plugin/badge.json)](https://github.com/jvm-repo-rebuild/reproducible-central/blob/master/content/io/takari/maven/plugins/takari-lifecycle-plugin/README.md)


To learn about the Takari Maven Lifecycle you can take a look here:

<http://takari.io/book/40-lifecycle.html>

To use it, declare the plugin as extension in your POM:

```
      <plugin>
        <groupId>io.takari.maven.plugins</groupId>
        <artifactId>takari-lifecycle-plugin</artifactId>
        <version>2.1.5</version>
        <extensions>true</extensions>
      </plugin>
```

Build time requirement of this project and projects using takari-lifecycle-plugin is Java11 (since 2.0.9), 
but the project can produce even Java 7 byte code by using the "release" compiler flag.
