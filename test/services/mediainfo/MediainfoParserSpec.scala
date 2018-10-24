package services.mediainfo

import org.specs2.mutable.Specification

class MediainfoParserSpec extends Specification {

  "can parse track information from mediainfo XML output" in {
    val mediainfoOutput = scala.io.Source.fromFile("test/resources/mediainfo.xml").mkString

    val tracks = new MediainfoParser().parse(mediainfoOutput)

    tracks.size must equalTo(3)
    tracks.seq(1).`type` must equalTo("Video")
    tracks.seq(1).fields.get("Width").get must equalTo("1 920 pixels")
  }

}
