package model

import play.api.libs.json.{Format, Json}

case class Track(`type`: String, fields: Map[String, String])

object Track {
  implicit val formats: Format[Track] = Json.format[Track]
}
