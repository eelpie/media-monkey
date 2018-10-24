package services.images

import java.io.File

import javax.inject.Inject
import org.specs2.mutable._
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.Await
import scala.concurrent.duration._

class ImageServiceSpec @Inject()(imageService: ImageService) extends Specification {

  val port: Port = 3334
  val tenSeconds = Duration(10, SECONDS)

  "can determine the dimensions of an image" in {
    running(TestServer(port)) {
      val landscapeImageFile = new File("test/resources/IMG_9758.JPG")

      val dimensions: (Int, Int) = Await.result(imageService.info(landscapeImageFile), tenSeconds)

      dimensions._1 must equalTo(3456)
      dimensions._2 must equalTo(2304)
    }
  }

}
