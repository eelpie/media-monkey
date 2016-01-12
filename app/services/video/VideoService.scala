package services.video

import java.io.File

import play.api.Logger
import play.api.libs.concurrent.Akka

import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process.{ProcessLogger, _}
import play.api.Play.current

class VideoService {

  val logger: ProcessLogger = ProcessLogger(l => Logger.info("avconv: " + l))

  def thumbnail(input: File, outputFormat: String, width: Option[Int], height: Option[Int], rotation: Option[Int]): Future[File] = {

    implicit val imageProcessingExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("image-processing-context")

    Future {
      val output: File = File.createTempFile("thumbnail", "." + outputFormat)

      val avconvCmd = Seq("avconv", "-y", "-i", input.getAbsolutePath) ++
        sizeParameters(width, height) ++
        rotationParameters(rotation) ++
        Seq("-ss", "00:00:00", "-r", "1", "-an", "-vframes", "1", output.getAbsolutePath)

      val process: Process = avconvCmd.run(logger)
      val exitValue: Int = process.exitValue() // Blocks until the process completes

      if (exitValue == 0) {
        Logger.info("Thumbnail output to: " + output.getAbsolutePath)
        output

      } else {
        Logger.warn("avconv process failed")
        throw new RuntimeException("avconv process failed")
      }
    }
  }

  def transcode(input: File, outputFormat: String, width: Option[Int], height: Option[Int]): Future[File] = {

    implicit val videoProcessingExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("video-processing-context")

    Future {
      val output: File = File.createTempFile("transcoded", "." + outputFormat)
      val avconvCmd = Seq("avconv", "-y", "-i", input.getAbsolutePath) ++ sizeParameters(width, height) ++ Seq("-strict", "experimental", output.getAbsolutePath)
      val process: Process = avconvCmd.run(logger)
      val exitValue: Int = process.exitValue() // Blocks until the process completes

      if (exitValue == 0) {
        Logger.info("Transcoded video output to: " + output.getAbsolutePath)
        output
      } else {
        Logger.warn("avconv process failed")
        throw new RuntimeException("avconv process failed")
      }
    }
  }

  private def sizeParameters(width: Option[Int], height: Option[Int]): Seq[String] = {
    val map: Option[Seq[String]] = width.flatMap(w =>
      height.map(h => Seq("-s", w + "x" + h))
    )
    map.fold(Seq[String]())(s => s)
  }

  private def rotationParameters(rotation: Option[Int]): Seq[String] = {

    val RotationTransforms = Map(
      90 -> "transpose=1",
      180 -> "hflip,vflip",
      270 -> "transpose=2"
    )

    val map: Option[Seq[String]] = rotation.flatMap { r =>
      RotationTransforms.get(r).map {
        t => Seq("-vf", t)
      }
    }
    map.fold(Seq[String]())(s => s)
  }

}

object VideoService extends VideoService