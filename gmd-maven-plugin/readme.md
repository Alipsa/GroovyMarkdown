[![Maven Central](https://maven-badges.herokuapp.com/maven-central/se.alipsa.gmd/gmd-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/se.alipsa.gmd/gmd-maven-plugin)
[![javadoc](https://javadoc.io/badge2/se.alipsa.gmd/gmd-maven-plugin/javadoc.svg)](https://javadoc.io/doc/se.alipsa.gmd/gmd-maven-plugin)
## Using Gmd in Maven
The gmd-maven-plugin is a maven plugin that allows you to use Gmd in your maven build.
Usage is as follows:
```xml
  <build>
    <plugins>
      <plugin>
        <groupId>se.alipsa.gmd</groupId>
        <artifactId>gmd-maven-plugin</artifactId>
        <version>3.0.1</version>
        <goals><goal>processGmd</goal></goals>
        <configuration>
          <sourceDir>src/test/gmd</sourceDir>
          <targetDir>target/gmd</targetDir>
          <outputType>html</outputType>
        </configuration>
      </plugin>
    </plugins>
  </build>
```
Then you run the plugin with the following command:
```bash
mvn gmd:processGmd
```

Possible configuration parameters are:
- `sourceDir` - the directory where the GMD files are located. Default is `src/main/gmd`
- `targetDir` - the directory where the output files will be created. Default is `target/gmd`
- `outputType` - the type of output file to create. Possible values are `md`, `html`, `pdf`. Default is `md`

If you don't want to run the plugin explicitly, you can add it to an existing lifecycle as follows:
```xml
<build>
  <plugins>
    <plugin>
      <groupId>se.alipsa.gmd</groupId>
      <artifactId>gmd-maven-plugin</artifactId>
      <version>3.0.1</version>
      <executions>
        <execution>
          <phase>compile</phase>
          <goals>
            <goal>processGmd</goal>
          </goals>
        </execution>
      </executions>
      <configuration>
        <sourceDir>src/test/gmd</sourceDir>
        <targetDir>target/gmd</targetDir>
        <outputType>html</outputType>
      </configuration>
    </plugin>
  </plugins>
</build>
```

By doing this, the gmd plugin will run every time you run `mvn compile` or any other lifecycle that includes the compile phase (test, package, verify, install, deploy). Of course `mvn gmd:processGmd` will also work.

