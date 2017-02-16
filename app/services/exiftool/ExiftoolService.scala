package services.exiftool

import java.io.File

import play.api.Logger
import play.api.libs.json.Json

import scala.concurrent.Future
import scala.sys.process.{ProcessLogger, _}
import scala.concurrent.ExecutionContext.Implicits.{global => ec}

class ExiftoolService {

  def contentType(f: File): Future[Option[String]] = {
    Future {
      val mediainfoCmd = Seq("exiftool", "-json", f.getAbsolutePath)

      val out: StringBuilder = new StringBuilder()
      val logger: ProcessLogger = ProcessLogger(l => {
        out.append(l)
      })

      val process: Process = mediainfoCmd.run(logger)

      val exitValue: Int = process.exitValue() // Blocks until the process completes
      Logger.debug("exiftool exit value: " + exitValue)
      if (exitValue == 0) {
        val json: String = out.mkString
        Logger.debug("exiftool output: " + json)
        parse(json)

      } else {
        Logger.warn("exiftool process failed for file: " + f.getAbsolutePath + " / " + out.mkString)
        None
      }
    }.recover {
      case t: Throwable =>
        Logger.error("exiftool call failed", t)
        None
    }
  }

  def addXmp(f: File, field: String): Future[Option[File]] = {
    Future.successful(Some(f)) // TODO implement
  }

  def extractXmp(f: File): Future[Option[String]] = {
    Future.successful(Some("TODO")) // TODO implement
  }

  def parse(json: String): Option[String] = {
    Json.parse(json).\\("MIMEType").headOption.map { j =>
      j.as[String]
    }
  }

}

object ExiftoolService extends ExiftoolService