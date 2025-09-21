[![Maven Central](https://maven-badges.herokuapp.com/maven-central/se.alipsa.gmd/gmd-gradle-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/se.alipsa.gmd/gmd-gradle-plugin)
[![javadoc](https://javadoc.io/badge2/se.alipsa.gmd/gmd-gradle-plugin/javadoc.svg)](https://javadoc.io/doc/se.alipsa.gmd/gmd-gradle-plugin)
# The gmd-gradle-plugin

This gradle plugin makes it possible to process a directory of GMD files and transform them into md, html of pdf files. For groovy library writers, this makes it easier to keep your documentation up to date since the groovy code that you use will be compiled and thus ensured to work instead of getting outdated over time as your library evolves.

To use it in your gradle build script, add the following to your build.gradle file:

```groovy
plugins {
  id 'se.alipsa.gmd.gmd-gradle-plugin'
}
gmdPlugin {
  sourceDir = 'src/test/gmd'
  targetDir = 'build/target'
  outputType = 'html'
}
```
Possible parameters are:
- `sourceDir` - the directory where the GMD files are located. Default is `src/main/gmd`
- `targetDir` - the directory where the output files will be created. Default is `build/gmd`
- `outputType` - the type of output file to create. Possible values are `md`, `html`, `pdf`. Default is `md`
- `groovyVersion` - the version of Groovy to use. Default is `5.0.1`
- `gmdVersion` - the version of GMD to use. Default is `3.0.0`
- `log4jVersion` - the version of log4j to use. Default is `2.24.3`
- `ivyVersion` - the version of ivy to use. Default is `2.5.3`
- `runTaskBefore` - the task that the gmd plugin should run before. Default is 'test'

The target task is called `processGmd` so it can be invoked from the command line as follows:

```bash
./gradlew processGmd
```
The default task to run after processGmd is `test`. If you dont have pliugins that define that task such as 
`java`, `groovy` etc. you can do something like the following to set processGmd to run before `build`

```groovy
plugins {
    id('base') // add build and assemble tasks
    id('se.alipsa.gmd.gmd-gradle-plugin')
}
group = 'my.group'
version = '1.0.0-SNAPSHOT'

gmdPlugin {
    sourceDir = 'src/test/gmd'
    targetDir = 'build/target'
    outputType = 'pdf'
    runTaskBefore = 'build' // we dont have the test target so specify the task to not get a warning 
}
```
Now if you do `./gradlew build`, the processGmd task will run before build:
```
> Task :processGmd

Gmd files processed and written to /Users/myuser/myproject/build/target


BUILD SUCCESSFUL
 in 3s
```