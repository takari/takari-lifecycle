# Takari Maven Lifecycle

To learn about the Takari Maven Lifecycle you can take a look here:

<http://takari.io/book/40-lifecycle.html>

To use it, declare the plugin as extension in your POM:

```
      <plugin>
        <groupId>io.takari.maven.plugins</groupId>
        <artifactId>takari-lifecycle-plugin</artifactId>
        <version>2.1.4</version>
        <extensions>true</extensions>
      </plugin>
```

Build time requirement of this project and projects using takari-lifecycle-plugin is Java11 (since 2.0.9), 
but the project can produce even Java 7 byte code by using the "release" compiler flag.
