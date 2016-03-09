package services.exiftool

import java.io.File

import org.specs2.mutable.Specification

class ExiftoolServiceSpec extends Specification {

  "can detect content type of media files" in {
    val videoFile = new File("IMG_0004.MOV")

    val contentType  = ExiftoolService.contentType(videoFile)

    contentType.get must equalTo(Some("video/quicktime"))
  }

  "can parse exiftool json output" in {
    val exiftoolOutput = scala.io.Source.fromFile("test/resources/exiftool.json").mkString

    val contentType = ExiftoolService.parse(exiftoolOutput)

    contentType.get must equalTo(Some("video/quicktime"))
  }

}
