package service

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.dynamodbv2.document.{ DynamoDB => Delegate, Item }
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec

object AWS {
  lazy val region = Settings.AWS_REGION
  lazy val credential = {
    val id = Settings.AWS_ACCESS_KEY_ID
    val key = Settings.AWS_SECRET_KEY
    new BasicAWSCredentials(id, key)
  }
  object DynamoDB {
    val client = {
      val c = new com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient(credential)
      c.setEndpoint(f"dynamodb.${region}.amazonaws.com")
      c
    }
    val delegate = new Delegate(client)
    def table(name: String) = delegate.getTable(name)
    def spec(id: String) = new GetItemSpec().withPrimaryKey("ID", id)
    def item(id: String) = new Item().withPrimaryKey("ID", id)
  }
}
