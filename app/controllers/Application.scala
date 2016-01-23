package controllers

import java.io.File

import model.Track
import play.api.Logger
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.ws.WS
import play.api.mvc.{Result, Action, BodyParsers, Controller}
import services.images.ImageService
import services.mediainfo.{MediainfoInterpreter, MediainfoService}
import services.tika.TikaService
import services.video.VideoService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Application extends Controller with MediainfoInterpreter {

  val XWidth = "X-Width"
  val XHeight = "X-Height"

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

        def parseRotation(r: String): Int = {
          r.replaceAll("[^\\d]", "").toInt
        }

        val mediainfoTracks = mediainfoService.mediainfo(file)

        val videoTrackDimensions = videoDimensions(mediainfoTracks)
        val rotation = inferRotation(mediainfoTracks)

        val trackFields: Option[Seq[(String, String)]] = mediainfoTracks.map { ts =>
          ts.filter( t => t.trackType == "General" || t.trackType == "Video").map { t =>  // TODO work out a good format to preserver all of this information
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

  def scale(w: Option[Int], h: Option[Int], rotate: Option[Int], callback: Option[String], f: Option[Boolean]) = Action.async(BodyParsers.parse.temporaryFile) { request =>

    val width = w.getOrElse(800)
    val height = h.getOrElse(600)
    val rotationToApply = rotate.getOrElse(0)

    val fill = f.getOrElse(false)

    inferOutputTypeFromAcceptHeader(request.headers.get("Accept"), supportedImageOutputFormats).fold(Future.successful(BadRequest(UnsupportedOutputFormatRequested))) { of =>
      val sourceFile = request.body
      // TODO no error handling

      val eventualResult = imageService.resizeImage(sourceFile.file, width, height, rotationToApply, of.fileExtension, fill).map { result =>
        sourceFile.clean()
        (result, Some(imageService.info(result)))
      }

      handleResult(of, eventualResult, callback)
    }
  }

  def videoStrip(w: Option[Int], h: Option[Int], callback: Option[String], rotate: Option[Int]) = Action.async(BodyParsers.parse.temporaryFile) { request =>
    val width = w.getOrElse(320)
    val height = h.getOrElse(180)

    inferOutputTypeFromAcceptHeader(request.headers.get("Accept"), supportedImageOutputFormats).fold(Future.successful(BadRequest(UnsupportedOutputFormatRequested))) { of =>
      val sourceFile = request.body
      // TODO no error handling

      val eventualResult = videoService.strip(sourceFile.file, of.fileExtension, width, height, rotate).map { result =>
        sourceFile.clean()
        (result, Some(imageService.info(result)))
      }

      handleResult(of, eventualResult, callback)
    }
  }

  def videoTranscode(width: Option[Int], height: Option[Int], callback: Option[String], rotate: Option[Int]) = Action.async(BodyParsers.parse.temporaryFile) { request =>

    inferOutputTypeFromAcceptHeader(request.headers.get("Accept"), supportedVideoOutputFormats).fold(Future.successful(BadRequest(UnsupportedOutputFormatRequested))) { of =>

      val eventualResult = if (of.mineType.startsWith("image/")) {
        val sourceFile = request.body

        videoService.thumbnail(sourceFile.file, of.fileExtension, width, height, rotate).map { r =>
          sourceFile.clean()
          (r, Some(imageService.info(r)))
        }

      } else {
        val sourceFile = request.body

        videoService.transcode(sourceFile.file, of.fileExtension, width, height, rotate).map { r =>
          sourceFile.clean()
          (r, videoDimensions(mediainfoService.mediainfo(r)))
        }
      }

      handleResult(of, eventualResult, callback)
    }
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

  private def headersFor(of: OutputFormat, dimensions: Option[(Int, Int)]): Seq[(String, String)] = {
    val dimensionHeaders = Seq(dimensions.map(d => (XWidth -> d._1.toString)), dimensions.map(d => (XHeight -> d._2.toString))).flatten
    Seq(CONTENT_TYPE -> of.mineType) ++ dimensionHeaders
  }

  private def handleResult(of: OutputFormat, eventualResult: Future[(File, Option[(Int, Int)])], callback: Option[String]): Future[Result] = {
    callback.fold {
      eventualResult.map { r =>
        Ok.sendFile(r._1, onClose = () => {
          r._1.delete()
        }).withHeaders(headersFor(of, r._2): _*)
      }
    } { c =>
      eventualResult.map { r =>
        Logger.info("Calling back to: " + c)
        WS.url(c).withHeaders(headersFor(of, r._2): _*).
          post(r._1).map { rp =>
          Logger.info("Response from callback url " + callback + ": " + rp.status)
          r._1.delete()
        }
      }
      Future.successful(Accepted(Json.toJson("Accepted")))
    }
  }

}
