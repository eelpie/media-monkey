
package controllers

import java.io.File

import org.im4java.core.{ConvertCmd, IMOperation}
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, BodyParsers, Controller}
import services.tika.TikaService

object Application extends Controller {

  val JPEG = "jpeg"

  val tikaService: TikaService = TikaService

  def meta = Action(BodyParsers.parse.temporaryFile) { request =>

    def appendInferedType(tikaMetaData: JsValue): Unit = {
      val tikaContentType: Option[String] = (tikaMetaData \ "Content-Type").toOption.map(jv => jv.as[String])
      if (tikaContentType.equals(Some("image/jpeg")) || tikaContentType.equals(Some("image/tiff"))) {
        tikaMetaData -> ("type" -> "image")
      }
    }

    val f: File = request.body.file
    Logger.info("Received meta request to " + f.getAbsolutePath)

    val tikaMetaData: JsValue = tikaService.meta(f)

    Ok(Json.toJson( appendInferedType(tikaMetaData)))
  }

  def scale(width: Int = 800, height: Int = 600, rotate: Double = 0) = Action(BodyParsers.parse.temporaryFile) { request =>
    val f: File = request.body.file
    Logger.info("Received scale request to " + f.getAbsolutePath)

    val output: File = File.createTempFile("image", "." + JPEG)
    Logger.info("Applying ImageMagik operation to output file: " + output.getAbsoluteFile)
    val cmd: ConvertCmd = new ConvertCmd();
    cmd.run(imResizeOperation(width, height, rotate), f.getAbsolutePath, output.getAbsolutePath());
    Logger.info("Completed ImageMagik operation output to: " + output.getAbsolutePath())

    val source = scala.io.Source.fromFile(new File(output.getAbsolutePath))

    Ok.sendFile(output).withHeaders(CONTENT_TYPE -> ("image/" + JPEG))
  }

  private def imResizeOperation(width: Int, height: Int, rotate: Double): IMOperation = {
    val op: IMOperation = new IMOperation()
    op.addImage()
    op.rotate(rotate)
    op.resize(width, height, "^")
    op.gravity("Center")
    op.crop(width, height, 0, 0)
    op.addImage()
    op
  }

}