
package controllers

import java.io.File

import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, BodyParsers, Controller}
import services.tika.TikaService

object Application extends Controller {

  val tikaService: TikaService = TikaService

  def meta = Action(BodyParsers.parse.temporaryFile) { request =>
    val f: File = request.body.file
    Logger.info("Received meta request to " + f.getAbsolutePath)

    Ok(Json.toJson(tikaService.meta(f)))
  }

}