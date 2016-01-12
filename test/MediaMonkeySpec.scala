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
  val thirtySeconds = Duration(30, SECONDS)

  "can scale image to fit box along longest axis" in {
    running(TestServer(port)) {
      val eventualResponse = WS.url(localUrl + "/scale?width=800&height=600&rotate=0").post(new File("test/resources/IMG_9758.JPG"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)
      val jsonMeta = metadataForResponse(response)
      (jsonMeta \ "Content-Type").toOption.get.as[String] must equalTo("image/jpeg")
      (jsonMeta \ "width").toOption.get.as[Int] must equalTo(800)
      (jsonMeta \ "height").toOption.get.as[Int] must equalTo(533)
    }
  }

  "can scale and crop image to fill box" in {
    running(TestServer(port)) {
      val eventualResponse = WS.url(localUrl + "/scale?width=800&height=600&rotate=0").post(new File("test/resources/IMG_9758.JPG"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)
      val jsonMeta = metadataForResponse(response)
      (jsonMeta \ "Content-Type").toOption.get.as[String] must equalTo("image/jpeg")
      (jsonMeta \ "width").toOption.get.as[Int] must equalTo(800)
      (jsonMeta \ "height").toOption.get.as[Int] must equalTo(533)
    }
  }

  "scaled images should be returned with there dimensions available in the headers" in {
    running(TestServer(port)) {

      val eventualResponse = WS.url(localUrl + "/scale?width=800&height=600&rotate=0&fill=true").post(new File("test/resources/IMG_9758.JPG"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)
      response.header("X-width").get.toInt must equalTo(800)
      response.header("X-height").get.toInt must equalTo(600)
    }
  }

  "can scale portrait image to fit box along longest axis" in {
    running(TestServer(port)) {
      val eventualResponse = WS.url(localUrl + "/scale?width=600&height=800&rotate=0").post(new File("test/resources/IMG_9803.JPG"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)
      val jsonMeta = metadataForResponse(response)
      (jsonMeta \ "Content-Type").toOption.get.as[String] must equalTo("image/jpeg")
      (jsonMeta \ "width").toOption.get.as[Int] must equalTo(533)
      (jsonMeta \ "height").toOption.get.as[Int] must equalTo(800)
    }
  }

  "can scale and crop portrait image to fill box" in {
    running(TestServer(port)) {
      val eventualResponse = WS.url(localUrl + "/scale?width=600&height=800&rotate=0&fill=true").post(new File("test/resources/IMG_9803.JPG"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)
      val jsonMeta = metadataForResponse(response)
      (jsonMeta \ "Content-Type").toOption.get.as[String] must equalTo("image/jpeg")
      (jsonMeta \ "width").toOption.get.as[Int] must equalTo(600)
      (jsonMeta \ "height").toOption.get.as[Int] must equalTo(800)
    }
  }

  "image output format can be specified via the Accept header" in {
    running(TestServer(port)) {
      val eventualScalingResponse = WS.url(localUrl + "/scale?width=800&height=600&rotate=0").
        withHeaders(("Accept" -> "image/png")).
        post(new File("test/resources/IMG_9758.JPG"))

      val response = Await.result(eventualScalingResponse, tenSeconds)

      response.status must equalTo(OK)
      val jsonMeta = metadataForResponse(response)
      (jsonMeta \ "Content-Type").toOption.get.as[String] must equalTo("image/png")
    }
  }

  "scaled image dimensions should be returned as headers" in {
    running(TestServer(port)) {
      val eventualScalingResponse = WS.url(localUrl + "/scale?width=800&height=600&rotate=0").
        withHeaders(("Accept" -> "image/png")).
        post(new File("test/resources/IMG_9758.JPG"))

      val response = Await.result(eventualScalingResponse, tenSeconds)

      response.header("X-Width").get.toInt must equalTo(800)
      response.header("X-Height").get.toInt must equalTo(533)
    }
  }

  "unknown image output formats should result in a 400 response" in {
    running(TestServer(port)) {
      val eventualScalingResponse = WS.url(localUrl + "/scale?width=800&height=600&rotate=0").
        withHeaders(("Accept" -> "image/sausages")).
        post(new File("test/resources/IMG_9758.JPG"))

      val scalingResponse = Await.result(eventualScalingResponse, tenSeconds)

      scalingResponse.status must equalTo(BAD_REQUEST)
    }
  }

  "sensitive exif data must be stripped from scaled images" in {
    running(TestServer(port)) {
      val eventualResponse = WS.url(localUrl + "/scale?width=800&height=600&rotate=0").post(new File("test/resources/IMG_20150422_122718.jpg"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)
      val jsonMeta = metadataForResponse(response)
      (jsonMeta \ "GPS Latitude").toOption.isEmpty must equalTo(true)
    }
  }

  "can thumbnail videos" in {
    running(TestServer(port)) {
      val eventualResponse = WS.url(localUrl + "/video/transcode?width=320&height=200").
        withHeaders(("Accept" -> "image/jpeg")).
        post(new File("test/resources/IMG_0004.MOV"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)
      val jsonMeta = metadataForResponse(response)
      (jsonMeta \ "Content-Type").toOption.get.as[String] must equalTo("image/jpeg")
    }
  }

  "video thumbnail size can be specified" in {
    running(TestServer(port)) {
      val eventualResponse = WS.url(localUrl + "/video/transcode?width=120&height=100").
        withHeaders(("Accept" -> "image/jpeg")).
        post(new File("test/resources/IMG_0004.MOV"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)
      response.header("X-Width").get.toInt must equalTo(120)
      response.header("X-Height").get.toInt must equalTo(100)

      val jsonMeta = metadataForResponse(response)
      (jsonMeta \ "width").toOption.get.as[Int] must equalTo(120)
      (jsonMeta \ "height").toOption.get.as[Int] must equalTo(100)
    }
  }

  "can transcode videos" in {
    running(TestServer(port)) {
      val eventualResponse = WS.url(localUrl + "/video/transcode?width=284&height=160").post(new File("test/resources/IMG_0004.MOV"))

      val response = Await.result(eventualResponse, thirtySeconds)

      response.status must equalTo(OK)
      response.header("X-Width").get.toInt must equalTo(284)
      response.header("X-Height").get.toInt must equalTo(160)
      (metadataForResponse(response) \ "Content-Type").toOption.get.as[String] must equalTo("video/theora")
    }
  }

  "video output format can be specified via the Accept header" in {
    running(TestServer(port)) {
      val eventualTranscodingResponse = WS.url(localUrl + "/video/transcode").
        withHeaders(("Accept" -> "video/mp4")).
        post(new File("test/resources/IMG_0004.MOV"))

      val response = Await.result(eventualTranscodingResponse, thirtySeconds)

      response.status must equalTo(OK)
      response.header("Content-Type").get must equalTo("video/mp4")
      val jsonMeta = metadataForResponse(response)
      (jsonMeta \ "Content-Type").toOption.get.as[String] must equalTo("application/mp4")
    }
  }

  "video thumbnails can be explictly rotated" in {
    running(TestServer(port)) {
      val eventualResponse = WS.url(localUrl + "/video/transcode?rotate=90").
        withHeaders(("Accept" -> "image/jpeg")).
        post(new File("test/resources/IMG_0004.MOV"))
      val response = Await.result(eventualResponse, tenSeconds)

      response.header("X-Width").get.toInt must equalTo(320)
      response.header("X-Height").get.toInt must equalTo(568)
    }
  }

  "video thumbnail orientation should match video rotation metadata" in {
    running(TestServer(port)) {
      val videoFileWith90DegreeRotationMetadata: File = new File("test/resources/VID_20150822_144123.mp4")
      val eventualResponse = WS.url(localUrl + "/video/transcode").
        withHeaders(("Accept" -> "image/jpeg")).
        post(videoFileWith90DegreeRotationMetadata)
      val response = Await.result(eventualResponse, tenSeconds)

      response.header("X-Width").get.toInt must equalTo(1080)
      response.header("X-Height").get.toInt must equalTo(1920)
    }
  }

  private def metadataForResponse(response: WSResponse): JsValue = {
    val tf = java.io.File.createTempFile("response", "tmp")
    writeResponseBodyToFile(response, tf)

    val eventualMetaResponse = WS.url(localUrl + "/meta").post(tf)
    val metaResponse = Await.result(eventualMetaResponse, tenSeconds)
    Json.parse(metaResponse.body)
  }

}