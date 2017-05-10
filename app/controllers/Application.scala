package controllers

import java.io.File

import futures.Retry
import org.joda.time.{DateTime, Duration}
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.json._
import play.api.libs.ws.WS
import play.api.mvc.{Action, BodyParsers, Controller, Result}
import services.images.ImageService
import services.mediainfo.{MediainfoInterpreter, MediainfoService}
import services.video.VideoService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

object Application extends Controller with Retry with MediainfoInterpreter with JsonResponses with ReasonableWaitTimes {

  val XWidth = "X-Width"
  val XHeight = "X-Height"

  case class OutputFormat(mineType: String, fileExtension: String)

  val SupportedImageOutputFormats = Seq(OutputFormat("image/jpeg", "jpg"), OutputFormat("image/png", "png"), OutputFormat("image/gif", "gif"), OutputFormat("image/x-icon", "ico"))
  val SupportedVideoOutputFormats = Seq(OutputFormat("video/theora", "ogg"), OutputFormat("video/mp4", "mp4"), OutputFormat("image/jpeg", "jpg"))
  val AudioOutputFormat = OutputFormat("audio/wav", "wav")

  val UnsupportedOutputFormatRequested = "Unsupported output format requested"

  val mediainfoService = MediainfoService
  val imageService = ImageService
  val videoService = VideoService

  def crop(width: Int, height: Int, x: Int, y: Int) = Action.async(BodyParsers.parse.temporaryFile) { request =>

    val sourceFile = request.body

    inferOutputTypeFromAcceptHeader(request.headers.get("Accept"), SupportedImageOutputFormats).fold(Future.successful(BadRequest(UnsupportedOutputFormatRequested))) { of =>
      // TODO no error handling

      implicit val imageProcessingExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("image-processing-context")

      val eventualResult = imageService.cropImage(sourceFile.file, width, height, x, y, of.fileExtension).flatMap { ro =>
        sourceFile.clean()

        ro.map{ r =>
          imageService.info(r).map { dimensions =>
            Some(r, Some(dimensions), of)
          }
        }.getOrElse(Future.successful(None))
      }

      handleResult(eventualResult, None, imageProcessingExecutionContext)
    }
  }

  def scale(w: Option[Int], h: Option[Int], rotate: Option[Int], callback: Option[String], fill: Option[Boolean], gravity: Option[String]) = Action.async(BodyParsers.parse.temporaryFile) { request =>
    val width = w
    val height = h
    val rotationToApply = rotate.getOrElse(0)

    val sourceFile = request.body

    inferOutputTypeFromAcceptHeader(request.headers.get("Accept"), SupportedImageOutputFormats).fold(Future.successful(BadRequest(UnsupportedOutputFormatRequested))) { of =>
      // TODO no error handling

      implicit val imageProcessingExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("image-processing-context")

      val eventualResult = imageService.resizeImage(sourceFile.file, width, height, rotationToApply, of.fileExtension, fill.getOrElse(false), gravity).flatMap { ro =>
        sourceFile.clean()

        ro.map { r =>
          imageService.info(r).map { dimensions =>
            Some(r, Some(dimensions), of)
          }
        }.getOrElse(Future.successful(None))
      }

      handleResult(eventualResult, callback, imageProcessingExecutionContext)
    }
  }

  def videoStrip(w: Option[Int], h: Option[Int], callback: Option[String], rotate: Option[Int], aspectRatio: Option[Double]) = Action.async(BodyParsers.parse.temporaryFile) { request =>
    val width = w.getOrElse(320)
    val height = h.getOrElse(180)

    implicit val videoProcessingExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("video-processing-context")

    inferOutputTypeFromAcceptHeader(request.headers.get("Accept"), SupportedImageOutputFormats).fold(Future.successful(BadRequest(UnsupportedOutputFormatRequested))) { of =>
      val sourceFile = request.body
      val eventualResult = videoService.strip(sourceFile.file, of.fileExtension, width, height, aspectRatio, rotate).flatMap { ro =>
        sourceFile.clean()

        ro.map { r =>
          imageService.info(r).map { dimensions =>
            Some(r, Some(dimensions), of)
          }
        }.getOrElse(Future.successful(None))
      }

      handleResult(eventualResult, callback, videoProcessingExecutionContext)
    }
  }

