package service

object Settings {
  def get(name: String): String = System.getProperty(name, System.getenv(name))

  lazy val AWS_REGION = get("AWS_REGION")
  lazy val AWS_ACCESS_KEY_ID = get("AWS_ACCESS_KEY_ID")
  lazy val AWS_SECRET_KEY = get("AWS_SECRET_KEY")
}