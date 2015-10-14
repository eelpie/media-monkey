import java.io.File

import org.specs2.mutable._
import play.api.Play.current
import play.api.libs.ws.{WS, WSResponse}
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, Future}

class MediaMonkeySpec extends Specification {

  val port: Port = 3334
  val localUrl = "http://localhost:" + port.toString
  val tenSeconds = Duration(10, SECONDS)

  "can scale image" in {
    running(TestServer(port)) {
      val eventualResponse: Future[WSResponse] = WS.url(localUrl + "/scale?width=800&height=600&rotate=0").post(new File("test/resources/IMG_9758.JPG"))

      val response = Await.result(eventualResponse, tenSeconds)

      response.status must equalTo(OK)
    }
  }

}