  def videoAudio(callback: Option[String]) = Action.async(BodyParsers.parse.temporaryFile) { request =>
    val sourceFile = request.body

    implicit val videoProcessingExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("video-processing-context")

    val eventualResult = videoService.audio(sourceFile.file).map { ro =>
      sourceFile.clean()

      ro.map { r =>
        val noDimensions: Option[(Int, Int)] = None
        Some(r, noDimensions, AudioOutputFormat)
      }.getOrElse(None)
    }
    handleResult(eventualResult, callback, videoProcessingExecutionContext)
  }


  def videoTranscode(width: Option[Int], height: Option[Int], callback: Option[String], rotate: Option[Int], aspectRatio: Option[Double]) = Action.async(BodyParsers.parse.temporaryFile) { request =>
    val sourceFile = request.body

    implicit val videoProcessingExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("video-processing-context")

    inferOutputTypeFromAcceptHeader(request.headers.get("Accept"), SupportedVideoOutputFormats).fold(Future.successful(BadRequest(UnsupportedOutputFormatRequested))) { of =>

      val eventualResult = if (of.mineType.startsWith("image/")) {

        videoService.thumbnail(sourceFile.file, of.fileExtension, width, height, aspectRatio, rotate).flatMap { ro =>
          sourceFile.clean()

          ro.map { r =>
            imageService.info(r).map { dimensions =>
              Some(r, Some(dimensions), of)
            }
          }.getOrElse(Future.successful(None))
        }

      } else {

        val outputSize: Option[(Int, Int)] = width.flatMap { w =>
          height.map { h =>
            (w, h)
          }
        }

        videoService.transcode(sourceFile.file, of.fileExtension, outputSize, aspectRatio, rotate).flatMap { ro =>
          sourceFile.clean()

          ro.map{ r =>
            mediainfoService.mediainfo(r).map { mi =>
              Some(r, videoDimensions(mi), of)
            }
          }.getOrElse(Future.successful(None))
        }
      }

      handleResult(eventualResult, callback, videoProcessingExecutionContext)
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

  private def handleResult(eventualResult: Future[Option[(File, Option[(Int, Int)], OutputFormat)]], callback: Option[String], executionContext: ExecutionContext): Future[Result] = {

    def headersFor(of: OutputFormat, dimensions: Option[(Int, Int)]): Seq[(String, String)] = {
      val dimensionHeaders = Seq(dimensions.map(d => XWidth -> d._1.toString), dimensions.map(d => XHeight -> d._2.toString)).flatten
      Seq(CONTENT_TYPE -> of.mineType) ++ dimensionHeaders
    }

    callback.fold {

      implicit val ec = executionContext

      eventualResult.map { ro =>
        ro.fold {
          UnprocessableEntity(Json.toJson("Could not process file"))

        } { r =>
          Logger.info("Sending file")
          val of: OutputFormat = r._3
          Ok.sendFile(r._1, onClose = () => {
            Logger.debug("Deleting tmp file after sending file: " + r._1)
            r._1.delete()
          }).withHeaders(headersFor(of, r._2): _*)
        }
      }(ec)

    } { c =>
      implicit val ec = executionContext

      eventualResult.map { ro =>
        Logger.info("Mapping")
        ro.fold {
          Logger.warn("Failed to process file; not calling back")

        } { r =>
          val startTime = DateTime.now
          Logger.info("Calling back to " + c)
          val of: OutputFormat = r._3

          val callbackPost = WS.url(c).withHeaders(headersFor(of, r._2): _*).withRequestTimeout(ThirtySeconds.toMillis).post(r._1)

          callbackPost.map { rp =>
            val duration = new Duration(startTime, DateTime.now)
            Logger.info("Response from callback url " + c + ": " + rp.status + " after " + duration.toStandardSeconds.toStandardDays)
            Logger.debug("Deleting tmp file after calling back: " + r._1)
            r._1.delete()

          }.recover {
            case c: java.net.ConnectException =>
              Logger.warn("Could not connect to callback url '" + c + "' caller has gone away?. Cleaning up tmp file: " + r._1)
              r._1.delete()
            case t: Throwable =>
              Logger.error("Media callback failed. Cleaning up tmp file: " + r._1, t)
              r._1.delete()
          }
        }
      }(ec)

      Logger.info("Returning accepted")
      Future.successful(Accepted(JsonAccepted))
    }

  }

}
