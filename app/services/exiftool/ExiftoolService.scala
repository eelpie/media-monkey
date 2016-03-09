package services.exiftool

import java.io.File

import play.api.Logger
import play.api.libs.json.Json

import scala.sys.process.{ProcessLogger, _}

class ExiftoolService {

  def contentType(f: File): Option[String] = {
    val mediainfoCmd = Seq("exiftool", "-json", f.getAbsolutePath)

    val out: StringBuilder = new StringBuilder()
    val logger: ProcessLogger = ProcessLogger(l => {
      out.append(l)
    })

    val process: Process = mediainfoCmd.run(logger)

    val exitValue: Int = process.exitValue() // Blocks until the process completes

    if (exitValue == 0) {
      parse(out.mkString)

    } else {
      Logger.warn("exiftool process failed")
      None
    }
  }

  def parse(json: String): Option[String] = {
    Json.parse(json).\\("MIMEType").headOption.map { j =>
      j.as[String]
    }
  }

}

object ExiftoolService extends ExiftoolService