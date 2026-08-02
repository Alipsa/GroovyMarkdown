#!/usr/bin/env bash
set -euo pipefail

repo_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cd "$repo_dir"

version=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)

validate_release_artifacts() {
  local artifact
  local module_dir
  local file

  # Keep this in reactor order so an incomplete early component fails before
  # later components are considered for publication.
  for artifact in gmd-parent highlightjs-jvm gmd-core gmd-maven-plugin; do
    if [[ "$artifact" == "gmd-parent" ]]; then
      module_dir="."
      file=".flattened-pom.xml"
      [[ -f "$module_dir/$file" ]] || {
        echo "Missing release artifact: $module_dir/$file" >&2
        return 1
      }
      file="target/$artifact-$version.pom.asc"
      [[ -f "$module_dir/$file" ]] || {
        echo "Missing release signature: $module_dir/$file" >&2
        return 1
      }
      continue
    fi

    module_dir="$artifact"
    for file in "$artifact-$version.jar" \
                "$artifact-$version-sources.jar" \
                "$artifact-$version-javadoc.jar"; do
      [[ -f "$module_dir/target/$file" ]] || {
        echo "Missing release artifact: $module_dir/target/$file" >&2
        return 1
      }
      [[ -f "$module_dir/target/$file.asc" ]] || {
        echo "Missing release signature: $module_dir/target/$file.asc" >&2
        return 1
      }
    done

    [[ -f "$module_dir/.flattened-pom.xml" ]] || {
      echo "Missing release artifact: $module_dir/.flattened-pom.xml" >&2
      return 1
    }
    [[ -f "$module_dir/target/$artifact-$version.pom.asc" ]] || {
      echo "Missing release signature: $module_dir/target/$artifact-$version.pom.asc" >&2
      return 1
    }
  done
}

echo "Running the complete non-publishing build"
mvn clean install

echo "Validating the Gradle Plugin Portal publication without uploading"
(cd gmd-gradle-plugin && ./gradlew clean build publishPlugins --validate-only \
  -Ppublish.version="$version")

echo "Building and validating the Central bundle without uploading"
mvn -Prelease -DskipGradleDriver=true -Dcentral.skipPublishing=true deploy
validate_release_artifacts

echo "Publishing Maven Central artifacts"
# Do not publish the Gradle plugin from Maven's deploy phase. Publish it only
# after Central has accepted and published its deployment.
mvn -Prelease -DskipGradleDriver=true -Dcentral.waitUntil=published deploy

echo "Publishing the Gradle plugin"
(cd gmd-gradle-plugin && ./gradlew publishPlugins -Ppublish.version="$version")

echo "Maven Central and Gradle Plugin Portal publication completed for $version"
