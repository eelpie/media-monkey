package services.facedetection

import java.io.{File, FileInputStream}

import org.specs2.mutable.Specification

import scala.concurrent.Await
import scala.concurrent.duration._

class FaceDetectorSpec extends Specification {

  "can detect faces" in {
    val imageWithSingleFace = new File("test/resources/face.jpg")
    val detectedFaces = Await.result(FaceDetector.detectFaces(imageWithSingleFace), Duration(10, SECONDS))
    detectedFaces.size must equalTo(1)
  }

}
