package services.exiftool

import java.io.File

import org.specs2.mutable.Specification

import scala.concurrent.Await
import scala.concurrent.duration._

class ExiftoolServiceSpec extends Specification {

  val tenSeconds = Duration(10, SECONDS)

  "can detect content type of media files" in {
    val videoFile = new File("test/resources/IMG_0004.MOV")

    val contentType  = Await.result(ExiftoolService.contentType(videoFile), tenSeconds)

    contentType must equalTo(Some("video/quicktime"))
  }

  "can parse exiftool json output" in {
    val exiftoolOutput = scala.io.Source.fromFile("test/resources/exiftool.json").mkString

    val contentType = ExiftoolService.parse(exiftoolOutput)

    contentType must equalTo(Some("video/quicktime"))
  }

}
