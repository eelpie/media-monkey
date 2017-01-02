package controllers

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
    if (OrientationsRequiringWidthHeightFlip.contains(rotation)) {
      (dimensions._2, dimensions._1)
    } else {
      dimensions
    }
  }

  def determineOrientation(imageDimensions: (Int, Int)): String = {
    if (imageDimensions._1 > imageDimensions._2) "landscape" else "portrait"
  }

  def inferImageSpecificAttributes(metadata: Map[String, String]): Seq[(String, Any)] = { // TODO sensible return type
    val imageDimensions = parseImageDimensions(metadata)
    val rotation = metadata.get("Orientation").flatMap(i => parseExifRotationString(i))
    val orientedImageDimensions = imageDimensions.map (d => correctImageDimensionsForRotation(d, rotation))
    val orientation = orientedImageDimensions.map(d => determineOrientation(d))

    Seq(
      orientedImageDimensions.map(id => "width" -> id._1),
      orientedImageDimensions.map(id => "height" -> id._2),
      orientation.map(o => "orientation" -> o),
      rotation.map(r => "rotation" -> r)
    ).flatten
  }

}
