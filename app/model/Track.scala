package model

import play.api.libs.json.{Format, Json}

case class Track(trackType: String, width: Option[Int], height: Option[Int])

object Track {
  implicit val formats: Format[Track] = Json.format[Track]
}
