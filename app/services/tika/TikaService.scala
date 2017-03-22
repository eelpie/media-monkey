package services.tika

import java.io.{File, FileInputStream}

import com.ning.http.client.{AsyncHttpClient, Response}
import org.apache.tika.mime.MimeTypes
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.json.{JsObject, JsString, JsValue, Json}
import play.api.libs.ws.WS
import play.api.{Logger, Play}

import scala.collection.JavaConverters._
import scala.concurrent.Future

trait TikaService {

  val tikaUrl: String

  def meta(f: File): Future[Option[Map[String, String]]] = {

    implicit val executionContext = Akka.system.dispatchers.lookup("meta-processing-context")

    Future {
      Logger.info("Posting submitted file to Tika for typing")
      val asyncHttpClient: AsyncHttpClient = WS.client.underlying

      val putBuilder: AsyncHttpClient#BoundRequestBuilder = asyncHttpClient.preparePut(tikaUrl + "/meta").
        addHeader("Accept", "application/json; charset=UTF-8").
        setRequestTimeout(10000).
        setBody(new FileInputStream(f))

      val request = putBuilder.build()
      val response: Response = asyncHttpClient.executeRequest(request).get
      if (response.getStatusCode == 200) {
        Json.parse(response.getResponseBody) match {
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

  def suggestedFileExtension(contentType: String): Option[String] = {
    val mimeType = MimeTypes.getDefaultMimeTypes.forName(contentType)

    val tikaSuggestion = mimeType.getExtensions.asScala.headOption.map { e =>
      e.replaceFirst("\\.", "")
    }

    tikaSuggestion.map { e =>
      if (e == "mp4s") "mp4" else e
    }
  }

}

object TikaService extends TikaService {
  override lazy val tikaUrl = Play.configuration.getString("tika.url").get
}
