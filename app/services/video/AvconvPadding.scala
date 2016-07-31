package services.video

import play.api.Logger

trait AvconvPadding {

  val SixteenNine = (BigDecimal(16) / BigDecimal(9)).setScale(10, BigDecimal.RoundingMode.HALF_DOWN).toDouble

  def padding(sourceDimensions: Option[(Int, Int)], outputSize: Option[(Int, Int)], rotationToApply: Int): Option[String] = {
    outputSize.flatMap { os =>
      sourceDimensions.flatMap { sd =>
        val sourceAspectRatio = (BigDecimal(sd._1) / BigDecimal(sd._2)).setScale(10, BigDecimal.RoundingMode.HALF_DOWN).toDouble
        Logger.info("Source dimensions " + sd + " aspect ratio: " + sourceAspectRatio)

        val outputAspectRatio = (BigDecimal(os._1) / BigDecimal(os._2)).setScale(10, BigDecimal.RoundingMode.HALF_DOWN).toDouble
        Logger.info("Ouptut dimensions " + os + " aspect ratio: " + outputAspectRatio)

        val aspectRatiosDiffer: Boolean = sourceAspectRatio != outputAspectRatio
        val isRotated = (rotationToApply == 90 || rotationToApply == 270)

        if (aspectRatiosDiffer || isRotated) {
          Logger.info("Applying padding")

          val paddedWidth: BigDecimal = (BigDecimal(sd._2) * SixteenNine).setScale(0, BigDecimal.RoundingMode.UP)
          val paddingParameter = Some("pad=" + Seq(paddedWidth.rounded.toInt, sd._2, 0, 0).mkString(":"))
          Logger.info("Generated padding parameter: " + paddingParameter)
          paddingParameter

        } else {
          Logger.info("No padding required")
          None
        }
      }
    }
  }

}
