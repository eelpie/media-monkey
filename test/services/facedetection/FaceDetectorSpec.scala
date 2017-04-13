package services.facedetection

import java.io.File

import org.specs2.mutable.Specification
import play.api.test.Helpers.running
import play.api.test.{Port, TestServer}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.global

class FaceDetectorSpec extends Specification {

  val port: Port = 3334

  private val imageWithSingleFace = new File("test/resources/5282722938_e0e2515624_o.jpg")

  "can detect faces" in {
    running(TestServer(port)) {
      implicit val ec = global

      val detectedFaces = Await.result(FaceDetector.detectFaces(imageWithSingleFace), Duration(10, SECONDS))

      detectedFaces.size must equalTo(1)
    }
  }

  "face positions are reported as percentages" in {
    running(TestServer(port)) {
      implicit val ec = global

      val detectedFaces = Await.result(FaceDetector.detectFaces(imageWithSingleFace), Duration(10, SECONDS))
      val detectedFaceBounds = detectedFaces.head.bounds

      detectedFaceBounds.topLeft.x must equalTo(27.3)
      detectedFaceBounds.topLeft.y must equalTo(30.3)

      detectedFaceBounds.bottomRight.x must equalTo(60.3)
      detectedFaceBounds.bottomRight.y must equalTo(67.9)
    }
  }

}
