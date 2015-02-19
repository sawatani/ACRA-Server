import play.api.{ Application, Logger }
import play.api.mvc.WithFilters

object Global extends WithFilters(BasicAuthFilter) {
  override def onStart(app: Application) {
    Logger info f"Application is started: $app"
  }
}
