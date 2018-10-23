package services.mediainfo

import model.Track

import scala.xml.Elem

class MediainfoParser {

  def parse(mediainfo: String): Seq[Track] = {
    val xml: Elem = scala.xml.XML.loadString(mediainfo)
    xml.\\("track").map{ t =>

      val trackType = t \@ "type"
      val fields = t.nonEmptyChildren.map { c =>
        (c.label, c.text)
      }.toMap

      Track(trackType, fields)
    }
  }

}
