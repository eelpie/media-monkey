package services.video

import org.specs2.mutable._

class AvconvPaddingSpec extends Specification with AvconvPadding {

  "can generate padding arguments from source and destination dimensions" in {
      padding(Some((854, 480)), Some((296, 163)), 0) must equalTo(None)
      padding(Some((568, 320)), Some((568, 320)), 90) must equalTo(Some("pad=568:320:0:0"))
  }

}
