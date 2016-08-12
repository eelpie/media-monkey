package services.video

import org.specs2.mutable._

class AvconvPaddingSpec extends Specification with AvconvPadding {

  "can generate padding arguments from source and destination dimensions" in {
      padding(Some((854, 480)), Some((296, 163)), None, 0) must equalTo(None)
      padding(Some((568, 320)), Some((568, 320)), None, 90) must equalTo(Some("pad=width=1008:height=568:x=344"))
  }

  "can crop to reduce aspect ratio" in {
    padding(Some((1920, 1080)), Some((320, 320)), None, 0) must equalTo(Some("crop=1080:1080"))
  }

}
