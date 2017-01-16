package model

import play.api.libs.json.{Format, Json}

case class LatLong(latitude: Double, longitude: Double) {
  val MaxLatitude = 90
  val MaxLongitude = 180
  require(latitude >= -MaxLatitude && latitude <= MaxLatitude)
  require(longitude >= -MaxLongitude && longitude <= MaxLongitude)
}

object LatLong {
  implicit val formats: Format[LatLong] = Json.format[LatLong]
}