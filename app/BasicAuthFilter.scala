
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.Logger
import play.api.mvc.{ Filter, RequestHeader, Result, Results }

import org.apache.commons.codec.binary.Base64

import service.Settings

object BasicAuthFilter extends Filter {
  case class Realm(name: String, url: String, username: String, password: String) {
    def isMatch(request: RequestHeader) = {
      url startsWith request.uri
    }
    def canAuthorize(request: RequestHeader): Boolean = {
      decodeBasicAuth(request) match {
        case Some(c) => (c._1 == username && c._2 == password)
        case None    => false
      }
    }
  }
  private lazy val realms = {
    Settings.BASIC_AUTH_REALMS.split("\\s+").map { key =>
      def get(a: String) = Settings get f"BASIC_AUTH_REALM_${key}_${a}"
      Realm(get("NAME"), get("URL"), get("USERNAME"), get("PASSWORD"))
    }
  }
  def decodeBasicAuth(request: RequestHeader): Option[(String, String)] = {
    for {
      authText <- request.headers.get("authorization")
      Array(basic, encoded) = authText.split(" ", 2)
      if basic.toUpperCase == "BASIC"
      decoded = Base64 decodeBase64 encoded
      Array(username, password) = new String(decoded, "UTF-8").split(":", 2)
    } yield (username, password)
  }
  def apply(nextFilter: (RequestHeader) => Future[Result])(request: RequestHeader): Future[Result] = {
    realms.find(_ isMatch request) match {
      case None => nextFilter(request)
      case Some(realm) => if (realm canAuthorize request) nextFilter(request) else Future {
        val remote = request.headers.get("x-forwarded-for") getOrElse request.remoteAddress
        Logger.warn(f"Login failure: from ${remote} to ${request.uri}")
        Results.Unauthorized.withHeaders(("WWW-Authenticate" -> f"""Basic realm="${realm.name}""""))
      }
    }
  }
}
