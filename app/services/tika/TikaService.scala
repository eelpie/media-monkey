package services.tika

import java.io.{File, FileInputStream}

import com.ning.http.client.{AsyncHttpClient, Response}
import play.api.{Play, Logger}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WS
import play.api.Play.current

trait TikaService {

  val tikaUrl: String

  def meta(f: File): JsValue = {
    Logger.info("Posting submitted file to Taki for typing")
    val asyncHttpClient: AsyncHttpClient = WS.client.underlying
    val putBuilder = asyncHttpClient.preparePut(tikaUrl + "/meta").
      addHeader("Accept", "application/json; charset=UTF-8").
      setBody(new FileInputStream(f))

    val response: Response = asyncHttpClient.executeRequest(putBuilder.build()).get

    val tikaJson: JsValue = Json.parse(response.getResponseBody)
    tikaJson
  }

}

object TikaService extends TikaService {

  override lazy val tikaUrl = Play.configuration.getString("tika.url").get

}