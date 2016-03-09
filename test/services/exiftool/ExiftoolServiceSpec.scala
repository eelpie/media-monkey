package services.exiftool

import java.io.File

import org.specs2.mutable.Specification

class ExiftoolServiceSpec extends Specification {

  "can parse exif info from media files" in {
    val videoFile = new File("IMG_0004.MOV")

    val exif: Option[Map[String, String]] = ExiftoolService.meta(videoFile)

    exif.get.get("MIMETYPE").get must equalTo("video/quicktime")
  }

}
