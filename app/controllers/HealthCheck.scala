package controllers

import scala.concurrent.Future

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{ Action, Controller }

object HealthCheck extends Controller {
  def ping = Action.async { implicit request =>
    Future(Ok)
  }
}
