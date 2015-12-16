package services.images

import java.io.File

import org.specs2.mutable.Specification

class ImageServiceSpec extends Specification {

  "can determine the dimensions of an image" in {
    val landscapeImageFile = new File("test/resources/IMG_9758.JPG")

    val dimensions = ImageService.info(landscapeImageFile)

    dimensions._1 must equalTo(3456)
    dimensions._2 must equalTo(2304)
  }

}
