
import scala.concurrent.Future
import scala.util.matching.Regex

import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{ Filter, RequestHeader, Result, Results }

import org.apache.commons.codec.binary.Base64
import org.fathens.play.util.Exception.allCatch

import service.Settings

object BasicAuthFilter extends Filter {
  case class Realm(name: String, pathRegex: Regex, username: String, password: String) {
    def authorize(request: RequestHeader): Boolean = { // Does not care pathRegex
      decodeBasicAuth(request) match {
        case Some(c) => (c._1 == username && c._2 == password)
        case None    => false
      }
    }
  }
  def isMatch(request: RequestHeader)(hasRegex: (Regex, _)): Boolean = {
    val regex = hasRegex._1
    val m = regex.findFirstIn(request.uri)
    Logger debug f"BasicAuth: ${request} with ${regex}: $m"
    m.isDefined
  }
  private lazy val realms = {
    Settings.BASIC_AUTH_REALMS.split("\\s+").toList.flatMap { key =>
      def get(a: String) = Option(Settings get f"BASIC_AUTH_REALM_${key}_${a}").filter(_.length > 0)
      for {
        name <- get("NAME")
        path <- get("PATH").flatMap(r => allCatch.opt(r.r))
        username <- get("USERNAME")
        password <- get("PASSWORD")
      } yield Realm(name, path, username, password)
    }.groupBy(_.pathRegex)
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
    realms.find(isMatch(request)) match {
      case None => nextFilter(request)
      case Some((_, list)) => list.find(_ authorize request) match {
        case Some(_) => nextFilter(request)
        case None => Future {
          val remote = request.headers.get("x-forwarded-for") getOrElse request.remoteAddress
          Logger.warn(f"Login failure: from ${remote} to ${request.uri}")
          Results.Unauthorized.withHeaders(("WWW-Authenticate" -> f"""Basic realm="${list.head.name}""""))
        }
      }
    }
  }
}
