package service

import com.amazonaws.auth.BasicAWSCredentials

object AWS {
  lazy val region = Settings.AWS_REGION
  lazy val credential = {
    val id = Settings.AWS_ACCESS_KEY_ID
    val key = Settings.AWS_SECRET_KEY
    new BasicAWSCredentials(id, key)
  }
  object DynamoDB {
    lazy val client = {
      val c = new com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient(credential)
      c.setEndpoint(f"dynamodb.${region}.amazonaws.com")
      c
    }
  }
}
