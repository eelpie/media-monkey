package services.mediainfo

import java.io.File

import org.scalatest.path
import org.w3c.dom.Document
import play.api.Logger

import scala.sys.process.{ProcessLogger, _}
import scala.xml.{Node, Elem}

class MediainfoService {


  def mediainfo(f: File) = {

    val mediainfoCmd = Seq("mediainfo", "--Output=XML", f.getAbsolutePath)

    val out: StringBuilder = new StringBuilder()
    val logger: ProcessLogger = ProcessLogger(l => {
      Logger.info("mediainfo: " + l)
      out.append(l)
    })

    val process: Process = mediainfoCmd.run(logger)

    val exitValue: Int = process.exitValue() // Blocks until the process completes

    if (exitValue == 0) {
      Some(out.mkString)
    } else {
      Logger.warn("mediainfo process failed")
      None
    }

  }

}

object MediainfoService extends MediainfoService