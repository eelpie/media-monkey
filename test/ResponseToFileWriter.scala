import java.io.{FileOutputStream, BufferedOutputStream, File}

import play.api.libs.ws.WSResponse

trait ResponseToFileWriter {

  def writeResponseBodyToFile(response: WSResponse, scaled: File): Unit = {
    val target = new BufferedOutputStream(new FileOutputStream(scaled))
    try response.bodyAsBytes.foreach(target.write(_)) finally target.close;
  }

}
