package services.exiftool

import java.io.File

import javax.inject.Inject
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ExiftoolServiceSpec @Inject()(exifToolService: ExiftoolService) extends PlaySpec with GuiceOneServerPerSuite {

  val tenSeconds = Duration(10, SECONDS)

  "can detect content type of media files" in {
    val videoFile = new File("test/resources/IMG_0004.MOV")

    val contentType  = Await.result(exifToolService.contentType(videoFile), tenSeconds)

    contentType must equal (Some("video/quicktime"))
  }

  "can add XMP tags to images" in {
    val imageFile = new File("test/resources/IMG_0004.MOV")

    val tagsToAdd = Seq(
      ("XMP-dc", "Title", "A test title"),
      ("XMP-dc", "Description", "A test description")
    )

    val withMetadata: File = Await.result(exifToolService.addMeta(imageFile, tagsToAdd), tenSeconds).get

    val xmp = Await.result(exifToolService.extractXmp(withMetadata), tenSeconds).get

    xmp must contain("A test title")
    xmp must contain("A test description")
  }

}
