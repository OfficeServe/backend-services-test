package com.officeserve.basketservice


import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.github.dwhjames.awswrap.sns.AmazonSNSScalaClient
import com.google.inject.{AbstractModule, Guice}
import com.officeserve.basketservice.clients._
import com.officeserve.basketservice.common.DynamoDBSettings
import com.officeserve.basketservice.persistence.{OrderRepository, OrderRepositoryImpl, PaymentMethodRepository, PaymentMethodRepositoryImpl, PostCodeRepository, PostCodeRepositoryImpl, SequenceRepository, SequenceRepositoryImpl}
import com.officeserve.basketservice.service._
import com.officeserve.basketservice.settings._
import com.officeserve.basketservice.web.BasketServiceApi
import com.typesafe.scalalogging.Logger
import net.codingwell.scalaguice.InjectorExtensions._
import net.codingwell.scalaguice.ScalaModule
import org.slf4j.LoggerFactory
import spray.can.Http
import spray.routing.HttpServiceActor

import scala.concurrent.{ExecutionContext, Future}

object Boot extends App {

  //Creating Guice Injector
  val injector = Guice.createInjector(new AppModule)

  val serviceName = "basket-service"
  val logger = Logger(LoggerFactory.getLogger(Boot.getClass.getName))

  implicit val system = ActorSystem(serviceName)
  implicit val ec: ExecutionContext = ExecutionContext.global

  //Retrieving settings
  val serverSettings: ServerSettings = injector.instance[ServerSettings]
  val basketConfig = injector.instance[BasketConfig]

  //Notification Publisher
  val snsClient = injector.instance[AmazonSNSScalaClient]
  val notificationsSettings = injector.instance[NotificationsSettings]
  val sNSSender = new SNSPublisher(snsClient)
  val purchasePublisherActor = system.actorOf(SNSPublisherActor.props(sNSSender, notificationsSettings), "purchase-complete-publisher")

  //  // Spreedly payment gateway
  val spreedlySettings = injector.instance[SpreedlySettings]
  val paymentGateway = new PaymentGatewayImpl(spreedlySettings)

  //dependency injecting services
  val pricingService = injector.instance[MockPricingServiceImpl]
  val promoRepository = injector.instance[PromoRepositoryImpl]
  val orderRepository = injector.instance[OrderRepository]
  val postCodeRepository = injector.instance[PostCodeRepository]
  val promotionService = new PromotionServiceImpl(orderRepository, promoRepository)

  val catalogueClientSettings: CatalogueClientSettings = injector.instance[CatalogueClientSettings]
  val catalogueClient = CatalogueClient(catalogueClientSettings)
  implicit val tradingDays = new TradingDays(DateUtils(), basketConfig)

  val sequenceService = injector.instance[SequenceService]
  val deliveryService = new DeliveryServiceImpl(postCodeRepository)

  val basketService = new BasketServiceImpl(pricingService, promotionService, basketConfig)
  val paymentService = new PaymentServiceImpl(orderRepository, paymentGateway, purchasePublisherActor, basketConfig, sequenceService)
  val orderService = new OrderServiceImpl(orderRepository, basketConfig, purchasePublisherActor, catalogueClient, paymentService, deliveryService)
  val paymentMethodRepository = injector.instance[PaymentMethodRepository]
  val paymentMethodService = new PaymentMethodServiceImpl(paymentMethodRepository, paymentGateway)

  // setting up the actor
  val httpService = system.actorOf(BasketServiceActor.props(basketService, paymentService, orderService, paymentMethodService, catalogueClient, deliveryService, basketConfig, tradingDays), serviceName)

  // Bind HTTP to the specified service.
  implicit val timeout = Timeout(serverSettings.timeout)

  //starting the service
  logger.info("App started")
  IO(Http) ? Http.Bind(httpService, interface = serverSettings.host, port = serverSettings.port)

  val sqsSettings: SQSSettings = injector.instance[SQSSettings]
  val systemEventProcessor = new SystemEventProcessor(new SQSConsumer(sqsSettings), orderService)


}


class BasketServiceActor(basketService: BasketService, paymentService: PaymentService, orderService: OrderService, paymentMethodService: PaymentMethodService, catalogueClient: CatalogueClient, deliveryService: DeliveryService , basketConfig: BasketConfig, implicit val tradingDays: TradingDays) extends HttpServiceActor {
  val basketServiceApi = new BasketServiceApi(basketService, paymentService, orderService, paymentMethodService, catalogueClient,deliveryService, basketConfig)

  def receive = runRoute(basketServiceApi.routes)
}

object BasketServiceActor {
  def props(basketService: BasketService,
            paymentService: PaymentService,
            orderService: OrderService,
            paymentMethodService: PaymentMethodService,
            catalogueClient: CatalogueClient,
            deliveryService: DeliveryService,
            basketConfig: BasketConfig,
            tradingDays: TradingDays) = Props(new BasketServiceActor(basketService, paymentService, orderService, paymentMethodService, catalogueClient,deliveryService, basketConfig, tradingDays))
}

class AppModule extends AbstractModule with ScalaModule {

  override def configure(): Unit = {
    val settings = new BasketSettings()

    bind[DynamoDBSettings] toInstance settings.dynamoDBSettings
    bind[SQSSettings] toInstance settings.sqsSettings
    bind[ServerSettings] toInstance settings.serverSettings
    bind[BasketConfig] toInstance settings.basketConfig
    bind[SpreedlySettings] toInstance settings.spreedlySettings
    bind[OrderRepository] toInstance
      new OrderRepositoryImpl()(settings.dynamoDBSettings, ExecutionContext.global)

    bind[NotificationsSettings] toInstance settings.notificationsSettings
    bind[AmazonSNSScalaClient] toInstance settings.notificationsSettings.snsClient
    bind[CatalogueClientSettings] toInstance settings.catalogueClientSettings
    bind[PromoRepository] toInstance new PromoRepositoryImpl
    bind[PaymentMethodRepository] toInstance
      new PaymentMethodRepositoryImpl()(settings.dynamoDBSettings, ExecutionContext.global)
    bind[PostCodeRepository] toInstance
      new PostCodeRepositoryImpl()(settings.dynamoDBSettings, ExecutionContext.global)

    bind[SequenceService] toInstance
      new SequenceServiceImpl()(settings.dynamoDBSettings).withSequenceIfNotExists("invoiceNumber", 90200)
  }
}
