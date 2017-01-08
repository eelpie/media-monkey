package services.facedetection

import java.io.{File, FileInputStream}

import org.specs2.mutable.Specification

import scala.concurrent.Await
import scala.concurrent.duration._

class FaceDetectorSpec extends Specification {

  "can detect faces" in {
    val imageWithSingleFace = new File("test/resources/5282722938_e0e2515624_o.jpg")
    val detectedFaces = Await.result(FaceDetector.detectFaces(new FileInputStream(imageWithSingleFace)), Duration(10, SECONDS))
    detectedFaces.size must equalTo(1)
  }

}
