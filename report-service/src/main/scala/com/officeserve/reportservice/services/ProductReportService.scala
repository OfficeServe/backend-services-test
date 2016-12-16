package com.officeserve.reportservice.services

import java.time.{Clock, LocalDate, ZonedDateTime}

import com.officeserve.reportservice.models.orderlog.OrderLog
import com.officeserve.reportservice.models.productreport._
import com.officeserve.reportservice.repositories.{OrderLogRepository, ProductReportLogRepository}

import scala.concurrent.{ExecutionContext, Future}

trait ProductReportService {
  def generate: Future[ProductReport]

  def storeOrderLog(orderLog: OrderLog): Future[Unit]
}


class ProductReportServiceImpl(clock: Clock, orderLogRepository: OrderLogRepository, productReportLogRepository: ProductReportLogRepository)(implicit ec: ExecutionContext) extends ProductReportService {


  override def generate: Future[ProductReport] = {
    val generatedOn = ZonedDateTime.now(clock)
    val cutOffDate = generatedOn.toLocalDate
    val previousReportFuture = retrieveLatest(cutOffDate)
    for {
      orderLogs <- orderLogRepository.query(cutOffDate)
      currentReport <- squish(orderLogs)
      previousReport <- previousReportFuture
      mergedReport <- mergeResults(currentReport, previousReport)
      reportLog = ProductReport(cutOffDate, generatedOn, mergedReport)
      _ <- logReport(reportLog)
    } yield reportLog
  }

  override def storeOrderLog(orderLog: OrderLog): Future[Unit] =
    orderLogRepository.put(orderLog)


  private def squish(orders: Seq[OrderLog]): Future[ProductReportData] =
    Future {
      orders.foldLeft(Map[ProductId, ProductReportEntry]()) { (acc, curr) =>

        val multiplier: Int = curr.orderMessageRep.order.orderStatus.status match {
          case "Pending" => 1
          case "Cancelled" => -1
        }

        val basketItems = curr.orderMessageRep.order.basket.items.foldLeft(Map[ProductId, ProductReportEntry]()) { (basketAcc, basketCurr) =>
          val newProduct = basketAcc.get(basketCurr.productId).fold {
            ProductReportEntry(
              productId = basketCurr.productId,
              productCode = basketCurr.productCode,
              productName = basketCurr.name,
              previousQuantity = 0,
              currentQuantity = basketCurr.quantity * multiplier,
              deliveryDate = curr.orderMessageRep.order.deliveryDate.get // I want this to fail in case it's absent
            )
          } {
            oldValue => oldValue.copy(currentQuantity = oldValue.currentQuantity + (basketCurr.quantity * multiplier))
          }
          basketAcc + (basketCurr.productId -> newProduct)
        }

        acc ++ basketItems.map {
          case (k, v) =>
            k -> acc.get(k).fold {
              v
            } { old =>
              old.copy(currentQuantity = old.currentQuantity + v.currentQuantity)
            }
        }
      }
    }

  private def retrieveLatest(cutOffDate: LocalDate): Future[ProductReportData] =
    productReportLogRepository.retrieveLatest(cutOffDate)
      .map {
        case Some(productReportLog) => productReportLog.report
        case _ => Map[ProductId, ProductReportEntry]()
      }

  private def mergeResults(current: ProductReportData, previous: ProductReportData): Future[ProductReportData] =
    Future {
      current ++ previous.map {
        case (k, v) => k -> current.get(k).fold {
          // the item is not on the list anymore (e.g. a purchase has been cancelled)
          v.copy(previousQuantity = v.currentQuantity, currentQuantity = 0)
        } { currentProductReport =>
          currentProductReport.copy(previousQuantity = v.currentQuantity)
        }
      }
    }

  private def logReport(report: ProductReport): Future[Unit] =
    productReportLogRepository.put(report)

}
