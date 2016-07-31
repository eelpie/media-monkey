package services.video

import play.api.Logger

trait AvconvPadding {

  val SixteenNine = (BigDecimal(16) / BigDecimal(9)).setScale(10, BigDecimal.RoundingMode.HALF_DOWN).toDouble

  def padding(sourceDimensions: Option[(Int, Int)], outputSize: Option[(Int, Int)], sourceAspectRatio: Option[Double], rotationToApply: Int): Option[String] = {
    outputSize.flatMap { os =>
      sourceDimensions.flatMap { sd =>

        val effectiveSourceDimensions = if (rotationToApply == 90 || rotationToApply == 270) {
          (sd._2, sd._1)
        } else {
          sd
        }
        // TODO invert user supplied aspect ration on rotate

        val effectiveSourceAspectRatio = sourceAspectRatio.getOrElse((BigDecimal(effectiveSourceDimensions._1) / BigDecimal(effectiveSourceDimensions._2)).setScale(10, BigDecimal.RoundingMode.HALF_DOWN).toDouble)
        Logger.info("Source dimensions " + effectiveSourceDimensions + " aspect ratio: " + effectiveSourceDimensions)

        val outputAspectRatio = (BigDecimal(os._1) / BigDecimal(os._2)).setScale(10, BigDecimal.RoundingMode.HALF_DOWN).toDouble
        Logger.info("Ouptut dimensions " + os + " aspect ratio: " + outputAspectRatio)

        val d: Double = (sourceAspectRatio - outputAspectRatio).abs
        val aspectRatiosDiffer: Boolean = outputAspectRatio > effectiveSourceAspectRatio && d > 0.05

        if (aspectRatiosDiffer) {
          Logger.info("Applying padding")

          val paddedWidth = if (d < 0.05) effectiveSourceDimensions._1 else (BigDecimal(effectiveSourceDimensions._2) * SixteenNine).setScale(0, BigDecimal.RoundingMode.HALF_UP).rounded.toInt
          val x = BigDecimal(paddedWidth - effectiveSourceDimensions._1) / 2
          val paddingParameter = Some("pad=width=" + paddedWidth + ":height=" + effectiveSourceDimensions._2 + ":x=" + x.rounded.toInt)
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
