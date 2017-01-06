package services.openimaj

import java.io.{FileInputStream, File, InputStream}
import java.util

import org.openimaj.image.{ImageUtilities, FImage}
import org.openimaj.image.processing.face.detection.{DetectedFace, HaarCascadeDetector}
import org.specs2.mutable.Specification
import scala.collection.JavaConversions._

class FaceDetector extends Specification {

  "can detect faces" in {

    def detectFaces(source: InputStream): Unit = {
      val detector = new HaarCascadeDetector()
      val image: FImage = ImageUtilities.readF(source)
      val result: util.List[DetectedFace] = detector.detectFaces(image)
      println(result)
      result.map { r =>
        println(r.getBounds + " / " + r.getConfidence)
      }
    }

    val landscapeImageFile = new File("test/resources/face.jpg")
    detectFaces(new FileInputStream(landscapeImageFile))
    1 must equalTo(1)
  }

}
