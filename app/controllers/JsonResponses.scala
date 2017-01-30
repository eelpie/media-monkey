package controllers

import play.api.libs.json.Json

trait JsonResponses {

  var JsonAccepted = Json.toJson("Accepted")

}
