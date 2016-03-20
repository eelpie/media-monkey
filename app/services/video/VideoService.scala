package services.video

import java.io.File

import org.im4java.core.{ConvertCmd, IMOperation}
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Akka
import services.mediainfo.{MediainfoInterpreter, MediainfoService}

import scala.concurrent.ExecutionContext.Implicits.{global => ec}
import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process.{ProcessLogger, _}

object VideoService extends MediainfoInterpreter {

  val logger: ProcessLogger = ProcessLogger(l => Logger.info("avconv: " + l))

  val mediainfoService = MediainfoService

  def thumbnail(input: File, outputFormat: String, width: Option[Int], height: Option[Int], rotation: Option[Int]): Future[File] = {
    mediainfoService.mediainfo(input).flatMap { mediainfo =>

      val rotationToApply = rotation.getOrElse {
        val ir = inferRotation(mediainfo)
        Logger.info("Applying rotation infered from mediainfo: " + ir)
        ir
      }

      implicit val imageProcessingExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("image-processing-context")

      Future {
        val output: File = File.createTempFile("thumbnail", "." + outputFormat)

        val outputSize = width.flatMap(w =>
          height.map { h =>
            (w, h)
          }
        )
        val sourceDimensions: Option[(Int, Int)] = videoDimensions(mediainfo)

        val avconvCmd = Seq("avconv", "-y", "-i", input.getAbsolutePath) ++
          sizeParameters(width, height) ++
          rotationAndPaddingParameters(rotationToApply, padding(sourceDimensions, outputSize, rotationToApply), None) ++
          Seq("-ss", "00:00:00", "-r", "1", "-an", "-vframes", "1", output.getAbsolutePath)

        Logger.info("avconv command: " + avconvCmd)

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
  }

  def strip(input: File, outputFormat: String, width: Int, height: Int, rotation: Option[Int]): Future[File] = {
    mediainfoService.mediainfo(input).flatMap { mediainfo =>

      val rotationToApply = rotation.getOrElse {
        val ir = inferRotation(mediainfo)
        Logger.info("Applying rotation infered from mediainfo: " + ir)
        ir
      }

      implicit val imageProcessingExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("image-processing-context")

      Future {
        val output: File = File.createTempFile("strip", "")

        val sourceDimensions: Option[(Int, Int)] = videoDimensions(mediainfo)

        val avconvCmd = Seq("avconv", "-y", "-i", input.getAbsolutePath) ++
          sizeParameters(Some(width), Some(height)) ++
          Seq("-ss", "00:00:00", "-an") ++
          rotationAndPaddingParameters(rotationToApply, padding(sourceDimensions, Some(width, height), rotationToApply), Some("fps=1")) ++
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
            output

          } catch {
            case e: Exception => {
              Logger.error("Exception while executing IM operation", e)
              output.delete()
              throw e
            }
          }

        } else {
          Logger.warn("avconv process failed")
          throw new RuntimeException("avconv process failed")
        }
      }
    }
  }

  def audio(input: File): Future[File] = {
    Future {
      val outputFormat = "wav"
      val output: File = File.createTempFile("audio", "." + outputFormat)
      val avconvCmd = Seq("avconv", "-y", "-i", input.getAbsolutePath, output.getAbsolutePath)

      Logger.info("avconv command: " + avconvCmd)

      val process: Process = avconvCmd.run(logger)        // Blocks until the process completes
      if (process.exitValue() == 0) {
        Logger.info("Transcoded video output to: " + output.getAbsolutePath)
        output

      } else {
        Logger.warn("avconv process failed")
        throw new RuntimeException("avconv process failed")
      }
    }
  }

  def transcode(input: File, outputFormat: String, outputSize: Option[(Int, Int)], rotation: Option[Int]): Future[File] = {
    mediainfoService.mediainfo(input).flatMap { mediainfo =>

      val rotationToApply = rotation.getOrElse {
        val ir = inferRotation(mediainfo)
        Logger.info("Applying rotation infered from mediainfo: " + ir)
        ir
      }

      val sourceDimensions: Option[(Int, Int)] = videoDimensions(mediainfo)
      val possiblePadding = padding(sourceDimensions, outputSize, rotationToApply)

      implicit val videoProcessingExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("video-processing-context")

      Future {
        val output: File = File.createTempFile("transcoded", "." + outputFormat)
        val avconvCmd = Seq("avconv", "-y", "-i", input.getAbsolutePath) ++
          sizeParameters(outputSize.map(os => os._1), outputSize.map(os => os._2)) ++
          rotationAndPaddingParameters(rotationToApply, possiblePadding, None) ++
          Seq("-strict", "experimental", output.getAbsolutePath)

        Logger.info("avconv command: " + avconvCmd)

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
  }

  private def sizeParameters(width: Option[Int], height: Option[Int]): Seq[String] = {
    val map: Option[Seq[String]] = width.flatMap(w =>
      height.map(h => Seq("-s", w + "x" + h))
    )
    map.fold(Seq[String]())(s => s)
  }

  private def padding(sourceDimensions: Option[(Int, Int)], outputSize: Option[(Int, Int)], rotationToApply: Int): Option[String] = {
    outputSize.flatMap { os =>
      sourceDimensions.flatMap { sd =>
        val sourceAspectRatio = (BigDecimal(sd._1) / BigDecimal(sd._2)).setScale(2, BigDecimal.RoundingMode.HALF_DOWN).toDouble
        Logger.info("Source dimensions " + sd + " aspect ratio: " + sourceAspectRatio)

        val outputAspectRatio = (BigDecimal(os._1) / BigDecimal(os._2)).setScale(2, BigDecimal.RoundingMode.HALF_DOWN).toDouble
        Logger.info("Ouptut dimensions " + os + " aspect ratio: " + outputAspectRatio)

        val aspectRatiosDiffer: Boolean = sourceAspectRatio != outputAspectRatio
        val isRotated = (rotationToApply == 90 || rotationToApply == 270)
        if (aspectRatiosDiffer || isRotated) {
          Logger.info("Applying padding")
          Some("pad=ih*16/9:ih:(ow-iw)/2:(oh-ih)/2")
        } else {
          Logger.info("No padding required")
          None
        }
      }
    }
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

}