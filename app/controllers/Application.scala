package controllers

import java.io.File

import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, BodyParsers, Controller}
import services.images.ImageService
import services.tika.TikaService
import services.video.VideoService

object Application extends Controller {

  val ImageJpegHeader: (String, String) = CONTENT_TYPE -> ("image/" + "jpeg")

  val tikaService: TikaService = TikaService
  val imageService: ImageService = ImageService
  val videoService: VideoService = VideoService

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

    Ok(Json.toJson(appendInferedType(tikaMetaData)))
  }

  def scale(width: Int = 800, height: Int = 600, rotate: Double = 0) = Action(BodyParsers.parse.temporaryFile) { request =>
    val f: File = request.body.file
    Logger.info("Received scale request to " + f.getAbsolutePath)

    val output = imageService.resizeImage(f, width, height, rotate)
    val source = scala.io.Source.fromFile(new File(output.getAbsolutePath))
    Ok.sendFile(output).withHeaders(ImageJpegHeader)
  }

  def videoThumbnail() = Action(BodyParsers.parse.temporaryFile) { request =>
    val f: File = request.body.file
    Logger.info("Received transcode request to " + f.getAbsolutePath)

    val output = videoService.thumbnail(f)

    output.fold(InternalServerError(Json.toJson("Video could not be thumbnailed")))(o => {
      val source = scala.io.Source.fromFile(new File(o.getAbsolutePath))
      Ok.sendFile(o).withHeaders(ImageJpegHeader)
    })
  }

}