import java.io.File

import org.specs2.mutable._
import play.api.Play.current
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{WS, WSResponse}
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, _}

class MetadataSpec extends Specification with ResponseToFileWriter {

  val port: Port = 3334
  val localUrl = "http://localhost:" + port.toString
  val tenSeconds = Duration(10, SECONDS)
  val thirtySeconds = Duration(30, SECONDS)

  "can detect images" in {
    running(TestServer(port)) {
      val eventualResponse = WS.url(localUrl + "/meta").post(new File("test/resources/IMG_9758.JPG"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)
      val jsonResponse = Json.parse(response.body)
      (jsonResponse \ "type").toOption.get.as[String] must equalTo("image")
    }
  }

  "image size and orientation should be summarised" in {
    running(TestServer(port)) {
      val eventualResponse = WS.url(localUrl + "/meta").post(new File("test/resources/IMG_20150422_122718.jpg"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)
      val jsonResponse = Json.parse(response.body)
      (jsonResponse \ "width").toOption.get.as[Int] must equalTo(2448)
      (jsonResponse \ "height").toOption.get.as[Int] must equalTo(3264)
      (jsonResponse \ "orientation").toOption.get.as[String] must equalTo("portrait")
    }
  }

  "image orientation should account for EXIF rotation corrections" in {
    running(TestServer(port)) {
      val eventualResponse = WS.url(localUrl + "/meta").post(new File("test/resources/IMG_9803.JPG"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)

      val jsonResponse = Json.parse(response.body)
      (jsonResponse \ "width").toOption.get.as[Int] must equalTo(2304)
      (jsonResponse \ "height").toOption.get.as[Int] must equalTo(3456)
      (jsonResponse \ "orientation").toOption.get.as[String] must equalTo("portrait")
    }
  }

  "EXIF data can be extracted from images" in {
    running(TestServer(port)) {
      val eventualResponse = WS.url(localUrl + "/meta").post(new File("test/resources/IMG_20150422_122718.jpg"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)
      val jsonResponse = Json.parse(response.body)
      (jsonResponse \ "GPS Latitude").toOption.get.as[String] must equalTo("37° 45' 18.26\"")
    }
  }

  "can detect videos" in {
    running(TestServer(port)) {
      val eventualResponse = WS.url(localUrl + "/meta").post(new File("test/resources/IMG_0004.MOV"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)
      val jsonResponse = Json.parse(response.body)
      (jsonResponse \ "type").toOption.get.as[String] must equalTo("video")
    }
  }

  "video size can be detected" in {
    running(TestServer(port)) {
      val eventualResponse = WS.url(localUrl + "/meta").post(new File("test/resources/IMG_0004.MOV"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)
      val jsonResponse = Json.parse(response.body)
      (jsonResponse \ "width").toOption.get.as[Int] must equalTo(568)
      (jsonResponse \ "height").toOption.get.as[Int] must equalTo(320)
    }
  }

  "video metadata should include mediainfo data" in {
    running(TestServer(port)) {
      val eventualResponse = WS.url(localUrl + "/meta").post(new File("test/resources/IMG_0004.MOV"))

      val response = Await.result(eventualResponse, tenSeconds)

      (Json.parse(response.body) \ "Display_aspect_ratio").toOption.get.as[String] must equalTo("16:9")
      (Json.parse(response.body) \ "Frame_rate").toOption.get.as[String] must equalTo("30.000 fps")
    }
  }

  "video metadata should include inferred rotation" in {
    running(TestServer(port)) {
      val eventualResponse = WS.url(localUrl + "/meta").post(new File("test/resources/VID_20150822_144123.mp4"))

      val response = Await.result(eventualResponse, tenSeconds)
      val rotationField = (Json.parse(response.body) \ "rotation").toOption

      rotationField.get.as[Int] must equalTo(90)
    }
  }

  "md5 hash should be included in metadata to assist with duplicate detection" in {
    running(TestServer(port)) {
      val eventualResponse = WS.url(localUrl + "/meta").post(new File("test/resources/IMG_20150422_122718.jpg"))

      val response = Await.result(eventualResponse, tenSeconds)

      val jsonResponse = Json.parse(response.body)
      (jsonResponse \ "md5").toOption.get.as[String] must equalTo("8eecbf514c06b9a98744b9ef7bc33ec0")
    }
  }

}