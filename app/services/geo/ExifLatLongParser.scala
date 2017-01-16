package services.geo

import model.LatLong

import scala.math.BigDecimal
import scala.util.Try

trait ExifLatLongParser {

  def extractGPSLatLongFromMetadata(metadata: Map[String, String]): Option[LatLong] = {
    metadata.get("GPS Latitude").flatMap { lat =>
      metadata.get("GPS Latitude Ref").flatMap { latRef =>
        metadata.get("GPS Longitude").flatMap { lon =>
          metadata.get("GPS Longitude Ref").flatMap { lonRef =>
            parseLatLongString(lat).flatMap { dlat =>
              parseLatLongString(lon).flatMap { dlon =>
                val clat = if (latRef == "S" && dlat > 0) dlat * -1 else dlat;
                val clon = if (lonRef == "W" && dlon > 0) dlon * -1 else dlon;
                Try(LatLong(clat, clon)).toOption
              }
            }
          }
        }
      }
    }
  }

  def parseLatLongString(i: String): Option[Double] = {

    val DegreeMinuteSecondFormat = """^(-?)(.*)° (.*)' (.*)"$""".r
    val MinutesToDecimal = BigDecimal(1 / 60D)
    val SecondsToDecimal = BigDecimal(1 / 3600D)

    i match {
      case DegreeMinuteSecondFormat(n, d, m, s) => {
        val degrees = BigDecimal(d)
        val minutes = BigDecimal(m) * MinutesToDecimal
        val seconds = BigDecimal(s)  * SecondsToDecimal

        val v = degrees + minutes + seconds
        val withSign = if (n == "-") v * -1 else v
        Some(withSign.setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble)
      }
      case _ => None
    }
  }

}
