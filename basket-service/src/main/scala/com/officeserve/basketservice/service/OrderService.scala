package com.officeserve.basketservice.service

import java.time.ZonedDateTime
import javax.inject.{Inject, Singleton}

import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout
import cats.data.{Xor, XorT}
import cats.implicits._
import com.google.inject.ImplementedBy
import com.officeserve.basketservice.clients.SNSPublisherActor.{CutoffTimeNotification, PartialProductReportTimeNotification, SendPurchaseCancelledNotification}
import com.officeserve.basketservice.clients.{CatalogueClient, ProductRep}
import com.officeserve.basketservice.persistence.{Basket, BasketItem, Cancelled, NoTableware, Order, OrderMessage, OrderRepository, OrderStatus, Pending, Processed, Started}
import com.officeserve.basketservice.settings.BasketConfig
import com.officeserve.basketservice.web._
import com.typesafe.scalalogging.StrictLogging
import officeserve.commons.domain.UserDom
import officeserve.commons.spray.auth.TrustedAuth

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

@ImplementedBy(classOf[OrderServiceImpl])
trait OrderService {

  def notifyPendingOrders(cutoffTime: ZonedDateTime)(implicit ec: ExecutionContext): Future[Unit]

  def processOrdersAtCutoffTime(cutOffTime: ZonedDateTime)(implicit ec: ExecutionContext = ExecutionContext.global): Future[Unit]

  def getUserOrders(userId: TrustedAuth.CognitoIdentityId, status: Option[Seq[OrderStatus]] = None)(implicit ec: ExecutionContext = ExecutionContext.global): Future[Seq[Order]]

  /** Creates and persists a new order and returns its ID. */
  def createOrder(basket: Basket, products: Seq[ProductRep] = Seq.empty, userId: TrustedAuth.CognitoIdentityId)(implicit ec: ExecutionContext): Future[Order]

  def cancelOrder(userId: TrustedAuth.CognitoIdentityId, orderId: String, user: UserRep)(implicit ec: ExecutionContext): Future[Order]

  def updateOrder(orderId: String, updateReq: UpdateOrder)(implicit ec: ExecutionContext = ExecutionContext.global): Future[Unit]

}

