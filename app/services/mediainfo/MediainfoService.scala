package services.mediainfo

import java.io.File

import model.Track
import play.api.Logger

import scala.sys.process.{ProcessLogger, _}

class MediainfoService {

  val mediainfoParser = MediainfoParser

  def mediainfo(f: File): Option[Seq[Track]] = {

    val mediainfoCmd = Seq("mediainfo", "--Output=XML", f.getAbsolutePath)

    val out: StringBuilder = new StringBuilder()
    val logger: ProcessLogger = ProcessLogger(l => {
      out.append(l)
    })

    val process: Process = mediainfoCmd.run(logger)

    val exitValue: Int = process.exitValue() // Blocks until the process completes

    if (exitValue == 0) {
      Some(mediainfoParser.parse(out.mkString))

    } else {
      Logger.warn("mediainfo process failed")
      None
    }
  }

}

object MediainfoService extends MediainfoService