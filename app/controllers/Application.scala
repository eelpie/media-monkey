package controllers

import java.io.File

import org.im4java.core.{ConvertCmd, IMOperation}
import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, BodyParsers, Controller}
import services.tika.TikaService

import scala.sys.process.{ProcessLogger, _}

object Application extends Controller {

  val Jpeg = "jpeg"
  val ImageJpegHeader: (String, String) = CONTENT_TYPE -> ("image/" + Jpeg)

  val tikaService: TikaService = TikaService

  def meta = Action(BodyParsers.parse.temporaryFile) { request =>

    val supportedImageTypes = Seq[String]("image/jpeg", "image/tiff", "image/png")
    val supportedVideoTypes = Seq[String]("application/mp4")

    def appendInferedType(tikaMetaData: JsValue): JsValue = {
      val tikaContentType: Option[String] = (tikaMetaData \ "Content-Type").toOption.map(jv => jv.as[String])

      tikaContentType.fold(tikaMetaData)(tct =>
        if (supportedImageTypes.contains(tct)) {
          tikaMetaData.as[JsObject] + ("type" -> Json.toJson("image"))
        } else if (supportedVideoTypes.contains(tct)) {
          tikaMetaData.as[JsObject] + ("type" -> Json.toJson("video"))
        } else {
          tikaMetaData
        }
      )
    }

    val f: File = request.body.file
    Logger.info("Received meta request to " + f.getAbsolutePath)

    val tikaMetaData: JsValue = tikaService.meta(f)

    Ok(Json.toJson( appendInferedType(tikaMetaData)))
  }
  
  def scale(width: Int = 800, height: Int = 600, rotate: Double = 0) = Action(BodyParsers.parse.temporaryFile) { request =>
    val f: File = request.body.file
    Logger.info("Received scale request to " + f.getAbsolutePath)

    val output: File = File.createTempFile("image", "." + Jpeg)
    Logger.info("Applying ImageMagik operation to output file: " + output.getAbsoluteFile)
    val cmd: ConvertCmd = new ConvertCmd();
    cmd.run(imResizeOperation(width, height, rotate), f.getAbsolutePath, output.getAbsolutePath());
    Logger.info("Completed ImageMagik operation output to: " + output.getAbsolutePath())

    val source = scala.io.Source.fromFile(new File(output.getAbsolutePath))
    Ok.sendFile(output).withHeaders(ImageJpegHeader)
  }

  def videoThumbnail() = Action(BodyParsers.parse.temporaryFile) {request =>

    val logger: ProcessLogger = ProcessLogger(l => Logger.info("avconv: " + l))

    val f: File = request.body.file
    Logger.info("Received transcode request to " + f.getAbsolutePath)

    val output: File = File.createTempFile("thumbnail", "." + Jpeg)
    val avconvCmd = Seq("avconv", "-y", "-i", f.getAbsolutePath, "-ss", "00:00:00", "-r", "1", "-an", "-vframes", "1", output.getAbsolutePath)
    val process: Process = avconvCmd.run(logger)
    val exitValue: Int = process.exitValue()  // Blocks until the process completes

    if (exitValue == 0) {
      val source = scala.io.Source.fromFile(new File(output.getAbsolutePath))
      Ok.sendFile(output).withHeaders(CONTENT_TYPE -> ("image/" + Jpeg))

    } else {
      Logger.warn("avconv process failed")
      InternalServerError(Json.toJson("Video could not be processed"))
    }
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