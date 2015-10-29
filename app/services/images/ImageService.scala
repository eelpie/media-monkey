package services.images

import java.io.File

import org.im4java.core.{ConvertCmd, IMOperation}
import play.api.Logger

class ImageService {

  def resizeImage(input: File, width: Int, height: Int, rotate: Double, outputFormat: String): File = {
    val output: File = File.createTempFile("image", "." + outputFormat)
    Logger.info("Applying ImageMagik operation to output file: " + output.getAbsoluteFile)
    val cmd: ConvertCmd = new ConvertCmd();
    cmd.run(imResizeOperation(width, height, rotate), input.getAbsolutePath, output.getAbsolutePath());
    Logger.info("Completed ImageMagik operation output to: " + output.getAbsolutePath())
    output
  }

  private def imResizeOperation(width: Int, height: Int, rotate: Double): IMOperation = {
    val op: IMOperation = new IMOperation()
    op.addImage()
    op.autoOrient()
    op.rotate(rotate)
    op.resize(width, height, "^")
    op.gravity("Center")
    op.crop(width, height, 0, 0)
    op.strip()
    op.addImage()
    op
  }

}

object ImageService extends ImageService