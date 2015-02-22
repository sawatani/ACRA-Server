package controllers

import java.util.Date

import scala.concurrent.Future

import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc.{ Action, Controller, RequestHeader }

import org.apache.commons.codec.binary.Base64
import org.fathens.play.util.Exception.allCatch

import com.amazonaws.util.DateUtils

import service.AWS.DynamoDB

object CrashReceiver extends Controller {
  // Access to Database
  case class AppConfig(tableName: String, username: String, password: String) {
    def checkAccount(theUsername: String, thePassword: String): Boolean = {
      username == theUsername && password == thePassword
    }
    def putReport(id: String, report: JsValue): Boolean = {
      val table = DynamoDB table f"ACRA-${tableName}"
      val item = DynamoDB.item(id).withString("REPORT", report.toString).withString("CREATED_AT", DateUtils.formatISO8601Date(new Date))
      Logger debug f"Putting crash report (${table}): ${id}"
      val result = allCatch.opt { Option(table putItem item) }.flatten
      Logger debug f"Put crash report (${table}): ${id}: ${result}"
      result.isDefined
    }
  }
  object AppConfig {
    implicit val json = Json.format[AppConfig]
    def load(appName: String) = {
      Logger debug f"Finding application: ${appName}"
      allCatch.opt {
        for {
          table <- Option(DynamoDB table "ACRA-APPLICATIONS")
          item <- Option(table getItem DynamoDB.spec(appName))
          value <- Option(item getJSONPretty "CONFIG")
        } yield Json.parse(value).as[AppConfig]
      }.flatten
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
  def report(appName: String, id: String) = Action.async(parse.json) { implicit request =>
    Future {
      AppConfig.load(appName) match {
        case None => NotFound
        case Some(config) => decodeBasicAuth(request) match {
          case Some((u, p)) if config.checkAccount(u, p) =>
            if (config.putReport(id, request.body)) Ok else InternalServerError
          case _ =>
            Logger warn f"Login failure: from ${request.remoteAddress} to ${request.uri}"
            Unauthorized.withHeaders(("WWW-Authenticate" -> f"""Basic realm="${appName}""""))
        }
      }
    }
  }
}
