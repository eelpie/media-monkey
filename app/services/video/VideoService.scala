package services.video

import java.io.File

import play.api.Logger

import scala.sys.process.{ProcessLogger, _}

class VideoService {

  val Jpeg = "jpeg"

  def thumbnail(input: File): Option[File] = {

    val logger: ProcessLogger = ProcessLogger(l => Logger.info("avconv: " + l))

    val output: File = File.createTempFile("thumbnail", "." + Jpeg)
    val avconvCmd = Seq("avconv", "-y", "-i", input.getAbsolutePath, "-ss", "00:00:00", "-r", "1", "-an", "-vframes", "1", output.getAbsolutePath)
    val process: Process = avconvCmd.run(logger)
    val exitValue: Int = process.exitValue() // Blocks until the process completes

    if (exitValue == 0) {
      Logger.info("Thumbnail output to: " + output.getAbsolutePath)
      Some(output)
    } else {
      Logger.warn("avconv process failed")
      None
    }
  }
}

object VideoService extends VideoService