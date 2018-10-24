import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSClient

trait TestWSClient {
  implicit lazy val system = ActorSystem("tests")
  implicit val materializer = ActorMaterializer()
  val ws = AhcWSClient()
}