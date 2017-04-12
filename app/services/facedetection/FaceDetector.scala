package services.facedetection

import java.io.File

import model.Point
import org.joda.time.{DateTime, Duration}
import org.openimaj.image.ImageUtilities
import org.openimaj.image.processing.face.detection.HaarCascadeDetector
import play.api.Logger

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}

class FaceDetector {

  def detectFaces(source: File)(implicit ec: ExecutionContext): Future[Seq[model.DetectedFace]] = {
    Future {
        Logger.info("Detecting faces in file: " + source.getAbsolutePath)
        val start = DateTime.now()

        val detectFaces1 = new HaarCascadeDetector().detectFaces(ImageUtilities.readF(source))

        val detected = detectFaces1.map { r =>
          val b = r.getBounds()
          model.DetectedFace(bounds = model.Bounds(
            Point(b.getTopLeft.getX.toInt, b.getTopLeft.getY.toInt), Point(b.getBottomRight.getX.toInt, b.getBottomRight.getY.toInt)),
            confidence = r.getConfidence)
          }

        Logger.info("Detected " + detected.size + " in " + new Duration(start, DateTime.now))
        detected
    }
  }

}

object FaceDetector extends FaceDetector