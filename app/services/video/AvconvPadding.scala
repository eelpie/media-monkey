package services.video

import play.api.Logger

trait AvconvPadding {

  def padding(sourceDimensions: Option[(Int, Int)], outputSize: Option[(Int, Int)], rotationToApply: Int): Option[String] = {
    outputSize.flatMap { os =>
      sourceDimensions.flatMap { sd =>
        val sourceAspectRatio = (BigDecimal(sd._1) / BigDecimal(sd._2)).setScale(2, BigDecimal.RoundingMode.HALF_DOWN).toDouble
        Logger.info("Source dimensions " + sd + " aspect ratio: " + sourceAspectRatio)

        val outputAspectRatio = (BigDecimal(os._1) / BigDecimal(os._2)).setScale(2, BigDecimal.RoundingMode.HALF_DOWN).toDouble
        Logger.info("Ouptut dimensions " + os + " aspect ratio: " + outputAspectRatio)

        val aspectRatiosDiffer: Boolean = sourceAspectRatio != outputAspectRatio
        val isRotated = (rotationToApply == 90 || rotationToApply == 270)

        if (aspectRatiosDiffer || isRotated) {
          Logger.info("Applying padding")
          Some("pad=ih*16/9:ih:(ow-iw)/2:(oh-ih)/2")

        } else {
          Logger.info("No padding required")
          None
        }
      }
    }
  }

}
