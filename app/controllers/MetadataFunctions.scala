package controllers

import java.io.{File, FileInputStream}

import controllers.Application._
import model.{FormatSpecificAttributes, Summary}
import org.apache.commons.codec.digest.DigestUtils

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait MetadataFunctions {

  private val RecognisedImageTypes = Seq(
    "image/jpeg",
    "image/png",
    "image/gif",
    "image/x-ms-bmp",
    "image/tiff"
  )

  private val RecognisedVideoTypes = Seq(
    "application/mp4",
    "video/3gpp",
    "video/m2ts",
    "video/mp4",
    "video/mpeg",
    "video/quicktime",
    "video/x-flv",
    "video/x-m4v",
    "video/x-matroska",
    "video/x-ms-asf",
    "video/x-msvideo",
    "video/theora",
    "video/webm"
  )

  def inferTypeFromContentType(contentType: String): Option[String] = {
    if (RecognisedImageTypes.contains(contentType)) { // TODO not stricly true; something can be an image without having to be supported
      Some("image")
    } else if (RecognisedVideoTypes.contains(contentType)) {
      Some("video")
    } else {
      None
    }
  }

  def summarise(contentType: String, file: File): Summary = {
    val `type` = inferTypeFromContentType(contentType)

    val stream: FileInputStream = new FileInputStream(file)
    val md5Hash = DigestUtils.md5Hex(stream)
    stream.close()

    Summary(`type`, contentType, tika.suggestedFileExtension(contentType), md5Hash)
  }

  def parseExifRotationString(i: String): Option[Int] = {
    val ExifRotations = Map[String, Int](
      "Right side, top (Rotate 90 CW)" -> 90,
      "Bottom, right side (Rotate 180)" -> 180,
      "Left side, bottom (Rotate 270 CW)" -> 270
    )
    ExifRotations.get(i)
  }

  def parseImageDimensions(metadata: Map[String, String]): Option[(Int, Int)] = {
    metadata.get("Image Width").flatMap{ iw =>
      metadata.get("Image Height").map{ ih =>
        (iw.replace(" pixels", "").toInt, ih.replace(" pixels", "").toInt)
      }
    }
  }

  def correctImageDimensionsForRotation(dimensions: (Int, Int), rotation: Option[Int]): (Int, Int) = {
    val OrientationsRequiringWidthHeightFlip = Seq(90, 270)
    rotation.map { r =>
      if (OrientationsRequiringWidthHeightFlip.contains(r)) {
        (dimensions._2, dimensions._1)
      } else {
        dimensions
      }
    }.getOrElse(dimensions)
  }

  def determineOrientation(imageDimensions: (Int, Int)): String = {
    if (imageDimensions._1 > imageDimensions._2) "landscape" else "portrait"
  }

  def inferImageSpecificAttributes(metadata: Map[String, String]): FormatSpecificAttributes = {

    val imageDimensions = parseImageDimensions(metadata)
    val rotation = metadata.get("Orientation").flatMap(i => parseExifRotationString(i))
    val orientedImageDimensions = imageDimensions.map (d => correctImageDimensionsForRotation(d, rotation))
    val orientation = orientedImageDimensions.map(d => determineOrientation(d))

    FormatSpecificAttributes(
      orientedImageDimensions.map(d => d._1),
      orientedImageDimensions.map(d => d._2),
      rotation,
      orientation)
  }

  def inferVideoSpecificAttributes(file: File): Future[FormatSpecificAttributes] = {

    def parseRotation(r: String): Int = {
      r.replaceAll("[^\\d]", "").toInt
    }

    mediainfoService.mediainfo(file).map { mit =>
      val videoTrackDimensions = videoDimensions(mit)
      val rotation = inferRotation(mit)

      val trackFields: Option[Seq[(String, String)]] = mit.map { ts =>
        ts.filter(t => t.trackType == "General" || t.trackType == "Video").flatMap { t => // TODO work out a good format to preserver all of this information
          t.fields.toSeq
        }
      }

      val combinedTrackFields: Seq[(String, String)] = Seq(trackFields).flatten.flatten
      val dimensionFields: Seq[(String, Int)] = Seq(videoTrackDimensions.map(d => Seq("width" -> d._1, "height" -> d._2))).flatten.flatten

      // combinedTrackFields ++ dimensionFields :+ ("rotation" -> rotation)

      FormatSpecificAttributes(
        videoTrackDimensions.map(d => d._1),
        videoTrackDimensions.map(d => d._2),
        Some(rotation),
        None)
    }
  }

}
