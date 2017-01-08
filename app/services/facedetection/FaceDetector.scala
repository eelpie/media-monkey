package services.facedetection

import java.io.File

import org.joda.time.{DateTime, Duration}
import org.openimaj.image.ImageUtilities
import org.openimaj.image.processing.face.detection.HaarCascadeDetector
import play.api.Logger

import scala.collection.JavaConversions._
import scala.concurrent.Future

class FaceDetector {

  def detectFaces(source: File): Future[Seq[model.DetectedFace]] = {
      Future.successful{
        val start = DateTime.now()

        val detector = new HaarCascadeDetector()

        val detected = detector.detectFaces(ImageUtilities.readF(source)).map { r =>
          val b = r.getBounds()
          model.DetectedFace(bounds = model.Bounds(
            (b.getTopLeft.getX.toInt, b.getTopLeft.getY.toInt), (b.getBottomRight.getX.toInt, b.getBottomRight.getY.toInt)),
            confidence = r.getConfidence)
          }

        Logger.info("Detected " + detected.size + " in " + new Duration(start, DateTime.now))
        detected
      }
  }

}

object FaceDetector extends FaceDetector