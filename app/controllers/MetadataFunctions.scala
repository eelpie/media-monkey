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

}
