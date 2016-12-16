package com.officeserve.basketservice.service

import com.officeserve.basketservice.persistence.Order
import officeserve.commons.spray.webutils.Error

sealed trait ServiceException {
  this: Exception =>
  val toException = this
}

case class InvalidRequestException(messages: String*) extends Exception(messages.mkString("\n")) with ServiceException

case class PaymentFailedException(message: String) extends Exception(message) with ServiceException

case class AlreadyProcessedException(message: String, order: Order) extends Exception(message) with ServiceException

case class DynamoDBErrorException(message: String) extends Exception(message) with ServiceException

case class OperationNotAllowedException(message: String) extends Exception(message) with ServiceException

case class NotFoundException(message: String) extends Exception(message) with ServiceException

case class OrderAlreadyCancelledException(order: Order) extends Exception(s"Order ${order.id} was already cancelled") with ServiceException

case class ServiceGenericException(error: Error) extends Exception
