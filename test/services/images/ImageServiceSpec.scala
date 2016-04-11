package services.images

import java.io.File

import org.specs2.mutable._
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.Await
import scala.concurrent.duration._

class ImageServiceSpec extends Specification {

  val port: Port = 3334
  val tenSeconds = Duration(10, SECONDS)

  "can determine the dimensions of an image" in {
    running(TestServer(port)) {
      val landscapeImageFile = new File("test/resources/IMG_9758.JPG")

      val dimensions: (Int, Int) = Await.result(ImageService.info(landscapeImageFile), tenSeconds)

      dimensions._1 must equalTo(3456)
      dimensions._2 must equalTo(2304)
    }
  }

}
