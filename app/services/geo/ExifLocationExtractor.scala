package services.geo

import model.LatLong

trait ExifLocationExtractor extends ExifLatLongParser with ISO6709Parser {

  def extractLocationFrom(metadata: Map[String, String]): Option[LatLong] = {

    val possibleExifLocation = extractGPSLatLongFromMetadata(metadata)
    val possibleQuicktimeLocation = metadata.get("comapplequicktimelocationISO6709").flatMap (l => parseISO6709LatLong(l))
    val possibleAndroidLocation = metadata.get("xyz").flatMap ( l => parseISO6709LatLong(l))

    Seq(possibleExifLocation, possibleQuicktimeLocation, possibleAndroidLocation).flatten.headOption.flatMap { ll =>
      if (ll != LatLong(0, 0)) {
        Some(ll)
      } else {
        None
      }
    }
  }

}
