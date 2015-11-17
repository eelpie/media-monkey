package services.mediainfo

import org.specs2.mutable.Specification

class MediainfoParserSpec extends Specification {

  "can parse track information from mediainfo XML output" in {
    val mediainfoOutput = scala.io.Source.fromFile("test/resources/mediainfo.xml").mkString

    val tracks = MediainfoParser.parse(mediainfoOutput)

    tracks.size must equalTo(3)
    tracks.seq(1).width.get must equalTo(1920)
  }

}
