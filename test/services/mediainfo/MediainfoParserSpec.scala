package services.mediainfo

import java.io.File

import org.specs2.mutable.Specification

class MediainfoParserSpec extends Specification {


  "can parse track information from mediainfo XML output" in {

    val mediainfoOutput: File = new File("test/resources/mediainfo.xml")


    1 must equalTo(2)
    //val tracks = MediainfoParser.parse(mediainfoOutput)

    //tracks.size must equalTo(3)
  }

}
