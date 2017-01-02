package controllers

import java.io.File

import controllers.Application._
import model.FormatSpecificAttributes

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait MetadataFunctions {

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
