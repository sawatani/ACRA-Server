package controllers

import java.util.Date

import scala.annotation.implicitNotFound
import scala.collection.JavaConversions._
import scala.concurrent.Future

import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc.{ Action, Controller, RequestHeader }

import org.apache.commons.codec.binary.Base64
import org.fathens.play.util.Exception.allCatch

import com.amazonaws.services.dynamodbv2.model._
import com.amazonaws.util.DateUtils

import service.AWS.DynamoDB.client

object CrashReceiver extends Controller {
  implicit def stringsToAttributes(strings: Map[String, String]): java.util.Map[String, AttributeValue] = strings.map {
    case (name, value) =>
      name -> new AttributeValue().withS(value)
  }
  // Access to Database
  case class AppConfig(tableName: String, username: String, password: String) {
    def checkAccount(theUsername: String, thePassword: String): Boolean = {
      username == theUsername && password == thePassword
    }
    def putReport(id: String, report: JsValue): Boolean = {
      val name = f"ACRA-${tableName}"
      val attributes = Map(
        "ID" -> id,
        "REPORT" -> report.toString,
        "CREATED_AT" -> DateUtils.formatISO8601Date(new Date)
      )
      Logger debug f"Putting crash report (${name}): ${id}"
      val result = allCatch.opt { Option(client.putItem(name, attributes)) }.flatten
      Logger debug f"Put crash report (${name}): ${id}: ${result}"
      result.isDefined
    }
  }
  object AppConfig {
    implicit val json = Json.format[AppConfig]
    def load(appName: String) = {
      val key = Map("ID" -> appName)
      Logger debug f"Finding application: ${key}"
      allCatch.opt {
        for {
          result <- Option(client.getItem(f"ACRA-APPLICATIONS", key, true))
          item <- Option(result.getItem)
          value <- Option(item.get("CONFIG")).map(_.getS)
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
