package services.tika

import java.io.{File, FileInputStream}

import com.ning.http.client.{AsyncHttpClient, Response}
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WS
import play.api.Play.current

class TikaService {

  val tikaUrl = "http://localhost:9998"

  def meta(f: File): JsValue = {
    Logger.info("Posting submitted file to Taki for typing")
    val asyncHttpClient: AsyncHttpClient = WS.client.underlying
    val putBuilder = asyncHttpClient.preparePut("http://localhost:9998/meta").
      addHeader("Accept", "application/json").
      setBody(new FileInputStream(f))

    val response: Response = asyncHttpClient.executeRequest(putBuilder.build()).get

    val tikaJson: JsValue = Json.parse(response.getResponseBody)
    tikaJson
  }

}

object TikaService extends TikaService