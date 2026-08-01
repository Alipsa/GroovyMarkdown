package se.alipsa.gmd.core

import groovy.transform.CompileStatic

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Locale

@CompileStatic
class GmdProcessor {

  static void main(String[] args) {
    GmdProcessor gmdp = new GmdProcessor()
    if (args.length != 3) {
      throw new IllegalArgumentException("Expected 3 parameters (sourceDir, targetDir, outputType) but was $args.length")
    }
    def sourceDir = args[0]
    def targetDir = args[1]
    def outputType = args[2].toLowerCase(Locale.ROOT)
    if (!['md', 'html', 'pdf'].contains(outputType)) {
      throw new IllegalArgumentException("Unknown output type $outputType, expected either md, html or pdf")
    }
    gmdp.process(sourceDir, targetDir, outputType)
  }

  void process(String sourceDir, String targetDir, String outputType) {
    if (sourceDir == null || targetDir == null) {
      throw new IllegalArgumentException('Source and target directories cannot be null')
    }
    String normalizedOutputType = outputType?.toLowerCase(Locale.ROOT)
    if (!['md', 'html', 'pdf'].contains(normalizedOutputType)) {
      throw new IllegalArgumentException("Unknown output type $outputType, expected either md, html or pdf")
    }
    File sourceDirectory = new File(sourceDir)
    if (!sourceDirectory.exists()) {
      throw new IllegalArgumentException("Source directory ${sourceDirectory.absolutePath} does not exist")
    }
    if (!sourceDirectory.isDirectory()) {
      throw new IllegalArgumentException("Source path ${sourceDirectory.absolutePath} is not a directory")
    }
    File targetDirectory = new File(targetDir)
    if (targetDirectory.exists() && !targetDirectory.isDirectory()) {
      throw new IllegalArgumentException("Target path ${targetDirectory.absolutePath} is not a directory")
    }
    if (!targetDirectory.exists() && !targetDirectory.mkdirs() && !targetDirectory.isDirectory()) {
      throw new IllegalArgumentException("Could not create target directory ${targetDirectory.absolutePath}")
    }

    File[] sourceFiles = sourceDirectory.listFiles({ File f -> f.isFile() && f.name.endsWith('.gmd') } as FileFilter)
    if (sourceFiles == null) {
      throw new IllegalArgumentException("Could not read source directory ${sourceDirectory.absolutePath}")
    }

    Gmd gmd = new Gmd()
    for (file in sourceFiles) {
      if (file.name.endsWith('.gmd')) {
        def outputFile = new File(targetDirectory, file.name.substring(0, file.name.length() - 4) + ".${normalizedOutputType}")
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8)
        switch (normalizedOutputType) {
          case 'md': Files.writeString(outputFile.toPath(), gmd.gmdToMd(content), StandardCharsets.UTF_8); break
          case 'html': Files.writeString(outputFile.toPath(), gmd.gmdToHtml(content), StandardCharsets.UTF_8); break
          case 'pdf': gmd.gmdToPdf(content, outputFile); break
        }
      }
    }
  }
}
