package services.tika

import java.io.{File, FileInputStream}

import com.ning.http.client.{AsyncHttpClient, Response}
import play.api.{Play, Logger}
import play.api.libs.json.{JsString, JsObject, JsValue, Json}
import play.api.libs.ws.WS
import play.api.Play.current

trait TikaService {

  val tikaUrl: String

  def meta(f: File): Option[Map[String, String]] = {
    Logger.info("Posting submitted file to Taki for typing")
    val asyncHttpClient: AsyncHttpClient = WS.client.underlying
    val putBuilder = asyncHttpClient.preparePut(tikaUrl + "/meta").
      addHeader("Accept", "application/json; charset=UTF-8").
      setBody(new FileInputStream(f))

    val response: Response = asyncHttpClient.executeRequest(putBuilder.build()).get

    if (response.getStatusCode == 200) {

      val tikaJson: JsValue = Json.parse(response.getResponseBody)
      tikaJson match {
        case JsObject(fields) => {
          val toMap: Map[String, String] = fields.map((f: (String, JsValue)) => {
            val key: String = f._1
            val value: Option[String] = f._2 match {
              case JsString(j) => Some(j.toString())
              case _ => None
            }
            value.map(v => (key, v))
          }).flatten.toMap
          Some(toMap)
        }
        case _ => None
      }

    } else {
      Logger.warn("Unexpected response from Tika: " + response.getStatusCode + " / " + response.getResponseBody)
      Some(Map())
    }
  }

}

object TikaService extends TikaService {

  override lazy val tikaUrl = Play.configuration.getString("tika.url").get

}