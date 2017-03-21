package services.facedetection

import java.io.File

import org.specs2.mutable.Specification
import play.api.test.Helpers.running
import play.api.test.{Port, TestServer}

import scala.concurrent.Await
import scala.concurrent.duration._

class FaceDetectorSpec extends Specification {

  val port: Port = 3334

  "can detect faces" in {
    running(TestServer(port)) {
      val imageWithSingleFace = new File("test/resources/5282722938_e0e2515624_o.jpg")
      val detectedFaces = Await.result(FaceDetector.detectFaces(imageWithSingleFace), Duration(10, SECONDS))
      detectedFaces.size must equalTo(1)
    }
  }

}
