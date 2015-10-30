package services.video

import java.io.File

import play.api.Logger

import scala.sys.process.{ProcessLogger, _}

class VideoService {

  val Ogg = "ogg"

  val logger: ProcessLogger = ProcessLogger(l => Logger.info("avconv: " + l))

  def thumbnail(input: File, outputFormat: String): Option[File] = {

    val output: File = File.createTempFile("thumbnail", "." + outputFormat)
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

  def transcode(input: File): Option[File] = {

    val output: File = File.createTempFile("transcoded", "." + Ogg)
    val avconvCmd = Seq("avconv", "-y", "-i", input.getAbsolutePath, output.getAbsolutePath)
    val process: Process = avconvCmd.run(logger)
    val exitValue: Int = process.exitValue() // Blocks until the process completes

    if (exitValue == 0) {
      Logger.info("Transcoded video output to: " + output.getAbsolutePath)
      Some(output)
    } else {
      Logger.warn("avconv process failed")
      None
    }
  }

}

object VideoService extends VideoService