package se.alipsa.gmd.core

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
      toFile.write(html)
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
      gmd.processHtmlAndSaveAsPdf(html, toFile, true)
      println "Wrote $toFile.absolutePath"
    }
  }

  private static String gmdFileToHtml(String from, Gmd gmd) {
    File fromFile = new File(from)
    if (!fromFile.exists()) {
      throw new IllegalArgumentException("From file $fromFile does not exist")
    }
    gmd.gmdToHtmlDoc(fromFile.text)
  }

  void run() {
    runner.call()
  }
}
