//package com.officeserve.basketservice.clients
//
//import akka.actor.ActorSystem
//import com.netaporter.precanned.dsl.basic._
//import com.officeserve.basketservice.service.InvalidRequestException
//import com.officeserve.basketservice.settings.CatalogueClientSettings
//import org.scalatest.mock.MockitoSugar
//import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}
//import spray.http.{ContentTypes, HttpMethod, HttpMethods}
//import spray.testkit.ScalatestRouteTest
//
//import scala.concurrent.Await
//import scala.concurrent.duration._
//import scala.language.postfixOps
//import scala.util.{Failure, Success, Try}
//
//class CatalogueClientTest
//  extends FeatureSpec
//    with ScalatestRouteTest
//    with CatalogueClientFixture
//    with GivenWhenThen
//    with MockitoSugar
//    with Matchers {
//
//  override lazy val port: Int = 8765
//
//  expectCall(endpointPath = s"/products/$invalidProductId")
//  expectCall(endpointPath = s"/products/${validProductIds.mkString(",")}", body = twoProductsResponse)
//  expectCall(endpointPath = s"/products/${List(validProductId,invalidProductId).mkString(",")}", body = oneProductResponse)
//
//  scenario("Invalid product ids") {
//    Given(s"An invalid product id: $invalidProductId")
//    When("I call getProducts")
//    val futureResponse = client.getAvailableProducts(Seq(invalidProductId))
//    Then(s"should throw a ProductNotFoundException containing the $invalidProductId")
//    Try(Await.result(futureResponse, 10 seconds)) match {
//      case Failure(e@ProductNotFoundException(_)) => assert(e.getMessage.contains(invalidProductId))
//      case _ => fail("Error in response")
//    }
//  }
//
//  scenario("Valid Product ids") {
//    Given(s"${validProductIds.size} valid product id: ${validProductIds.mkString(",")}")
//    When("I call getProducts")
//    val futureResponse = client.getAvailableProducts(validProductIds)
//    Then(s"should return ${validProductIds.size} valid products")
//    Try(Await.result(futureResponse, 10 seconds)) match {
//      case Success(ps) => assert(ps.size == validProductIds.size)
//      case _ => fail("Error in response")
//    }
//  }
//
//  scenario("Empty list of Product ids") {
//    val emptyList = List.empty
//    Given(s"${emptyList.size} valid product id: ${emptyList.mkString(",")}")
//    When("I call getProducts")
//    val futureResponse = client.getAvailableProducts(emptyList)
//    Then(s"should return ${emptyList.size} valid products")
//    Try(Await.result(futureResponse, 10 seconds)) match {
//      case Failure(e@InvalidRequestException(_)) => assert(true)
//      case _ => fail("Error in response")
//    }
//  }
//
//  scenario("Invalid and valid product ids") {
//    val productIds = List(validProductId,invalidProductId)
//    Given(s"${productIds.size} valid and invalid product id: ${productIds.mkString(",")}")
//    When("I call getProducts")
//    val futureResponse = client.getAvailableProducts(productIds)
//    Then(s"should throw a ProductNotFoundException containing $invalidProductId")
//    And(s"should not contain $validProductId")
//    Try(Await.result(futureResponse, 10 seconds)) match {
//      case Failure(e@ProductNotFoundException(_)) =>
//        assert(e.getMessage.contains(invalidProductId) &&
//        !e.getMessage.contains(validProductId))
//      case _ => fail("Error in response")
//    }
//  }
//}
//
//trait CatalogueClientFixture extends PreCannedResponses {
//
//  lazy val client = CatalogueClient(CatalogueClientSettings(s"http://localhost:$port"))
//
//  val invalidProductId = "invalidProductId"
//  val validProductId = "product1"
//  val validProductIds = List(validProductId,"product2")
//
//  lazy val oneProductResponse =
//    """
//      |[
//      |  {
//      |    "responseType": "FoodItem",
//      |    "id": "product1",
//      |    "name": "Signature Roasted Butternut Squash, Fresh Beetroot & Feta Salad",
//      |    "description": "A rich and colourful mix of fresh beetroot, butternut squash, pumpkin seeds & feta salad.\nIncludes: Beetroot, bulgar wheat, fetta cheese, butternut squash, carrot, pumpkin seeds (approx 500g) and a serving spoon",
//      |    "contents": "Includes: Beetroot, bulgar wheat, fetta cheese, butternut squash, carrot, pumpkin seeds (approx 500g) and a serving spoon",
//      |    "storageInstructions": "Keep Chilled +5C - Consume on use by date",
//      |    "price": {
//      |      "currency": "GBP",
//      |      "value": 9.5,
//      |      "valueIncludingVAT": 11.4,
//      |      "vatRate": 0.2
//      |    },
//      |    "addedDate": "2016-08-28T14:42:09+0100",
//      |    "updatedDate": "2016-08-28T14:42:09+0100",
//      |    "availability": true,
//      |    "leadTime": 1,
//      |    "servings": {
//      |      "unit": "people",
//      |      "value": 5
//      |    },
//      |    "categories": [
//      |      "41297794-5ad6-47d0-abeb-02b5a5778363",
//      |      "93C7B0BC-1387-11E6-AA95-D8FB6FA2A08B",
//      |      "a63c85ab-1bb5-4dc1-a953-e2aa2beb0cfc"
//      |    ],
//      |    "images": [
//      |      {
//      |        "rel": "image",
//      |        "href": "http://images.contentful.com/s5khr7w5elfa/1XaUL2Y5wQeKWQ4gKmwIU4/5bf12b2b9b2384a673ae6f9e04a81198/TT500107.jpg",
//      |        "type": "image/jpeg"
//      |      }
//      |    ],
//      |    "tags": [
//      |      "Signature",
//      |      "Vegetarian"
//      |    ],
//      |    "productCode": "TT500107",
//      |    "readyToEat": true,
//      |    "preparationInstructions": "None",
//      |    "isNew": true,
//      |    "items": [
//      |      {
//      |        "name": "Beetroot, Pumpkin Seed, Butternut Squash & Feta Salad",
//      |        "foodNutritions": {
//      |          "per": {
//      |            "unit": "g",
//      |            "value": 100
//      |          },
//      |          "energy": {
//      |            "calories": {
//      |              "unit": "kCal",
//      |              "value": 104618
//      |            },
//      |            "kiloJoules": {
//      |              "unit": "kJoules",
//      |              "value": 438124
//      |            }
//      |          },
//      |          "info": [
//      |            {
//      |              "title": "Salt",
//      |              "value": {
//      |                "unit": "g",
//      |                "value": 0.6
//      |              },
//      |              "items": []
//      |            },
//      |            {
//      |              "title": "Fibre",
//      |              "value": {
//      |                "unit": "g",
//      |                "value": 1.3
//      |              },
//      |              "items": []
//      |            },
//      |            {
//      |              "title": "Fat",
//      |              "value": {
//      |                "unit": "g",
//      |                "value": 4.4
//      |              },
//      |              "items": [
//      |                {
//      |                  "title": "Saturates",
//      |                  "value": {
//      |                    "unit": "g",
//      |                    "value": 2
//      |                  }
//      |                }
//      |              ]
//      |            },
//      |            {
//      |              "title": "Carbohydrates",
//      |              "value": {
//      |                "unit": "g",
//      |                "value": 29.7
//      |              },
//      |              "items": [
//      |                {
//      |                  "title": "Sugars",
//      |                  "value": {
//      |                    "unit": "g",
//      |                    "value": 8.9
//      |                  }
//      |                }
//      |              ]
//      |            },
//      |            {
//      |              "title": "Protein",
//      |              "value": {
//      |                "unit": "g",
//      |                "value": 6.9
//      |              },
//      |              "items": []
//      |            }
//      |          ]
//      |        },
//      |        "ingredients": "BEETROOT (34%), BULGAR WHEAT (26%) [ Tritium Durum Whea], FETA CHEESE (13%) [Sheeps Milk, Goats Milk, Sea Salt, Starter Culture, Microbial Rennet], CARROT, SWEET & SOUR DRESSING [ Sugar, Water, White Wine Vinegar, Rapeseed Oil, Stabilisers: Xanthan Gum, Guar Gum, Paprika, Preservative: Potassium Sorbate, Acidity Regulator: Citric Acid], BUTTERNUT SQUASH, PUMPKIN SEED, FRUIT PEEL, ONION, PARSLEY, SALT, CRACKED BLACK PEPPER.",
//      |        "allergens": [
//      |          "Milk",
//      |          "Wheat"
//      |        ]
//      |      }
//      |    ]
//      |  }
//      | ]
//    """.stripMargin
//
//  lazy val twoProductsResponse: String =
//    """
//      |[
//      |  {
//      |    "responseType": "FoodItem",
//      |    "id": "product1",
//      |    "name": "Signature Roasted Butternut Squash, Fresh Beetroot & Feta Salad",
//      |    "description": "A rich and colourful mix of fresh beetroot, butternut squash, pumpkin seeds & feta salad.\nIncludes: Beetroot, bulgar wheat, fetta cheese, butternut squash, carrot, pumpkin seeds (approx 500g) and a serving spoon",
//      |    "contents": "Includes: Beetroot, bulgar wheat, fetta cheese, butternut squash, carrot, pumpkin seeds (approx 500g) and a serving spoon",
//      |    "storageInstructions": "Keep Chilled +5C - Consume on use by date",
//      |    "price": {
//      |      "currency": "GBP",
//      |      "value": 9.5,
//      |      "valueIncludingVAT": 11.4,
//      |      "vatRate": 0.2
//      |    },
//      |    "addedDate": "2016-08-28T14:42:09+0100",
//      |    "updatedDate": "2016-08-28T14:42:09+0100",
//      |    "availability": true,
//      |    "leadTime": 1,
//      |    "servings": {
//      |      "unit": "people",
//      |      "value": 5
//      |    },
//      |    "categories": [
//      |      "41297794-5ad6-47d0-abeb-02b5a5778363",
//      |      "93C7B0BC-1387-11E6-AA95-D8FB6FA2A08B",
//      |      "a63c85ab-1bb5-4dc1-a953-e2aa2beb0cfc"
//      |    ],
//      |    "images": [
//      |      {
//      |        "rel": "image",
//      |        "href": "http://images.contentful.com/s5khr7w5elfa/1XaUL2Y5wQeKWQ4gKmwIU4/5bf12b2b9b2384a673ae6f9e04a81198/TT500107.jpg",
//      |        "type": "image/jpeg"
//      |      }
//      |    ],
//      |    "tags": [
//      |      "Signature",
//      |      "Vegetarian"
//      |    ],
//      |    "productCode": "TT500107",
//      |    "readyToEat": true,
//      |    "preparationInstructions": "None",
//      |    "isNew": true,
//      |    "items": [
//      |      {
//      |        "name": "Beetroot, Pumpkin Seed, Butternut Squash & Feta Salad",
//      |        "foodNutritions": {
//      |          "per": {
//      |            "unit": "g",
//      |            "value": 100
//      |          },
//      |          "energy": {
//      |            "calories": {
//      |              "unit": "kCal",
//      |              "value": 104618
//      |            },
//      |            "kiloJoules": {
//      |              "unit": "kJoules",
//      |              "value": 438124
//      |            }
//      |          },
//      |          "info": [
//      |            {
//      |              "title": "Salt",
//      |              "value": {
//      |                "unit": "g",
//      |                "value": 0.6
//      |              },
//      |              "items": []
//      |            },
//      |            {
//      |              "title": "Fibre",
//      |              "value": {
//      |                "unit": "g",
//      |                "value": 1.3
//      |              },
//      |              "items": []
//      |            },
//      |            {
//      |              "title": "Fat",
//      |              "value": {
//      |                "unit": "g",
//      |                "value": 4.4
//      |              },
//      |              "items": [
//      |                {
//      |                  "title": "Saturates",
//      |                  "value": {
//      |                    "unit": "g",
//      |                    "value": 2
//      |                  }
//      |                }
//      |              ]
//      |            },
//      |            {
//      |              "title": "Carbohydrates",
//      |              "value": {
//      |                "unit": "g",
//      |                "value": 29.7
//      |              },
//      |              "items": [
//      |                {
//      |                  "title": "Sugars",
//      |                  "value": {
//      |                    "unit": "g",
//      |                    "value": 8.9
//      |                  }
//      |                }
//      |              ]
//      |            },
//      |            {
//      |              "title": "Protein",
//      |              "value": {
//      |                "unit": "g",
//      |                "value": 6.9
//      |              },
//      |              "items": []
//      |            }
//      |          ]
//      |        },
//      |        "ingredients": "BEETROOT (34%), BULGAR WHEAT (26%) [ Tritium Durum Whea], FETA CHEESE (13%) [Sheeps Milk, Goats Milk, Sea Salt, Starter Culture, Microbial Rennet], CARROT, SWEET & SOUR DRESSING [ Sugar, Water, White Wine Vinegar, Rapeseed Oil, Stabilisers: Xanthan Gum, Guar Gum, Paprika, Preservative: Potassium Sorbate, Acidity Regulator: Citric Acid], BUTTERNUT SQUASH, PUMPKIN SEED, FRUIT PEEL, ONION, PARSLEY, SALT, CRACKED BLACK PEPPER.",
//      |        "allergens": [
//      |          "Milk",
//      |          "Wheat"
//      |        ]
//      |      }
//      |    ]
//      |  },
//        {
//      |    "responseType": "FoodItem",
//      |    "id": "product2",
//      |    "name": "Signature Roasted Butternut Squash, Fresh Beetroot & Feta Salad",
//      |    "description": "A rich and colourful mix of fresh beetroot, butternut squash, pumpkin seeds & feta salad.\nIncludes: Beetroot, bulgar wheat, fetta cheese, butternut squash, carrot, pumpkin seeds (approx 500g) and a serving spoon",
//      |    "contents": "Includes: Beetroot, bulgar wheat, fetta cheese, butternut squash, carrot, pumpkin seeds (approx 500g) and a serving spoon",
//      |    "storageInstructions": "Keep Chilled +5C - Consume on use by date",
//      |    "price": {
//      |      "currency": "GBP",
//      |      "value": 9.5,
//      |      "valueIncludingVAT": 11.4,
//      |      "vatRate": 0.2
//      |    },
//      |    "addedDate": "2016-08-28T14:42:09+0100",
//      |    "updatedDate": "2016-08-28T14:42:09+0100",
//      |    "availability": true,
//      |    "leadTime": 1,
//      |    "servings": {
//      |      "unit": "people",
//      |      "value": 5
//      |    },
//      |    "categories": [
//      |      "41297794-5ad6-47d0-abeb-02b5a5778363",
//      |      "93C7B0BC-1387-11E6-AA95-D8FB6FA2A08B",
//      |      "a63c85ab-1bb5-4dc1-a953-e2aa2beb0cfc"
//      |    ],
//      |    "images": [
//      |      {
//      |        "rel": "image",
//      |        "href": "http://images.contentful.com/s5khr7w5elfa/1XaUL2Y5wQeKWQ4gKmwIU4/5bf12b2b9b2384a673ae6f9e04a81198/TT500107.jpg",
//      |        "type": "image/jpeg"
//      |      }
//      |    ],
//      |    "tags": [
//      |      "Signature",
//      |      "Vegetarian"
//      |    ],
//      |    "productCode": "TT500107",
//      |    "readyToEat": true,
//      |    "preparationInstructions": "None",
//      |    "isNew": true,
//      |    "items": [
//      |      {
//      |        "name": "Beetroot, Pumpkin Seed, Butternut Squash & Feta Salad",
//      |        "foodNutritions": {
//      |          "per": {
//      |            "unit": "g",
//      |            "value": 100
//      |          },
//      |          "energy": {
//      |            "calories": {
//      |              "unit": "kCal",
//      |              "value": 104618
//      |            },
//      |            "kiloJoules": {
//      |              "unit": "kJoules",
//      |              "value": 438124
//      |            }
//      |          },
//      |          "info": [
//      |            {
//      |              "title": "Salt",
//      |              "value": {
//      |                "unit": "g",
//      |                "value": 0.6
//      |              },
//      |              "items": []
//      |            },
//      |            {
//      |              "title": "Fibre",
//      |              "value": {
//      |                "unit": "g",
//      |                "value": 1.3
//      |              },
//      |              "items": []
//      |            },
//      |            {
//      |              "title": "Fat",
//      |              "value": {
//      |                "unit": "g",
//      |                "value": 4.4
//      |              },
//      |              "items": [
//      |                {
//      |                  "title": "Saturates",
//      |                  "value": {
//      |                    "unit": "g",
//      |                    "value": 2
//      |                  }
//      |                }
//      |              ]
//      |            },
//      |            {
//      |              "title": "Carbohydrates",
//      |              "value": {
//      |                "unit": "g",
//      |                "value": 29.7
//      |              },
//      |              "items": [
//      |                {
//      |                  "title": "Sugars",
//      |                  "value": {
//      |                    "unit": "g",
//      |                    "value": 8.9
//      |                  }
//      |                }
//      |              ]
//      |            },
//      |            {
//      |              "title": "Protein",
//      |              "value": {
//      |                "unit": "g",
//      |                "value": 6.9
//      |              },
//      |              "items": []
//      |            }
//      |          ]
//      |        },
//      |        "ingredients": "BEETROOT (34%), BULGAR WHEAT (26%) [ Tritium Durum Whea], FETA CHEESE (13%) [Sheeps Milk, Goats Milk, Sea Salt, Starter Culture, Microbial Rennet], CARROT, SWEET & SOUR DRESSING [ Sugar, Water, White Wine Vinegar, Rapeseed Oil, Stabilisers: Xanthan Gum, Guar Gum, Paprika, Preservative: Potassium Sorbate, Acidity Regulator: Citric Acid], BUTTERNUT SQUASH, PUMPKIN SEED, FRUIT PEEL, ONION, PARSLEY, SALT, CRACKED BLACK PEPPER.",
//      |        "allergens": [
//      |          "Milk",
//      |          "Wheat"
//      |        ]
//      |      }
//      |    ]
//      |  }
//      |]
//    """.stripMargin
//
//}
//
//trait PreCannedResponses {
//
//  implicit val system: ActorSystem
//
//  val port:Int
//
//  val catalogueApi = httpServerMock(system).bind(port).block
//
//  def expectCall(method1:HttpMethod = HttpMethods.GET, endpointPath: String, body: String = "[]"): Unit = {
//    catalogueApi.expect(method(method1),
//        path(endpointPath))
//      .andRespondWith(entity(body),contentType(ContentTypes.`application/json`))
//  }
//
//
//
//}
