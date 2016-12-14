package com.officeserve.basketservice.migration

import java.io.File

import com.amazonaws.auth.BasicAWSCredentials
import com.officeserve.basketservice.common.DynamoDBSettings
import com.officeserve.basketservice.persistence.{PaymentMethodRepositoryImpl, PostCodeRepositoryImpl}
import com.officeserve.basketservice.service.PaymentMethodServiceAdminImpl
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

/**
  * Created by mo on 16/11/2016.
  */
object MigrationApp extends App
  with StrictLogging {

  // params
  if (args.isEmpty) {
    Console.err.println("Please pass the arguments: AWS_ACCESS_KEY , AWS_SECRET_KEY, MIGRATION_FILE_PATH in this respective order")
    System.exit(1)
  }

  Console.out.println("Please choose which migration you want to run:" + "\n" +
    "[1] OnAccount user Migration:" + "\n" +
    "[2] Postcodes Migration:")
  val userResp = Console.in.readLine()
  val migrationType = userResp match {
    case "1" => MigrationType.ON_ACCOUNT
    case "2" => MigrationType.POSTCODE
    case e: String => Console.err.println(s"Option ${e} Not supported!")
      System.exit(1)
  }

  val accessKey = args(0)
  val secret = args(1)
  val filePath = args(2)


  logger.info(s"Running migration of $migrationType")

  val config = ConfigFactory.parseFile(new File("app/src/test/scala/com/officeserve/basketservice/migration/config/migration.conf"))

  implicit val dbSetting = new DynamoDBSettings(config, Some(new BasicAWSCredentials(accessKey, secret)))
  implicit val ec = ExecutionContext.Implicits.global

  val spreadsheetReader = new SpreadsheetReaderImpl(filePath)

  val paymentMethodRepository = new PaymentMethodRepositoryImpl
  val paymentMethodServiceAdmin = new PaymentMethodServiceAdminImpl(paymentMethodRepository)
  val onAccountPaymentMethodMigration = new OnAccountPaymentMethodMigration(paymentMethodServiceAdmin, spreadsheetReader)

  val postCodeRepository = new PostCodeRepositoryImpl
  val postCodeMigration = new PostCodeMigration(postCodeRepository, spreadsheetReader)

  logger.info(s"connecting to dynamodb endpoint: ${dbSetting.endpoint}")
  logger.info(s"pointing database : ${postCodeRepository.table.fullName}")

  migrationType match {
    case MigrationType.ON_ACCOUNT => Await.result(onAccountPaymentMethodMigration.migrateOnAccountUserFromSpreadSheet, 10 minutes)
    case MigrationType.POSTCODE => Await.result(postCodeMigration.migratePostCodeFromSpreadsheet, 10 minutes)
  }


  System.exit(0)


}

object MigrationType {
  val POSTCODE = "postcode"
  val ON_ACCOUNT = "onAccount"
}