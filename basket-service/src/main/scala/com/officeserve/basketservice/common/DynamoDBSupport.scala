package com.officeserve.basketservice.common

abstract class DynamoDBSupport[T](val table: DynamoDBTable[T]) {

  table.createIfNotExists()

}