@Singleton
class OrderServiceImpl @Inject()(orderRepository: OrderRepository,
                                 basketConfig: BasketConfig,
                                 publisherActor: ActorRef,
                                 catalogueClient: CatalogueClient,
                                 payments: PaymentService,
                                 deliveryService: DeliveryService
                                )(implicit val tradingDays: TradingDays) extends OrderService with StrictLogging with Adapter {

  import BusinessValidator._

  override def createOrder(basket: Basket, products: Seq[ProductRep] = Seq.empty, userId: TrustedAuth.CognitoIdentityId)
                          (implicit ec: ExecutionContext = ExecutionContext.global): Future[Order] = {
    val order = Order(userId = userId,
      basket = basket,
      orderStatus = Started,
      tableware = NoTableware(calculateCupsPlatesNapkins(products, basketConfig.extraTablewarePercent, basket.items)))
    orderRepository.createOrder(order) map (_ => order)
  }


  private def calculateCupsPlatesNapkins(products: Seq[ProductRep], extraTablewarePercent: Double, basketItems: List[BasketItem]): Int = {
    val servingsSum = products.foldLeft(0.0)((a, i) => a + (i.servings.value * basketItems.filter(item => item.id == i.id).head.quantity))
    math.ceil(servingsSum * (1 + extraTablewarePercent)).toInt
  }

  override def getUserOrders(userId: TrustedAuth.CognitoIdentityId,
                             status: Option[Seq[OrderStatus]] = None)(implicit ec: ExecutionContext = ExecutionContext.global): Future[Seq[Order]] = {

    orderRepository.getUserOrders(userId) map (_.filterNot(_.isInProgress).sortBy(-_.createdDate.toInstant.getEpochSecond))

  }

  object Result {

    type Result[R] = XorT[Future, ServiceException, R]

    def fromFutureOption[T](func: => Future[Option[T]])(onError: => ServiceException)
                           (implicit ec: ExecutionContext = ExecutionContext.global): Result[T] =
      XorT(func.map(_.toRight(onError)).map(_.toXor))

    def fromOption[T](opt: => Option[T])(onError: => ServiceException)
                     (implicit ec: ExecutionContext = ExecutionContext.global): Result[T] =

      fromFutureOption(Future.successful(opt))(onError)

    def fromFuture[T](func: => Future[T])
                     (implicit ec: ExecutionContext = ExecutionContext.global): Result[T] =

      XorT(func.map[Xor[ServiceException, T]](Xor.right))

  }

  override def cancelOrder(userId: TrustedAuth.CognitoIdentityId, orderId: String, user: UserRep)
                          (implicit ec: ExecutionContext = ExecutionContext.global): Future[Order] = {

    import Result._

    (for {

      order         <- fromFutureOption(orderRepository.getOrder(orderId))(NotFoundException(s"Order '$orderId' not found."))
      userOrder     <- fromOption(Some(order).filter(_.userId == userId))(OperationNotAllowedException("user is not allowed to perform this operation"))
      _             <- fromOption(Some(userOrder).filterNot(_.orderStatus == Cancelled))(OrderAlreadyCancelledException(userOrder))
      updatedOrder  <- fromOption(Some(userOrder).filter(_.canCancel).map(o => o.copy(orderStatus = Cancelled)))(OperationNotAllowedException("order cannot be cancelled"))
      _             <- fromFuture(orderRepository.updateOrders(updatedOrder))
      //TODO: no refund at this stage.
      //transactionId = userOrder.payment.fold("")(_.transactionId.getOrElse(""))
      //cancellation <- fromFuture(payments.refund(transactionId, userOrder.basket.grandTotal))
      //_ <- fromFuture(orderRepository.updateOrders(updatedOrder.copy(cancelTransaction = Some(cancellation.toCancelTransaction))))

    } yield (userOrder, updatedOrder)).value map {

      case Xor.Right((originalOrder, updatedOrder)) => (originalOrder, updatedOrder)
      case Xor.Left(OrderAlreadyCancelledException(order)) => (order, order)
      case Xor.Left(e) => throw e.toException

    } andThen {

      case Success((previousState, updatedOrder)) if !previousState.isCancelled =>
        publisherActor ! SendPurchaseCancelledNotification(OrderMessageRep(fromOrderToOrderRep(updatedOrder), user))

    } map (_._2)

  }

  private def calculateCutOffTime(order: Order)(dd: ZonedDateTime): ZonedDateTime = {
    val leadTime = order.basket.items.map(_.leadTime).max
    tradingDays.getLastWorkingDay(tradingDays.getTableTopCutOffTimeFor(dd.minusDays(leadTime)))
  }

  override def updateOrder(orderId: String, updateReq: UpdateOrder)
                          (implicit ec: ExecutionContext = ExecutionContext.global): Future[Unit] = {
    // retrieve the order from database
    for {
    // retrieving the order
      order <- orderRepository.getOrder(orderId)
      _ = if (order.isEmpty) throw new NotFoundException(s"order $orderId is not found")

      //TODO do validation based on the update Operation

      deliveryDateToUpdate = updateReq.fields.deliveryDate
      // validating fields
      _ <- deliveryDateToUpdate.fold[Future[Unit]](Future.successful(Unit)) { d =>
        val productsFuture = catalogueClient.getAvailableProducts(order.get.basket.items.map(_.id))
        productsFuture.map { products =>
          validateDeliveryDate(d, products.map(_.leadTime).foldLeft(1)(_ max _), tradingDays)
        }
      }

      existing = order.get
      _ = updateReq.fields.telephoneNum.foreach(t => validateTelephoneNumFormat(t))
      psCValidation <- validateDeliveryPostcode(updateReq.fields.deliveryAddress, deliveryService)
      _= psCValidation match {
        case Right(_) =>
        case Left(e) => throw ServiceGenericException(e)
      }

      // updating the order
      updatedOrder = existing.copy(
        deliveryAddress =  updateReq.fields.deliveryAddress.map(fromDeliveryAddressRepToDeliveryAddress).orElse(existing.deliveryAddress),
        telephoneNum    =  updateReq.fields.telephoneNum.orElse(existing.telephoneNum),
        paymentReference = updateReq.fields.paymentReference.orElse(existing.paymentReference),
        deliveryDate    =  deliveryDateToUpdate.orElse(existing.deliveryDate),
        deliverySlot    =  deliveryDateToUpdate.map(d => tradingDays.getDeliverySlot(d)).orElse(existing.deliverySlot),
        cutOffTime      =  deliveryDateToUpdate.map(calculateCutOffTime(existing)).orElse(existing.cutOffTime)
      )

      //persisting the order
      updateResp <- orderRepository.updateOrders(updatedOrder)

    } yield updateResp
  }

  private def getOrdersFor(status: OrderStatus, cutOffTime: ZonedDateTime)
                          (implicit ec: ExecutionContext): Future[Seq[Order]] = {

    orderRepository.getOrdersFor(status, cutOffTime)

  }


  private def notifyCutoffTime(orders: Seq[Order]) = {

    implicit val timeout = Timeout(1 second)

    publisherActor ? CutoffTimeNotification(
      orders.map(order =>
        OrderMessage(order,
          order.userData.getOrElse(
            UserDom(order.userId, "Unknown User",
              "Please, contact the user for information",
              "support@officeserve.com"
            )
          )
        )
      )
    )
  }

  override def processOrdersAtCutoffTime(cutoffTime: ZonedDateTime)(implicit ec: ExecutionContext): Future[Unit] = {

    val status = Pending

    logger.info(s"Status: $status and date ${cutoffTime.toString}")
    for {
      pendingOrders <- getOrdersFor(status, cutoffTime)
      _             = logger.debug(s"Sending cut off message with '${pendingOrders.size}' orders.")
      _             <- notifyCutoffTime(pendingOrders)
      _             = logger.debug(s"Moving orders to '$Processed' status.")
      _             <- updateOrders(pendingOrders.map(_.copy(orderStatus = Processed)))
    } yield ()
  }

  def updateOrders(orders: Seq[Order])(implicit ec: ExecutionContext): Future[Unit] = {
    if (orders.nonEmpty) {

      orderRepository.updateOrders(orders:_*).map(_ => ())

    } else {

      logger.info(s"No orders to process at ${ZonedDateTime.now}")
      Future.successful(Unit)

    }
  }

  override def notifyPendingOrders(cutoffTime: ZonedDateTime)(implicit ec: ExecutionContext): Future[Unit] = {

    implicit val timeout = Timeout(1 second)

    for {
      orders <- this.getOrdersFor(Pending, cutoffTime)
      _ <- publisherActor ? PartialProductReportTimeNotification(
        orders.map(order =>
          OrderMessage(order,
            order.userData.getOrElse(
              UserDom(order.userId, "Unknown User",
                "Please, contact the user for information",
                "support@officeserve.com"
              )
            )
          )
        )
      )
    } yield ()

  }
}
