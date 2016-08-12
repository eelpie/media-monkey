package services.video

import org.specs2.mutable._

class AvconvPaddingSpec extends Specification with AvconvPadding {

  "can generate padding arguments from source and destination dimensions" in {
      padding(Some((854, 480)), Some((296, 163)), None, 0) must equalTo(None)
      padding(Some((568, 320)), Some((568, 320)), None, 90) must equalTo(Some("pad=width=1010:height=568:x=345"))
  }

  "can crop to reduce aspect ratio" in {
    padding(Some((568, 320)), Some((320, 320)), None, 0) must equalTo(Some("crop=width=320:height=320:x=0"))
    padding(Some((568, 320)), Some((120, 100)), None, 0) must equalTo(Some("crop=width=120:height=100:x=0"))
  }

}
