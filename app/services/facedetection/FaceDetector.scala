package services.facedetection

import java.io.InputStream

import model.DetectedFace

import scala.concurrent.Future

class FaceDetector {

  def detectFaces(image: InputStream): Future[Seq[DetectedFace]] = {
    Future.successful(Seq[DetectedFace]())
  }

}
