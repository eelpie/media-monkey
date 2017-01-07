package services.facedetection

import java.io.{File, FileInputStream}

import org.specs2.mutable.Specification

class FaceDetectorSpec extends Specification {

  "can detect faces" in {
    val landscapeImageFile = new File("test/resources/face.jpg")
    val detectedFaces = FaceDetector.detectFaces(new FileInputStream(landscapeImageFile))
    println(detectedFaces)
    1 must equalTo(1)
  }

}
