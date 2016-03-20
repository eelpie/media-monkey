package controllers

import java.io.{File, FileInputStream}

import futures.Retry
import org.apache.commons.codec.digest.DigestUtils
import play.api.Logger
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.ws.WS
import play.api.mvc.{Action, BodyParsers, Controller, Result}
import services.exiftool.ExiftoolService
import services.images.ImageService
import services.mediainfo.{MediainfoInterpreter, MediainfoService}
import services.tika.TikaService
import services.video.VideoService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Application extends Controller with MediainfoInterpreter with Retry {

  val XWidth = "X-Width"
  val XHeight = "X-Height"

  case class OutputFormat(mineType: String, fileExtension: String)
  val supportedImageOutputFormats = Seq(OutputFormat("image/jpeg", "jpg"), OutputFormat("image/png", "png"), OutputFormat("image/gif", "gif"))
  val supportedVideoOutputFormats = Seq(OutputFormat("video/theora", "ogg"), OutputFormat("video/mp4", "mp4"), OutputFormat("image/jpeg", "jpg"))
  val audioOutputFormat = OutputFormat("audio/wav", "wav")

  val UnsupportedOutputFormatRequested = "Unsupported output format requested"

  val mediainfoService: MediainfoService = MediainfoService
  val tika = TikaService
  val exiftool = ExiftoolService
  val imageService = ImageService
  val videoService = VideoService

  def meta = Action.async(BodyParsers.parse.temporaryFile) { request =>

    def inferContentTypeSpecificAttributes(`type`: String, file: File, metadata: Map[String, String]): Future[Map[String, Any]] = {

      def inferImageSpecificAttributes(metadata: Map[String, String]): Seq[(String, Any)] = {
        val imageDimensions: Option[(Int, Int)] = metadata.get("Image Width").flatMap(iw => {
          metadata.get("Image Height").map(ih => {
            (iw.replace(" pixels", "").toInt, ih.replace(" pixels", "").toInt)
          })
        })

        val exifOrientation: Option[String] = metadata.get("Orientation")

        val rotation: Option[Int] = exifOrientation.flatMap { o =>
          val exifRotations = Map[String, Int](
            "Right side, top (Rotate 90 CW)" -> 90,
            "Bottom, right side (Rotate 180)" -> 180,
            "Left side, bottom (Rotate 270 CW)" -> 270
          )
          exifRotations.get(o)
        }

        val orientedImageDimensions: Option[(Int, Int)] = rotation.fold(imageDimensions)(r => {
          imageDimensions.map(im => {
            val orientationsRequiringWidthHeightFlip = Seq(90, 270)
            if (orientationsRequiringWidthHeightFlip.contains(r)) {
              (im._2, im._1)
            } else {
              im
            }
          })
        })

        val orientation = orientedImageDimensions.map(im => {
          if (im._1 > im._2) "landscape" else "portrait"
        })

        Seq(
          orientedImageDimensions.map(id => ("width" -> id._1)),
          orientedImageDimensions.map(id => ("height" -> id._2)),
          orientation.map(o => ("orientation" -> o)),
          rotation.map(r => ("rotation" -> r))
        ).flatten
      }

      def inferVideoSpecificAttributes(file: File, metadata: Map[String, String]): Future[Seq[(String, Any)]] = {

        def parseRotation(r: String): Int = {
          r.replaceAll("[^\\d]", "").toInt
        }

        mediainfoService.mediainfo(file).map { mit =>
          val videoTrackDimensions = videoDimensions(mit)
          val rotation = inferRotation(mit)

          val trackFields: Option[Seq[(String, String)]] = mit.map { ts =>
            ts.filter(t => t.trackType == "General" || t.trackType == "Video").map { t => // TODO work out a good format to preserver all of this information
              t.fields.toSeq
            }.flatten
          }

          val combinedTrackFields: Seq[(String, String)] = Seq(trackFields).flatten.flatten
          val dimensionFields: Seq[(String, Int)] = Seq(videoTrackDimensions.map(d => Seq("width" -> d._1, "height" -> d._2))).flatten.flatten

          combinedTrackFields ++ dimensionFields :+ ("rotation" -> rotation)
        }
      }

      val eventualContentTypeSpecificAttributes: Future[Seq[(String, Any)]] = if (`type` == "image") {
        Future.successful(inferImageSpecificAttributes(metadata))
      } else if (`type` == "video") {
        inferVideoSpecificAttributes(file, metadata)
      } else {
        Future.successful(Seq())
      }

      eventualContentTypeSpecificAttributes.map { i =>
        i.toMap
      }
    }

    val sourceFile = request.body

    val eventualTikaMetadata: Future[Option[Map[String, String]]] = retry(3)(tika.meta(sourceFile.file))

    eventualTikaMetadata.flatMap { tmdo =>

      val metadata = tmdo.fold(Map[String, String]())(tmd => tmd)

      val eventualContentType: Future[Option[String]] = metadata.get(CONTENT_TYPE).fold {
        exiftool.contentType(sourceFile.file)
      }(ct => Future.successful(Some(ct)))

      eventualContentType.flatMap { contentType =>
        contentType.fold(Future.successful(UnsupportedMediaType(Json.toJson("Unsupported media type")))) { ct =>

          val `type`: Option[String] = inferTypeFromContentType(ct)

          `type`.fold(Future.successful(UnsupportedMediaType(Json.toJson("Unsupported media type")))) { t =>

            inferContentTypeSpecificAttributes(t, sourceFile.file, metadata).map { contentTypeSpecificAttributes =>
              val stream: FileInputStream = new FileInputStream(sourceFile.file)
              val md5Hash = DigestUtils.md5Hex(stream)
              stream.close()
              sourceFile.clean()

              val summary: Map[String, String] = Seq(
                Some(("type" -> t)),
                Some(("contentType" -> ct)),
                tika.suggestedFileExtension(ct).map(e => ("fileExtension" -> e)),
                Some("md5" -> md5Hash)
              ).flatten.toMap

              implicit val writes = new Writes[Map[String, Any]] {
                override def writes(o: Map[String, Any]): JsValue = {
                  val map = o.map(i => {
                    val value: Any = i._2
                    val json: JsValue = value match {
                      case i: Int => JsNumber(i)
                      case _ => Json.toJson(value.toString)
                    }
                    (i._1, json)
                  }).toList

                  JsObject(map.toList)
                }
              }

              Ok(Json.toJson(metadata ++ contentTypeSpecificAttributes ++ summary))
            }
          }
        }
      }
    }
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

  def videoAudio(callback: Option[String]) = Action.async(BodyParsers.parse.temporaryFile) { request =>
    val sourceFile = request.body
    val eventualResult = videoService.audio(sourceFile.file).map { result =>
      sourceFile.clean()
      (result, None)
    }
    handleResult(audioOutputFormat, eventualResult, callback)
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

        val outputSize: Option[(Int, Int)] = width.flatMap { w =>
          height.map { h =>
            (w, h)
          }
        }

        videoService.transcode(sourceFile.file, of.fileExtension, outputSize, rotate).flatMap { r =>
          sourceFile.clean()
          mediainfoService.mediainfo(r).map { mi =>
            (r, videoDimensions(mi))
          }
        }
      }

      handleResult(of, eventualResult, callback)
    }
  }

  private def inferTypeFromContentType(contentType: String): Option[String] = {

    val recognisedImageTypes = supportedImageOutputFormats
    val recognisedVideoTypes = supportedVideoOutputFormats ++ Seq(
      OutputFormat("application/mp4", "mp4"),
      OutputFormat("video/x-matroska", "mp4"),
      OutputFormat("video/mpeg", "mp4"),
      OutputFormat("video/3gpp", "mp4"),
      OutputFormat("video/m2ts", "mp4")
    )  // TODO really bad overloading of output format

    if (recognisedImageTypes.exists(it => it.mineType == contentType)) {
      Some("image")
    } else if (recognisedVideoTypes.exists(vt => vt.mineType == contentType)) {
      Some("video")
    } else {
      None
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
        Logger.debug("Calling back to: " + c)
        WS.url(c).withHeaders(headersFor(of, r._2): _*).
          post(r._1).map { rp =>
          Logger.debug("Response from callback url " + callback + ": " + rp.status)
          r._1.delete()
        }
      }
      Future.successful(Accepted(Json.toJson("Accepted")))
    }
  }

}
