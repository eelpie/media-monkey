package services.exiftool

import java.io.File

import play.api.Logger
import play.api.libs.json.Json

import scala.sys.process.{ProcessLogger, _}

class ExiftoolService {

  def meta(f: File): Option[Map[String, String]] = {
    val mediainfoCmd = Seq("exiftool", "-json", f.getAbsolutePath)

    val out: StringBuilder = new StringBuilder()
    val logger: ProcessLogger = ProcessLogger(l => {
      out.append(l)
    })

    val process: Process = mediainfoCmd.run(logger)

    val exitValue: Int = process.exitValue() // Blocks until the process completes

    if (exitValue == 0) {
      Some(Json.parse(out.mkString).as[Map[String, String]])

    } else {
      Logger.warn("exiftool process failed")
      None
    }
  }

}

object ExiftoolService extends ExiftoolService