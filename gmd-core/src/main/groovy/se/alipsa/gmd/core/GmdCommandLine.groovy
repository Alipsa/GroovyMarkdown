package se.alipsa.gmd.core

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class GmdCommandLine {

  Closure runner
  /**
   *
   * @param args 0: command, 1: fromFile, 2: tofile
   */
  GmdCommandLine(String[] args) {
    if (args.length != 3) {
      throw new IllegalArgumentException("Expected 3 parameters (command, fromFile, toFile) but was $args.length")
    }
    def command = args[0].toLowerCase()
    runner = switch (command) {
      case 'tohtml' -> toHtml(args[1], args[2])
      case 'topdfraw' -> toPdfRaw(args[1], args[2])
      case 'topdf' -> toPdfStyled(args[1], args[2])
      default -> throw new IllegalArgumentException("Unknown command $command, expected either toHtml, toPdf or toPdfRaw")
    }
  }

  static Closure toHtml(String from, String to) {
    return {
      Gmd gmd = new Gmd()
      def html = gmdFileToHtml(from, gmd)
      File toFile = new File(to)
      Files.writeString(toFile.toPath(), html, StandardCharsets.UTF_8)
      println "Wrote $toFile.absolutePath"
    }
  }

  static Closure toPdfRaw(String from, String to) {
    return {
      Gmd gmd = new Gmd()
      def html = gmdFileToHtml(from, gmd)
      File toFile = new File(to)
      gmd.htmlToPdf(html, toFile)
      println "Wrote $toFile.absolutePath"
    }
  }

  static Closure toPdfStyled(String from, String to) {
    return {
      Gmd gmd = new Gmd()
      def html = gmdFileToHtml(from, gmd)
      File toFile = new File(to)
      gmd.processHtmlAndSaveAsPdf(html, toFile)
      println "Wrote $toFile.absolutePath"
    }
  }

  private static String gmdFileToHtml(String from, Gmd gmd) {
    File fromFile = new File(from)
    if (!fromFile.exists()) {
      throw new IllegalArgumentException("From file $fromFile does not exist")
    }
    gmd.gmdToHtmlDoc(Files.readString(fromFile.toPath(), StandardCharsets.UTF_8))
  }

  void run() {
    runner.call()
  }
}
