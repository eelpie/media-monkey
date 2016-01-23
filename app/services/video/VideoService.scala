package services.video

import java.io.File

import org.im4java.core.{IMOperation, ConvertCmd}
import play.api.Logger
import play.api.libs.concurrent.Akka
import services.mediainfo.{MediainfoInterpreter, MediainfoService}

import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process.{ProcessLogger, _}
import play.api.Play.current

object VideoService extends MediainfoInterpreter {

  val logger: ProcessLogger = ProcessLogger(l => Logger.info("avconv: " + l))

  val mediainfoService = MediainfoService

  def thumbnail(input: File, outputFormat: String, width: Option[Int], height: Option[Int], rotation: Option[Int]): Future[File] = {

    val rotationToApply = rotation.getOrElse{
      val ir = inferRotation(mediainfoService.mediainfo(input))
      Logger.info("Applying rotation infered from mediainfo: " + ir)
      ir
    }

    implicit val imageProcessingExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("image-processing-context")

    Future {
      val output: File = File.createTempFile("thumbnail", "." + outputFormat)

      val avconvCmd = Seq("avconv", "-y", "-i", input.getAbsolutePath) ++
        sizeParameters(width, height) ++
        rotationAndPaddingParameters(rotationToApply) ++
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

  def strip(input: File, outputFormat: String, width: Int, height: Int, rotation: Option[Int]) = {

    val rotationToApply = rotation.getOrElse{
      val ir = inferRotation(mediainfoService.mediainfo(input))
      Logger.info("Applying rotation infered from mediainfo: " + ir)
      ir
    }

    implicit val imageProcessingExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("image-processing-context")

    Future {
      val output: File = File.createTempFile("strip", "")

      // avconv -i input.original -s 320x180 -ss 00:00:00 -an -vf fps=1,pad=ih*16/9:ih:\(ow-iw\)/2:\(oh-ih\)/2 output-%3d.jpg
      val avconvCmd = Seq("avconv", "-y", "-i", input.getAbsolutePath) ++
        sizeParameters(Some(width), Some(height)) ++
        Seq("-ss", "00:00:00", "-an") ++
        rotationAndPaddingParameters(rotationToApply) ++
        Seq("-vf", "fps=1") ++
        Seq(output.getAbsolutePath + "-%6d." + outputFormat)

      Logger.info("avconv command: " + avconvCmd)

      val process: Process = avconvCmd.run(logger)
      val exitValue: Int = process.exitValue() // Blocks until the process completes

      if (exitValue == 0) {
        Logger.info("Strip files output to: " + output.getAbsolutePath)

        val imageOutput: File = File.createTempFile("image", "." + outputFormat)

        // convert test-*.jpg +append out.jpg
        try {

          def appendImagesOperation(files: String): IMOperation = {
            val op: IMOperation = new IMOperation()
            op.addImage(files)
            op.appendHorizontally()
            op
          }


          val cmd: ConvertCmd = new ConvertCmd()
          cmd.run(appendImagesOperation(output.getAbsolutePath + "-*." + outputFormat), output.getAbsolutePath())
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

  def transcode(input: File, outputFormat: String, width: Option[Int], height: Option[Int], rotation: Option[Int]): Future[File] = {

    val rotationToApply = rotation.getOrElse{
      val ir = inferRotation(mediainfoService.mediainfo(input))
      Logger.info("Applying rotation infered from mediainfo: " + ir)
      ir
    }

    implicit val videoProcessingExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("video-processing-context")

    Future {
      val output: File = File.createTempFile("transcoded", "." + outputFormat)
      val avconvCmd = Seq("avconv", "-y", "-i", input.getAbsolutePath) ++
        sizeParameters(width, height) ++
        rotationAndPaddingParameters(rotationToApply) ++
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

  private def sizeParameters(width: Option[Int], height: Option[Int]): Seq[String] = {
    val map: Option[Seq[String]] = width.flatMap(w =>
      height.map(h => Seq("-s", w + "x" + h))
    )
    map.fold(Seq[String]())(s => s)
  }

  private def rotationAndPaddingParameters(rotation: Int): Seq[String] = {

    val RotationTransforms = Map(
      90 -> "transpose=1",
      180 -> "hflip,vflip",
      270 -> "transpose=2"
    )

    val possibleRotation: Option[String] = RotationTransforms.get(rotation)

    Seq("-vf", possibleRotation.fold("")(r => r + ",") + "pad=ih*16/9:ih:(ow-iw)/2:(oh-ih)/2")
  }

}