package services.geo

import model.LatLong
import us.fatehi.pointlocation6709.parse.PointLocationParser

import scala.util.Try

trait ISO6709Parser {

  def parseISO6709LatLong(l: String): Option[LatLong] = {
      Try(PointLocationParser.parsePointLocation(l)).flatMap { p =>
        val lat = p.getLatitude.getDegrees
        val lon = p.getLongitude.getDegrees

        Try(LatLong(BigDecimal(lat).setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble,
          BigDecimal(lon).setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble))
      }.toOption
  }

}
