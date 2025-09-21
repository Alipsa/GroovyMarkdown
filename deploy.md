
# Deployment/publishing notes 
To deploy everything do:
```shell
mvn -Prelease deploy
```
If you want to publish only some things (e.g. parent and core) do:

```shell
# from the repo root
mvn -Prelease -pl :gmd-parent,:gmd-core -am \
clean verify \
org.sonatype.central:central-publishing-maven-plugin:0.8.0:publish
```