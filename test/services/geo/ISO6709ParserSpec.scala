package services.geo

import model.LatLong
import org.specs2.mutable._

class ISO6709ParserSpec extends Specification with ISO6709Parser {

  "can parse ISO6709 locations found in quicktime meta data to lat long" in {
    parseISO6709LatLong("+51.5081-000.1759+013.305/") must equalTo(Some(LatLong(51.5081, -0.1759)))
  }

  "gracefully returns none for non parsable inputs" in {
    parseISO6709LatLong("meh") must equalTo(None)
  }

}
