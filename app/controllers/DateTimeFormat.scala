package controllers

import org.joda.time.format.{DateTimeFormatter, ISODateTimeFormat}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json._

class DateTimeFormat extends Format[DateTime] {

  private val IsoDateTime : DateTimeFormatter = ISODateTimeFormat.dateTime().withZone(DateTimeZone.UTC)

  override def writes(o: DateTime): JsValue = {
    JsString(IsoDateTime.print(o))
  }

  override def reads(json: JsValue): JsResult[DateTime] = {
    json match {
      case JsString(s) => JsSuccess(IsoDateTime.parseDateTime(s))
      case _ => throw new RuntimeException()
    }
  }

  def print(o: DateTime): String = {
    IsoDateTime.print(o)
  }

}

object DateTimeFormat extends DateTimeFormat
