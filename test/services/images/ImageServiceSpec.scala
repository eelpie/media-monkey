package services.images

import java.io.File

import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite

import scala.concurrent.Await
import scala.concurrent.duration._

class ImageServiceSpec extends PlaySpec with GuiceOneServerPerSuite {

  val tenSeconds = Duration(10, SECONDS)

  val imageService: ImageService = fakeApplication().injector.instanceOf[ImageService]

  "can determine the dimensions of an image" in {
    val landscapeImageFile = new File("test/resources/IMG_9758.JPG")

    val dimensions: (Int, Int) = Await.result(imageService.info(landscapeImageFile), tenSeconds)

    dimensions._1 must equal (3456)
    dimensions._2 must equal (2304)
  }

}
