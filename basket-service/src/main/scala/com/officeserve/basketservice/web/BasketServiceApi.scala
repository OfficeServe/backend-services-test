package com.officeserve.basketservice.web

import akka.actor.ActorRefFactory
import com.officeserve.basketservice.clients.{BadGetProductsRequestException, CatalogueClient, ProductNotFoundException}
import com.officeserve.basketservice.service._
import com.officeserve.basketservice.settings.BasketConfig
import officeserve.commons.spray.auth.TrustedAuth
import officeserve.commons.spray.webutils.Error
import spray.http.StatusCodes._
import spray.httpx.SprayJsonSupport
import spray.json.{JsObject}
import spray.routing._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class BasketServiceApi(basketService: BasketService,
                       paymentService: PaymentService,
                       orderService: OrderService,
                       paymentMethodService: PaymentMethodService,
                       catalogueClient: CatalogueClient,
                       deliveryService: DeliveryService,
                       basketConfig: BasketConfig)
                      (implicit val actorRefFactory: ActorRefFactory, implicit val tradingDays: TradingDays) extends TrustedAuth
  with HttpService
  with SprayJsonSupport
  with BasketJsonProtocol
  with Adapter {

  import tradingDays._

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  private val healthCheck = {
    path("healthcheck") {
      get {
        complete("Basket-services up and running")
      }
    }
  }

  private val basket = pathPrefix("basket") {
    pathEnd {
      post {
        entity(as[BasketItemRequest]) { basketItem =>
          onSuccess(catalogueClient.getAvailableProducts(basketItem.items.map(_.productId).toSeq)) { products =>
            onSuccess(basketService.calculateBasket(UserRep.unknownUserId, basketItem, products)) { basketrep =>
              complete(fromBasketResponseToBasketRep(basketrep))
            }
          }
        }
      }
    }
  }

  private val orders = pathPrefix("orders") {
    auth { userId =>
      pathEnd {
        get {
          onSuccess(orderService.getUserOrders(userId)) { orders =>
            complete(PageResponse[OrderRep](orders))
          }
        } ~
          handleRejections(minimumOrderHandler) {
            post {
              entity(as[BasketItemRequest]) { basketItem =>
                onSuccess(catalogueClient.getAvailableProducts(basketItem.items.map(_.productId).toSeq)) { products =>
                  onSuccess(basketService.calculateBasket(userId, basketItem, products)) { basketResponse =>
                    validate(!basketResponse.hints.exists(hints => hints.exists(_.name == ServiceHint.basketMinimumOrder_key)), "Minimum order not met") {
                      onSuccess(orderService.createOrder(basketResponse.basket, products, userId)) { order =>
                        val blockDate = BlockDateRep(
                          getDeliveryFromDay(order.maxLeadTime),
                          holidays,
                          getSlots,
                          getTableTopCutOffTime)
                        complete(Created, StartOrderRep(fromOrderToOrderRep(order, basketResponse.hints), blockDate))
                      }
                    }
                  }
                }
              }
            }
          }
      } ~
        pathPrefix(Segment) { orderId =>
          pathEnd {
            patch {
              entity(as[UpdateOrder]) { updateRequest =>
                onSuccess(orderService.updateOrder(orderId, updateRequest)) { response =>
                  complete(OK, JsObject.empty)
                }
              }
            }
          } ~
            path("cancel") {
              put {
                entity(as[UserRep]) { userRep =>
                  onSuccess(orderService.cancelOrder(userId, orderId, userRep)) { response =>
                    complete(fromOrderToOrderRep(response))
                  }
                }
              }
            }
        }
    }
  }

  private val payment = pathPrefix("payment") {
    auth { userId =>
      pathPrefix("order" / Segment) { orderId =>
        post {
          entity(as[PaymentRequest]) { paymentRequest =>
            onSuccess(paymentService.processPayment(orderId, userId, paymentRequest, paymentMethodService)) { response =>
              if (response.order.paymentFailed) {
                complete(UnprocessableEntity, response.order.payment.map(p => Error(UnprocessableEntity.intValue, Some(p.paymentStatus.toString), None)))
              } else {
                complete(OK, JsObject.empty)
              }
            }
          }
        }
      }
    }
  }

  private val delivery = pathPrefix("delivery") {
    auth { userId =>
      pathPrefix("postcodeCoverage" / Segment) { postcode =>
        get {
          onSuccess(deliveryService.getPostcodeArea(postcode)) {
            case Right(p) => complete(DeliveryResponseRep(Some(List(p)), None))
            case Left(e) => complete(e.code, DeliveryResponseRep(None, Some(List(e))))
          }
        }
      }
    }
  }

  private val paymentMethod =
    auth { userId =>
      pathPrefix("paymentMethod") {
        pathEnd {
          post {
            entity(as[CreateOrUpdatePaymentMethod]) { createPaymentMethod =>
              onComplete(paymentMethodService.createPaymentMethod(userId, createPaymentMethod)) {
                case Success(resp) => resp match {
                  case Right(pm) => complete(Created, PaymentMethodResponseRep(Some(Set(fromPayMethodToRepresentation(pm))), None))
                  case Left(e) => complete(e.code, PaymentMethodResponseRep(None, Some(List(e))))
                }
                case Failure(e) => complete(InternalServerError, PaymentMethodResponseRep(None, Some(List(Error.withLogger(500, Some(e.getMessage), None, e)))))
              }
            }
          }
        } ~
          path(Segment / "default") { id =>
            pathEnd {
              put {
                onComplete(paymentMethodService.makeDefaultPaymentMethod(id)) {
                  case Success(resp) => resp match {
                    case Right(pm) => complete(OK, PaymentMethodResponseRep.emptyResponse)
                    case Left(e) => complete(e.code, PaymentMethodResponseRep(None, Some(List(e))))
                  }
                  case Failure(e) => complete(InternalServerError, PaymentMethodResponseRep(None, Some(List(Error.withLogger(500, Some(e.getMessage), None, e)))))
                }
              }
            }
          } ~
          path(Segment) { id =>
            pathEnd {
              put {
                entity(as[CreateOrUpdatePaymentMethod]) { createPaymentMethod =>
                  onComplete(paymentMethodService.update(userId, id, createPaymentMethod)) {
                    case Success(resp) => resp match {
                      case Right(pm) => complete(OK, PaymentMethodResponseRep.emptyResponse)
                      case Left(e) => complete(e.code, PaymentMethodResponseRep(None, Some(List(e))))
                    }
                    case Failure(e) => complete(InternalServerError, PaymentMethodResponseRep(None, Some(List(Error.withLogger(500, Some(e.getMessage), None, e)))))
                  }
                }
              }
            }
          }
      } ~
        pathPrefix("paymentMethods") {
          pathPrefix("byUsername" / Segment) { username =>
            get {
              onComplete(paymentMethodService.getPaymentMethods(userId, username)) {
                case Success(resp) => resp match {
                  case Right(pms) => complete(OK, PaymentMethodResponseRep(Some(fromPayMethodSeqToRepresentationSet(pms)), None))
                  case Left(e) => complete(e.code, PaymentMethodResponseRep(None, Some(List(e))))
                }
                case Failure(e) => complete(InternalServerError, PaymentMethodResponseRep(None, Some(List(Error.withLogger(500, Some(e.getMessage), None, e)))))
              }
            }
          }
        }
    }

  val exceptionHandler = ExceptionHandler {
    case e: InvalidRequestException => complete(BadRequest, Error.withLogger(BadRequest.intValue, Some(e.getMessage), None, e))
    case e: ServiceGenericException => complete(e.error.code, Error.withLogger(e.error.code, e.error.message, None, e))
    case e@PaymentFailedException(message) => complete(UnprocessableEntity, Error.withLogger(UnprocessableEntity.intValue, Some(message), None, e))
    case e@DynamoDBErrorException(_) => complete(InternalServerError, Error.withLogger(InternalServerError.intValue, Some("unexpected internal server error"), None, e))
    case e@OperationNotAllowedException(message) => complete(Forbidden, Error.withLogger(Forbidden.intValue, Some(message), None, e))
    case e@ProductNotFoundException(_) => complete(NotFound, Error.withLogger(NotFound.intValue, Some(e.getMessage), None, e))
    case e@NotFoundException(_) => complete(NotFound, Error.withLogger(NotFound.intValue, Some(e.getMessage), None, e))
    case e@BadGetProductsRequestException(_, _) => complete(InternalServerError.intValue, Error.withLogger(InternalServerError.intValue, Some(e.getMessage), None, e))
    case e => complete(InternalServerError, Error.withLogger(InternalServerError.intValue, Some(s"unexpected internal server error"), None, e))
  }

  val routes = handleExceptions(exceptionHandler) {
    healthCheck ~ basket ~ orders ~ payment ~ paymentMethod ~ delivery
  }

  val minimumOrderHandler = RejectionHandler {
    case list if list.collect(validationRejections).nonEmpty =>
      list.collect(validationRejections).head match {
        case ValidationRejection(message, cause) =>
          complete(BadRequest, Error.withLogger(1000, Some(message), Some("basket"), cause.getOrElse(new InvalidRequestException(message))))
      }
  }

  def validationRejections: PartialFunction[Rejection, ValidationRejection] = {
    case vr: ValidationRejection => vr
  }
}
