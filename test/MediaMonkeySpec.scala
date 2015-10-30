import java.io.{FileOutputStream, BufferedOutputStream, File}

import org.specs2.mutable._
import play.api.Play.current
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{WS, WSResponse}
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, Future}

class MediaMonkeySpec extends Specification with ResponseToFileWriter {

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

  "EXIF data can be extracted from images" in {
    running(TestServer(port)) {

      val eventualResponse: Future[WSResponse] = WS.url(localUrl + "/meta").post(new File("test/resources/IMG_20150422_122718.jpg"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)
      val jsonResponse = Json.parse(response.body)
      (jsonResponse \ "GPS Latitude").toOption.get.as[String] must equalTo("37° 45' 18.26\"")

    }
  }

  "can scale image" in {
    running(TestServer(port)) {
      val eventualResponse: Future[WSResponse] = WS.url(localUrl + "/scale?width=800&height=600&rotate=0").post(new File("test/resources/IMG_9758.JPG"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)
    }
  }

  "image output format can be specified via the Accept header" in {
    running(TestServer(port)) {
      val eventualScalingResponse: Future[WSResponse] = WS.url(localUrl + "/scale?width=800&height=600&rotate=0").
        withHeaders(("Accept", "image/png")).
        post(new File("test/resources/IMG_9758.JPG"))

      val scalingResponse = Await.result(eventualScalingResponse, tenSeconds)

      val tf = java.io.File.createTempFile("scaled", "tmp")
      writeResponseBodyToFile(scalingResponse, tf)

      val eventualMetaResponse: Future[WSResponse] = WS.url(localUrl + "/meta").post(tf)
      val metaResponse = Await.result(eventualMetaResponse, tenSeconds)
      val jsonMeta = Json.parse(metaResponse.body)
      (jsonMeta \ "Content-Type").toOption.get.as[String] must equalTo("image/png")
    }
  }

  "unknown image output formats should result in a 400 response" in {
    running(TestServer(port)) {
      val eventualScalingResponse: Future[WSResponse] = WS.url(localUrl + "/scale?width=800&height=600&rotate=0").
        withHeaders(("Accept", "image/sausages")).
        post(new File("test/resources/IMG_9758.JPG"))

      val scalingResponse = Await.result(eventualScalingResponse, tenSeconds)

      scalingResponse.status must equalTo(BAD_REQUEST)
    }
  }

  "sensitive exif data must be stripped from scaled images" in {
    running(TestServer(port)) {

      val eventualResponse: Future[WSResponse] = WS.url(localUrl + "/scale?width=800&height=600&rotate=0").post(new File("test/resources/IMG_20150422_122718.jpg"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)

      val scaled: File = File.createTempFile("image", ".tmp")

      writeResponseBodyToFile(response, scaled)

      val eventualMetaResponse: Future[WSResponse] = WS.url(localUrl + "/meta").post(scaled)
      val metaResponse = Await.result(eventualMetaResponse, tenSeconds)

      metaResponse.status must equalTo(OK)
      val jsonResponse = Json.parse(metaResponse.body)
      (jsonResponse \ "GPS Latitude").toOption.isEmpty must equalTo(true)
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

  "can thumbnail videos" in {
    running(TestServer(port)) {
      val eventualResponse: Future[WSResponse] = WS.url(localUrl + "/video/thumbnail").post(new File("test/resources/IMG_0004.MOV"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)

      val thumbnail: File = File.createTempFile("thumbnail", ".tmp")
      writeResponseBodyToFile(response, thumbnail)

      val eventualMetaResponse: Future[WSResponse] = WS.url(localUrl + "/meta").post(thumbnail)
      val metaResponse = Await.result(eventualMetaResponse, tenSeconds)
      val jsonResponse = Json.parse(metaResponse.body)
      val jsonMeta = Json.parse(metaResponse.body)
      (jsonMeta \ "Content-Type").toOption.get.as[String] must equalTo("image/jpeg")
    }
  }

  "can transcode videos" in {
    running(TestServer(port)) {
      val eventualResponse: Future[WSResponse] = WS.url(localUrl + "/video/transcode").post(new File("test/resources/IMG_0004.MOV"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)

      val transcoded: File = File.createTempFile("transcoded", ".tmp")
      writeResponseBodyToFile(response, transcoded)

      val eventualMetaResponse: Future[WSResponse] = WS.url(localUrl + "/meta").post(transcoded)
      val metaResponse = Await.result(eventualMetaResponse, tenSeconds)
      val jsonResponse = Json.parse(metaResponse.body)
      val jsonMeta = Json.parse(metaResponse.body)
      (jsonMeta \ "Content-Type").toOption.get.as[String] must equalTo("video/ogg")
    }
  }

  "video output format can be specified via the Accept header" in {
    running(TestServer(port)) {
      val eventualTranscodingResponse: Future[WSResponse] = WS.url(localUrl + "/video/transcode").
        withHeaders(("Accept", "video/mp4")).
        post(new File("test/resources/IMG_0004.MOV"))

      val trancodingResponse = Await.result(eventualTranscodingResponse, tenSeconds)

      val tf = java.io.File.createTempFile("transcoded", "tmp")
      writeResponseBodyToFile(trancodingResponse, tf)

      val eventualMetaResponse: Future[WSResponse] = WS.url(localUrl + "/meta").post(tf)
      val metaResponse = Await.result(eventualMetaResponse, tenSeconds)
      val jsonMeta = Json.parse(metaResponse.body)
      (jsonMeta \ "Content-Type").toOption.get.as[String] must equalTo("video/mp4")
    }
  }

}