package services.images

import java.io.File

import org.im4java.core.{ConvertCmd, IMOperation, Info}
import org.joda.time.DateTime
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Akka

import scala.concurrent.{ExecutionContext, Future}

class ImageService {

  def info(input: File): Future[(Int, Int)] = {
    implicit val imageProcessingExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("image-processing-context")
    Future {
      val imageInfo: Info = new Info(input.getAbsolutePath, true)
      (imageInfo.getImageWidth, imageInfo.getImageHeight)
    }
  }

  def resizeImage(input: File, width: Option[Int], height: Option[Int], rotate: Double, outputFormat: String, fill: Boolean): Future[Option[File]] = {

    def imResizeOperation(width: Option[Int], height: Option[Int], rotate: Double, fill: Boolean): IMOperation = {
      if (fill) {
        val op: IMOperation = new IMOperation()
        op.addImage()
        op.autoOrient()
        op.rotate(rotate)

        width.flatMap { w =>
          height { h =>
            op.resize(w, h, "^")
            op.gravity("Center")
            op.extent(width, height)
          }
        }
        op.strip()
        op.addImage()
        op

      } else {
        val op: IMOperation = new IMOperation()
        op.addImage()
        op.autoOrient()
        op.rotate(rotate)

        width.flatMap { w =>
          height.map { h =>
            op.resize(w, h)
          }
        }

        op.strip()
        op.addImage()
        op
      }
    }

    implicit val imageProcessingExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("image-processing-context")

    Future {
      val outputFile = File.createTempFile("image", "." + outputFormat)
      Logger.debug("Applying ImageMagik operation to output file: " + outputFile.getAbsoluteFile)
      try {
        val start = DateTime.now
        val cmd: ConvertCmd = new ConvertCmd()
        cmd.run(imResizeOperation(width, height, rotate, fill), input.getAbsolutePath, outputFile.getAbsolutePath())

        val duration = DateTime.now.getMillis - start.getMillis
        Logger.info("Completed ImageMagik operation " + Seq(width, height, rotate, fill) + " output to: " + outputFile.getAbsolutePath() + " in " + duration + "ms")
        Some(outputFile)

      } catch {
        case e: Exception => {
          Logger.error("Exception while executing IM operation", e)
          outputFile.delete()
          None
        }
      }
    }

  }

}

object ImageService extends ImageService