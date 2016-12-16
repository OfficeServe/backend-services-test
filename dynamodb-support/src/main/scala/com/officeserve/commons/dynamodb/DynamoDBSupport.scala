package com.officeserve.commons.dynamodb

abstract class DynamoDBSupport[T](val table: DynamoDBTable[T]) {

  table.createIfNotExists()

}
