package services.geo

import org.specs2.mutable._

class ExifLatLongParserSpec extends Specification with ExifLatLongParser {

  "can parse text exif lat long field to decimal" in {
    parseLatLongString("""50° 43' 15.88"""") must equalTo(Some(50.7211))
    parseLatLongString("""-1° 51' 24.89"""") must equalTo(Some(-1.8569))
  }

}
