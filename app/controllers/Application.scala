
package controllers

import java.io.File

import model.Track
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.{Action, BodyParsers, Controller}
import services.images.ImageService
import services.mediainfo.MediainfoService
import services.tika.TikaService
import services.video.VideoService

object Application extends Controller {

  val ApplicationJsonHeader = CONTENT_TYPE -> ("application/json")

  case class OutputFormat(mineType: String, fileExtension: String)
  val supportedImageOutputFormats = Seq(OutputFormat("image/jpeg", "jpg"), OutputFormat("image/png", "png"), OutputFormat("image/gif", "gif"))
  val supportedVideoOutputFormats = Seq(OutputFormat("video/theora", "ogg"), OutputFormat("video/mp4", "mp4"))

  val UnsupportedOutputFormatRequested = "Unsupported output format requested"

  val mediainfoService: MediainfoService = MediainfoService
  val tikaService = TikaService
  val imageService = ImageService
  val videoService = VideoService

  def meta = Action(BodyParsers.parse.temporaryFile) { request =>

    val recognisedImageTypes = supportedImageOutputFormats
    val recognisedVideoTypes = supportedVideoOutputFormats ++ Seq(OutputFormat("application/mp4", "mp4"))

    def inferContentTypeSpecificAttributes(metadata: Map[String, String], file: File): Map[String, Any] = {

      def inferContentType(md: Map[String, String]): Option[String] = {
        md.get("Content-Type").flatMap(ct => {
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
        val mediainfoTracks: Option[Seq[Track]] = mediainfoService.mediainfo(file)
        Logger.info("mediainfo for video: " + mediainfoTracks)

        val videoTrackDimensions: Option[(Int, Int)] = mediainfoTracks.flatMap(mi => {
          mi.find(t => t.trackType == "Video").headOption.flatMap(v => {
            v.width.flatMap(w =>
              v.height.map(h =>
                (w, h)
              )
            )
          })
        })

        Seq(videoTrackDimensions.map(d => Seq("width" -> d._1, "height" -> d._2))).flatten.flatten
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
      InternalServerError("Could not process metadata")

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

      Ok(Json.toJson(md ++ contentTypeSpecificAttributes))
    })
  }

  def scale(width: Int = 800, height: Int = 600, rotate: Double = 0) = Action(BodyParsers.parse.temporaryFile) { request =>

    inferOutputTypeFromAcceptHeader(request.headers.get("Accept"), supportedImageOutputFormats).fold(BadRequest(UnsupportedOutputFormatRequested))(of => {
      val sourceFile = request.body
      val result = imageService.resizeImage(sourceFile.file, width, height, rotate, of.fileExtension) // TODO no error handling
      sourceFile.clean()

      Ok.sendFile(result).withHeaders(CONTENT_TYPE -> of.mineType)  // TOOD result file is never teared down?
    })
  }

  def videoThumbnail(width: Option[Int], height: Option[Int]) = Action(BodyParsers.parse.temporaryFile) { request =>

    inferOutputTypeFromAcceptHeader(request.headers.get("Accept"), supportedImageOutputFormats).fold(BadRequest(UnsupportedOutputFormatRequested))(of => {
      val sourceFile = request.body
      val result = videoService.thumbnail(sourceFile.file, of.fileExtension, width, height)
      result.fold(InternalServerError(Json.toJson("Video could not be thumbnailed")))(o => {
        sourceFile.clean()
        Ok.sendFile(o).withHeaders(CONTENT_TYPE -> of.mineType)
      })
    })
  }

  def videoTranscode() = Action(BodyParsers.parse.temporaryFile) { request =>

    inferOutputTypeFromAcceptHeader(request.headers.get("Accept"), supportedVideoOutputFormats).fold(BadRequest(UnsupportedOutputFormatRequested))(of => {
      val sourceFile = request.body
      val result = videoService.transcode(sourceFile.file, of.fileExtension)
      sourceFile.clean()

      result.fold(InternalServerError(Json.toJson("Video could not be transcoded")))(o => {
        Ok.sendFile(o).withHeaders(CONTENT_TYPE -> of.mineType) // TOOD result file is never teared down?
      })
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
