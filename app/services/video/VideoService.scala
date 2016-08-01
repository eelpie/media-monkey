package services.video

import java.io.File

import model.Track
import org.im4java.core.{ConvertCmd, IMOperation}
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Akka
import services.mediainfo.{MediainfoInterpreter, MediainfoService}

import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process.{ProcessLogger, _}

object VideoService extends MediainfoInterpreter with AvconvPadding {

  val logger: ProcessLogger = ProcessLogger(l => Logger.debug("avconv: " + l))

  val mediainfoService = MediainfoService

  def thumbnail(input: File, outputFormat: String, width: Option[Int], height: Option[Int], sourceAspectRatio: Option[Double], rotation: Option[Int]): Future[Option[File]] = {

    implicit val videoProcessingExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("video-processing-context")

    mediainfoService.mediainfo(input).flatMap { mediainfo =>
      val rotationToApply = rotation.getOrElse {
        val ir = inferRotation(mediainfo)
        Logger.info("Applying rotation infered from mediainfo: " + ir)
        ir
      }

      Future {
        val output: File = File.createTempFile("thumbnail", "." + outputFormat)

        val outputSize = width.flatMap(w =>
          height.map { h =>
            (w, h)
          }
        )
        val sourceDimensions: Option[(Int, Int)] = videoDimensions(mediainfo)

        val avconvCmd = avconvInput(input, mediainfo) ++
          sizeParameters(width, height) ++
          rotationAndPaddingParameters(rotationToApply, padding(sourceDimensions, outputSize, sourceAspectRatio, rotationToApply), None) ++
          Seq("-ss", "00:00:00", "-r", "1", "-an", "-vframes", "1", output.getAbsolutePath)

        Logger.info("avconv command: " + avconvCmd)

        val process: Process = avconvCmd.run(logger)
        val exitValue: Int = process.exitValue() // Blocks until the process completes

        if (exitValue == 0) {
          Logger.info("Thumbnail output to: " + output.getAbsolutePath)
          Some(output)

        } else {
          Logger.warn("avconv process failed")
          output.delete
          None
        }
      }
    }
  }

  def strip(input: File, outputFormat: String, width: Int, height: Int, sourceAspectRatio: Option[Double], rotation: Option[Int]): Future[Option[File]] = {
    implicit val videoProcessingExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("video-processing-context")
    mediainfoService.mediainfo(input).flatMap { mediainfo =>
      val rotationToApply = rotation.getOrElse {
        val ir = inferRotation(mediainfo)
        Logger.info("Applying rotation infered from mediainfo: " + ir)
        ir
      }

      Future {
        val output: File = File.createTempFile("strip", "")

        val sourceDimensions: Option[(Int, Int)] = videoDimensions(mediainfo)

        val avconvCmd = avconvInput(input, mediainfo) ++
          sizeParameters(Some(width), Some(height)) ++
          Seq("-ss", "00:00:00", "-an") ++
          rotationAndPaddingParameters(rotationToApply, padding(sourceDimensions, Some(width, height), sourceAspectRatio, rotationToApply), Some("fps=1")) ++
          Seq(output.getAbsolutePath + "-%6d." + outputFormat)

        Logger.info("avconv command: " + avconvCmd)
        val process: Process = avconvCmd.run(logger)
        val exitValue: Int = process.exitValue() // Blocks until the process completes

        if (exitValue == 0) {
          Logger.info("Strip files output to: " + output.getAbsolutePath)

          val imageOutput: File = File.createTempFile("image", "." + outputFormat)
          try {
            def appendImagesOperation(files: String, outputPath: String): IMOperation = {
              val op: IMOperation = new IMOperation()
              op.addImage(files)
              op.appendHorizontally()
              op.addImage(outputPath)
              op
            }

            val cmd: ConvertCmd = new ConvertCmd()
            cmd.run(appendImagesOperation(output.getAbsolutePath + "-*." + outputFormat, output.getAbsolutePath()))
            Logger.info("Completed ImageMagik operation output to: " + output.getAbsolutePath())
            Some(output)

          } catch {
            case e: Exception => {
              Logger.error("Exception while executing IM operation", e)
              output.delete
              None
            }
          }

        } else {
          Logger.warn("avconv process failed")
          output.delete
          None
        }
      }
    }
  }

  def audio(input: File): Future[Option[File]] = {
    implicit val videoProcessingExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("video-processing-context")
    Future {
      val outputFormat = "wav"
      val output: File = File.createTempFile("audio", "." + outputFormat)
      val avconvCmd = avconvInput(input, mediainfo) ++ Seq("-vn", output.getAbsolutePath)
      Logger.info("Processing video audio track")
      Logger.info("avconv command: " + avconvCmd)

      val process: Process = avconvCmd.run(logger)        // Blocks until the process completes
      if (process.exitValue() == 0) {
        Logger.info("Transcoded video output to: " + output.getAbsolutePath)
        Some(output)

      } else {
        Logger.warn("avconv process failed")
        output.delete
        None
      }
    }
  }

  def transcode(input: File, outputFormat: String, outputSize: Option[(Int, Int)], sourceAspectRatio: Option[Double], rotation: Option[Int]): Future[Option[File]] = {

    implicit val videoProcessingExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("video-processing-context")

    mediainfoService.mediainfo(input).flatMap { mediainfo =>
      val rotationToApply = rotation.getOrElse(inferRotation(mediainfo))
      val sourceDimensions: Option[(Int, Int)] = videoDimensions(mediainfo)
      val possiblePadding = padding(sourceDimensions, outputSize, sourceAspectRatio, rotationToApply)

      Future {
        val outputFile = File.createTempFile("transcoded", "." + outputFormat)
        val avconvCmd = avconvInput(input, mediainfo) ++
          sizeParameters(outputSize.map(os => os._1), outputSize.map(os => os._2)) ++
          rotationAndPaddingParameters(rotationToApply, possiblePadding, None) ++
          Seq("-b:a", "128k", "-strict", "experimental", outputFile.getAbsolutePath)

        Logger.info("avconv command: " + avconvCmd)

        val process: Process = avconvCmd.run(logger)
        val exitValue: Int = process.exitValue() // Blocks until the process completes

        if (exitValue == 0) {
          Logger.info("Transcoded video output to: " + outputFile.getAbsolutePath)
          Some(outputFile)

        } else {
          Logger.warn("avconv process failed; deleting output file")
          outputFile.delete
          None
        }
      }
    }
  }

  private def sizeParameters(width: Option[Int], height: Option[Int]): Seq[String] = {
    val map: Option[Seq[String]] = width.flatMap(w =>
      height.map(h => Seq("-s", w + "x" + h))
    )
    map.fold(Seq[String]())(s => s)
  }

  private def rotationAndPaddingParameters(rotation: Int, possiblePadding: Option[String], additionalVfArguments: Option[String]): Seq[String] = {

    val RotationTransforms = Map(
      90 -> "transpose=1",
      180 -> "hflip,vflip",
      270 -> "transpose=2"
    )

    val possibleRotation: Option[String] = RotationTransforms.get(rotation)
    val vfParameters: Seq[String] = Seq(possibleRotation, possiblePadding, additionalVfArguments).flatten

    if (vfParameters.nonEmpty) Seq("-vf", vfParameters.mkString(",")) else Seq()
  }

  private def avconvInput(input: File, mediainfo: Option[Seq[Track]]): Seq[String] = {
    Seq("avconv", "-y") ++ videoCodec(mediainfo).flatMap(c => if (c == "WMV3") Some(Seq("-c:v", "wmv3")) else None).getOrElse(Seq()) ++ Seq("-i", input.getAbsolutePath)
  }

}