import java.io.{FileOutputStream, BufferedOutputStream, File}

import org.specs2.mutable._
import play.api.Play.current
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{WS, WSResponse}
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, Future}

class MediaMonkeySpec extends Specification {

  val port: Port = 3334
  val localUrl = "http://localhost:" + port.toString
  val tenSeconds = Duration(10, SECONDS)

  "can detect images" in {
    running(TestServer(port)) {

      val eventualResponse: Future[WSResponse] = WS.url(localUrl + "/meta").post(new File("test/resources/IMG_9758.JPG"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)
      val jsonResponse = Json.parse(response.body)

      (jsonResponse \ "type").toOption.get.as[String] must equalTo("image")
    }
  }

  "image size and orientation should be summarised" in {
    running(TestServer(port)) {

      val eventualResponse: Future[WSResponse] = WS.url(localUrl + "/meta").post(new File("test/resources/IMG_20150422_122718.jpg"))

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

      val eventualResponse: Future[WSResponse] = WS.url(localUrl + "/meta").post(new File("test/resources/IMG_9803.JPG"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)
      val jsonResponse = Json.parse(response.body)
      (jsonResponse \ "width").toOption.get.as[Int] must equalTo(2304)
      (jsonResponse \ "height").toOption.get.as[Int] must equalTo(3456)
      (jsonResponse \ "orientation").toOption.get.as[String] must equalTo("portrait")
    }
  }

  "exif data can be extracted from images" in {
    running(TestServer(port)) {

      val eventualResponse: Future[WSResponse] = WS.url(localUrl + "/meta").post(new File("test/resources/IMG_20150422_122718.jpg"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)
      val jsonResponse = Json.parse(response.body)
      (jsonResponse \ "GPS Latitude").toOption.get.as[String] must equalTo("37° 45' 18.26\"")

    }
  }

  "can detect videos" in {
    running(TestServer(port)) {

      val eventualResponse: Future[WSResponse] = WS.url(localUrl + "/meta").post(new File("test/resources/IMG_0004.MOV"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)
      val jsonResponse = Json.parse(response.body)

      val result = jsonResponse \ "type"
      result.toOption.get.as[String] must equalTo("video")
    }
  }

  "can scale image" in {
    running(TestServer(port)) {
      val eventualResponse: Future[WSResponse] = WS.url(localUrl + "/scale?width=800&height=600&rotate=0").post(new File("test/resources/IMG_9758.JPG"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)
    }
  }

  "sensitive exif data must be stripped from scaled images" in {
    running(TestServer(port)) {

      val eventualResponse: Future[WSResponse] = WS.url(localUrl + "/scale?width=800&height=600&rotate=0").post(new File("test/resources/IMG_20150422_122718.jpg"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)
      response.bodyAsBytes

      val scaled: File = File.createTempFile("image", ".tmp")

      val target = new BufferedOutputStream(new FileOutputStream(scaled))
      try response.bodyAsBytes.foreach( target.write(_) ) finally target.close;

      val eventualMetaResponse: Future[WSResponse] = WS.url(localUrl + "/meta").post(scaled)
      val metaResponse = Await.result(eventualMetaResponse, tenSeconds)

      metaResponse.status must equalTo(OK)
      val jsonResponse = Json.parse(metaResponse.body)
      (jsonResponse \ "GPS Latitude").toOption.isEmpty must equalTo(true)
    }
  }



  "can thumbnail videos" in {
    running(TestServer(port)) {
      val eventualResponse: Future[WSResponse] = WS.url(localUrl + "/video/thumbnail").post(new File("test/resources/IMG_0004.MOV"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)
    }
  }

}