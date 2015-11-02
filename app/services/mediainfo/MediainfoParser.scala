package services.mediainfo

import model.Track

import scala.xml.{Elem, Node, NodeSeq}

class MediainfoParser {

  def parse(mediainfo: String): Seq[Track] = {

    def parsePixels(t: Node): Int = {
      t.text.stripSuffix(" pixels").toInt
    }

    val xml: Elem = scala.xml.XML.loadString(mediainfo)

    val tracks: NodeSeq = xml.\\("track")
    tracks.map(t => {
      val trackType = t \@ "type"
      val w = (t \ "Width").headOption.map(t => parsePixels(t))
      val h = (t \ "Height").headOption.map(t => parsePixels(t))
      Track(trackType, w, h)
    })
  }

}

object MediainfoParser extends MediainfoParser
