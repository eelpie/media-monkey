package services.facedetection

import java.io.InputStream
import java.util

import org.openimaj.image.processing.face.detection.{DetectedFace, HaarCascadeDetector}
import org.openimaj.image.{FImage, ImageUtilities}

import scala.collection.JavaConversions._
import scala.concurrent.Future

class FaceDetector {

  def detectFaces(source: InputStream): Future[Seq[model.DetectedFace]] = {
      Future.successful{
        val detector = new HaarCascadeDetector()
        val image: FImage = ImageUtilities.readF(source)
        detector.detectFaces(image).map { r =>
          val b = r.getBounds()
          model.DetectedFace(bounds = model.Bounds(
            (b.getTopLeft.getX.toInt, b.getTopLeft.getY.toInt), (b.getBottomRight.getX.toInt, b.getBottomRight.getY.toInt)),
            confidence = r.getConfidence)
          }
      }
  }

}

object FaceDetector extends FaceDetector