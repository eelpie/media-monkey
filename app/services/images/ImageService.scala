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

  def resizeImage(input: File, width: Int, height: Int, rotate: Double, outputFormat: String, fill: Boolean): Future[Option[File]] = {
    def imResizeOperation(width: Int, height: Int, rotate: Double, fill: Boolean): IMOperation = {
      if (fill) {
        val op: IMOperation = new IMOperation()
        op.addImage()
        op.autoOrient()
        op.rotate(rotate)
        op.resize(width, height, "^")
        op.gravity("Center")
        op.extent(width, height)
        op.strip()
        op.addImage()
        op

      } else {
        val op: IMOperation = new IMOperation()
        op.addImage()
        op.autoOrient()
        op.rotate(rotate)
        op.resize(width, height)
        op.strip()
        op.addImage()
        op
      }
    }

    implicit val imageProcessingExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("image-processing-context")
    Future {
      val output: File = File.createTempFile("image", "." + outputFormat)
      Logger.debug("Applying ImageMagik operation to output file: " + output.getAbsoluteFile)
      try {
        val start = DateTime.now
        val cmd: ConvertCmd = new ConvertCmd()
        cmd.run(imResizeOperation(width, height, rotate, fill), input.getAbsolutePath, output.getAbsolutePath())

        val duration = DateTime.now.getMillis - start.getMillis
        Logger.info("Completed ImageMagik operation output to: " + output.getAbsolutePath() + " in " + duration + "ms")
        Some(output)

      } catch {
        case e: Exception => {
          Logger.error("Exception while executing IM operation", e)
          output.delete()
          None
        }
      }
    }

  }

}

object ImageService extends ImageService