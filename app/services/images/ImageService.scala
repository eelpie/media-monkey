package services.images

import java.io.File

import org.im4java.core.{ConvertCmd, IMOperation}
import play.api.Logger
import play.api.libs.concurrent.Akka
import play.api.Play.current

import scala.concurrent.{ExecutionContext, Future}

class ImageService {

  def resizeImage(input: File, width: Int, height: Int, rotate: Double, outputFormat: String, fill: Boolean): Future[File] = {

    implicit val mediaServiceContext: ExecutionContext = Akka.system.dispatchers.lookup("image-processing-context")

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

    Future {
      val output: File = File.createTempFile("image", "." + outputFormat)
      Logger.info("Applying ImageMagik operation to output file: " + output.getAbsoluteFile)

      try {
        val cmd: ConvertCmd = new ConvertCmd()
        cmd.run(imResizeOperation(width, height, rotate, fill), input.getAbsolutePath, output.getAbsolutePath())
        Logger.info("Completed ImageMagik operation output to: " + output.getAbsolutePath())
        output

      } catch {
        case e: Exception => {
          Logger.error("Exception while executing IM operation", e)
          output.delete()
          throw e
        }
      }
    }

  }

}

object ImageService extends ImageService