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
  case class AppConfig(tableName: String, username: String, password: String)
  object AppConfig {
    implicit val json = Json.format[AppConfig]
  }
  implicit def stringsToAttributes(strings: Map[String, String]): java.util.Map[String, AttributeValue] = strings.map {
    case (name, value) =>
      name -> new AttributeValue().withS(value)
  }

  def loadConfig(appName: String) = {
    val key = Map("name" -> appName)
    allCatch.opt {
      for {
        result <- Option(client.getItem(f"ACRA-APPLICATIONS", key, true))
        item <- Option(result.getItem)
        value <- Option(item.get("config")).map(_.getS)
      } yield Json.parse(value).as[AppConfig]
    }.flatten
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
  def report(appName: String) = Action.async(parse.text) { implicit request =>
    Future {
      loadConfig(appName) match {
        case None => NotFound
        case Some(config) => decodeBasicAuth(request) match {
          case Some((u, p)) if (config.username == u && config.password == p) =>
            val attributes = Map(
              "timestamp" -> DateUtils.formatISO8601Date(new Date),
              "value" -> request.body
            )
            val result = client.putItem(config.tableName, attributes)
            Logger debug f"Put crash report (${config.tableName}: ${result}"
            Ok
          case _ =>
            Logger warn f"Login failure: from ${request.remoteAddress} to ${request.uri}"
            Unauthorized.withHeaders(("WWW-Authenticate" -> f"""Basic realm="${appName}""""))
        }
      }
    }
  }
}
