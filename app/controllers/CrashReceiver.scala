package controllers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.Play
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.ws.{ WS, WSResponse }
import play.api.mvc.{ Action, AnyContent, Controller, Result }

object CrashReceiver extends Controller {
  def report(appName: String) = Action.async(parse.json) { implicit request =>
    val json = request.body
    Future(Ok)
  }
}
