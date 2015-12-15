
package controllers

import java.io.File

import model.Track
import play.api.Logger
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.ws.WS
import play.api.mvc.{Action, BodyParsers, Controller}
import services.images.ImageService
import services.mediainfo.MediainfoService
import services.tika.TikaService
import services.video.VideoService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Application extends Controller {

  val ApplicationJsonHeader = CONTENT_TYPE -> ("application/json")

  case class OutputFormat(mineType: String, fileExtension: String)
  val supportedImageOutputFormats = Seq(OutputFormat("image/jpeg", "jpg"), OutputFormat("image/png", "png"), OutputFormat("image/gif", "gif"))
  val supportedVideoOutputFormats = Seq(OutputFormat("video/theora", "ogg"), OutputFormat("video/mp4", "mp4"), OutputFormat("image/jpeg", "jpg"))

  val UnsupportedOutputFormatRequested = "Unsupported output format requested"

  val mediainfoService: MediainfoService = MediainfoService
  val tikaService = TikaService
  val imageService = ImageService
  val videoService = VideoService

  def meta = Action.async(BodyParsers.parse.temporaryFile) { request =>

    val recognisedImageTypes = supportedImageOutputFormats
    val recognisedVideoTypes = supportedVideoOutputFormats ++ Seq(OutputFormat("application/mp4", "mp4"))

    def inferContentTypeSpecificAttributes(metadata: Map[String, String], file: File): Map[String, Any] = {

      def inferContentType(md: Map[String, String]): Option[String] = {
        md.get(CONTENT_TYPE).flatMap(ct => {
          if (recognisedImageTypes.exists(it => it.mineType == ct)) {
            Some("image")
          } else if (recognisedVideoTypes.exists(vt => vt.mineType == ct)) {
            Some("video")
          } else {
            None
          }
        })
      }

      def inferImageSpecificAttributes(metadata: Map[String, String]): Seq[(String, Any)] = {
        val imageDimensions: Option[(Int, Int)] = metadata.get("Image Width").flatMap(iw => {
          metadata.get("Image Height").map(ih => {
            (iw.replace(" pixels", "").toInt, ih.replace(" pixels", "").toInt)
          })
        })

        val exifOrientation: Option[String] = metadata.get("Orientation")

        val orientedImageDimensions: Option[(Int, Int)] = exifOrientation.fold(imageDimensions)(eo => {
          imageDimensions.map(im => {
            val orientationsRequiringWidthHeightFlip = Seq("Right side, top (Rotate 90 CW)", "Left side, bottom (Rotate 270 CW)")
            if (orientationsRequiringWidthHeightFlip.contains(eo)) {
              (im._2, im._1)
            } else {
              im
            }
          })
        })

        val orientation = orientedImageDimensions.map(im => {
         if (im._1 > im._2) "landscape" else "portrait"
        })

        Seq(orientedImageDimensions.map(id => ("width" -> id._1)),
          orientedImageDimensions.map(id => ("height" -> id._2)),
          orientation.map(o => ("orientation" -> o))).flatten
      }

      def inferVideoSpecificAttributes(metadata: Map[String, String]): Seq[(String, Any)] = {

        def parsePixels(i: String): Int = {
         i.stripSuffix(" pixels").replaceAll(" ", "").toInt
        }

        def parseRotation(r: String): Int = {
          r.replaceAll("[^\\d]", "").toInt
        }

        val mediainfoTracks: Option[Seq[Track]] = mediainfoService.mediainfo(file)
        Logger.info("Tracks: " + mediainfoTracks)

        val mediainfoRotation: Option[String] = mediainfoTracks.flatMap(ts => ts.find(t => t.trackType == "Video").flatMap(i => i.fields.get("Rotation")))
        Logger.debug("Mediainfo video rotation: " + mediainfoRotation)
        val rotation = mediainfoRotation.fold(0)(mir => parseRotation(mir))

        val videoTrackDimensions: Option[(Int, Int)] = mediainfoTracks.flatMap(mi => {
          mi.find(t => t.trackType == "Video").headOption.flatMap{vt =>
            vt.fields.get("Width").flatMap(w =>
              vt.fields.get("Height").map(h =>
                (parsePixels(w), parsePixels(h))
              )
            )
          }
        })

        val trackFields: Option[Seq[(String, String)]] = mediainfoTracks.map { ts =>
          ts.map { t =>
            t.fields.toSeq
          }.flatten
        }

        val combinedTrackFields: Seq[(String, String)] = Seq(trackFields).flatten.flatten
        val dimensionFields: Seq[(String, Int)] = Seq(videoTrackDimensions.map(d => Seq("width" -> d._1, "height" -> d._2))).flatten.flatten

        combinedTrackFields ++ dimensionFields :+ ("rotation" -> rotation)
      }

      val contentType: Option[String] = inferContentType(metadata)

      val contentTypeSpecificAttributes = contentType.flatMap(ct => {
        if (ct == "image") {
          Some(inferImageSpecificAttributes(metadata))
        } else if (ct == "video") {
          Some(inferVideoSpecificAttributes(metadata))
        } else {
          None
        }
      })

      (contentTypeSpecificAttributes ++ contentType.map(ct => Seq(("type" -> ct)))).flatten.toMap
    }

    val sourceFile = request.body
    tikaService.meta(sourceFile.file).fold({
      Future.successful(InternalServerError("Could not process metadata"))

    }) (md => {
      implicit val writes = new Writes[Map[String, Any]] {
        override def writes(o: Map[String, Any]): JsValue = {
          val map = o.map(i => {
            val value: Any = i._2
            val json: JsValue = value match {
              case i: Int => JsNumber(i)
              case _ => Json.toJson (value.toString)
            }
            (i._1, json)
          }).toList

          JsObject(map.toList)
        }
      }

      val contentTypeSpecificAttributes = inferContentTypeSpecificAttributes(md, request.body.file)

      sourceFile.clean()

      Future.successful(Ok(Json.toJson(md ++ contentTypeSpecificAttributes)))
    })
  }

  def scale(w: Option[Int], h: Option[Int], rotate: Double = 0, callback: Option[String]) = Action.async(BodyParsers.parse.temporaryFile) { request =>

    val width = w.getOrElse(800)
    val height = h.getOrElse(600)

    inferOutputTypeFromAcceptHeader(request.headers.get("Accept"), supportedImageOutputFormats).fold(Future.successful(BadRequest(UnsupportedOutputFormatRequested))) { of =>
      val sourceFile = request.body
      val eventualResult = imageService.resizeImage(sourceFile.file, width, height, rotate, of.fileExtension) // TODO no error handling

      eventualResult.map { result =>
        sourceFile.clean()

        val imageWidthHeader = ("X-Width", width.toString) // TODO actual output dimensions may differ
        val imageHeightHeader = ("X-Height", height.toString)

        callback.fold {
          Ok.sendFile(result, onClose = () => {result.delete()}).
            withHeaders(CONTENT_TYPE -> of.mineType, imageWidthHeader, imageHeightHeader)

        } { cb =>
          Logger.info("Calling back to: " + cb)
          WS.url(cb).withHeaders((CONTENT_TYPE, of.mineType), imageWidthHeader, imageHeightHeader).
            post(result).map { r =>
            Logger.info("Response from callback url " + callback + ": " + r.status)
            result.delete()
          }

          Accepted(Json.toJson("Accepted"))
        }
      }
    }
  }

  def videoTranscode(w: Option[Int], h: Option[Int], callback: Option[String]) = Action(BodyParsers.parse.temporaryFile) { request =>

    inferOutputTypeFromAcceptHeader(request.headers.get("Accept"), supportedVideoOutputFormats).fold(BadRequest(UnsupportedOutputFormatRequested)){ of =>

      val width = w.getOrElse(320)
      val height = h.getOrElse(200)

      if (of.mineType.startsWith("image/")) {
        val sourceFile = request.body
        val result = videoService.thumbnail(sourceFile.file, of.fileExtension, width, height)
        sourceFile.clean()

        val imageWidthHeader = ("X-Width", width.toString)  // TODO actual output dimensions may differ
        val imageHeightHeader = ("X-Height", height.toString)

        result.fold(InternalServerError(Json.toJson("Video could not be thumbnailed"))) (o =>
          Ok.sendFile(o, onClose = () => {o.delete()}).
            withHeaders(CONTENT_TYPE -> of.mineType, imageWidthHeader, imageHeightHeader)
        )

      } else {

        val sourceFile = request.body
        val result = videoService.transcode(sourceFile.file, of.fileExtension)
        sourceFile.clean()

        val imageWidthHeader = ("X-Width", width.toString) // TODO actual output dimensions may differ
        val imageHeightHeader = ("X-Height", height.toString)

        result.fold(InternalServerError(Json.toJson("Video could not be transcoded")))(o =>
          Ok.sendFile(o, onClose = () => {
            o.delete()
          }).withHeaders(CONTENT_TYPE -> of.mineType, imageWidthHeader, imageHeightHeader)
        )
      }
    }
  }

  @Deprecated
  def videoTranscodeCallback(callback: String) = Action(BodyParsers.parse.temporaryFile) { request =>

    inferOutputTypeFromAcceptHeader(request.headers.get("Accept"), supportedVideoOutputFormats).fold(BadRequest(UnsupportedOutputFormatRequested))(of => {
      val result = videoService.transcode(request.body.file, of.fileExtension)
      request.body.clean()

      result.map { vr =>
        // TODO validate callback url
        Logger.info("Calling back to: " + callback)

        val imageWidthHeader = ("X-Width", 320.toString)  // TODO actual output dimensions may differ
        val imageHeightHeader = ("X-Height", 200.toString)

        WS.url(callback).
          withHeaders((CONTENT_TYPE, of.mineType), imageWidthHeader, imageHeightHeader).
          post(vr).map { r =>
           Logger.info("Response from callback url " + callback + ": " + r.status)
            vr.delete()
        }
      }

      Accepted(Json.toJson("Accepted"))
    })
  }
  
  @Deprecated
  def videoThumbnailCallback(width: Int, height: Int, callback: String) = Action(BodyParsers.parse.temporaryFile) { request =>

    inferOutputTypeFromAcceptHeader(request.headers.get("Accept"), supportedImageOutputFormats).fold(BadRequest(UnsupportedOutputFormatRequested))(of => {
      val result = videoService.thumbnail(request.body.file, of.fileExtension, width, height) // TODO width and height optionals should have been resolved by this point
      request.body.clean()

      result.map { vr =>
        // TODO validate callback url
        Logger.info("Calling back to: " + callback)

        val imageWidthHeader = ("X-Width", width.toString)  // TODO actual output dimensions may differ
        val imageHeightHeader = ("X-Height", height.toString)

        WS.url(callback).
          withHeaders((CONTENT_TYPE, of.mineType), imageWidthHeader, imageHeightHeader).
          post(vr).map { r =>
          Logger.info("Response from callback url " + callback + ": " + r.status)
          vr.delete()
        }
      }

      Accepted(Json.toJson("Accepted"))
    })
  }

  private def inferOutputTypeFromAcceptHeader(acceptHeader: Option[String], availableFormats: Seq[OutputFormat]): Option[OutputFormat] = {
    val defaultOutputFormat = availableFormats.headOption
    acceptHeader.fold(defaultOutputFormat)(ah => {
      if (ah.equals("*/*")) {
        defaultOutputFormat
      } else {
        availableFormats.find(sf => sf.mineType == ah)
      }
    })
  }

}
